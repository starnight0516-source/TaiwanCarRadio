package tw.carradio.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.carradio.app.data.RadioRepository
import tw.carradio.app.data.WeatherRepository
import tw.carradio.app.model.RadioStation
import tw.carradio.app.model.StationCategory
import tw.carradio.app.model.WeatherInfo
import tw.carradio.app.service.RadioPlaybackService

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    RECONNECTING
}

data class RadioUiState(
    val allStations: List<RadioStation> = emptyList(),
    val filteredStations: List<RadioStation> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val selectedCategory: StationCategory = StationCategory.ALL,
    val searchQuery: String = "",
    val currentStation: RadioStation? = null,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null,
    val keepScreenOn: Boolean = true,
    val autoResumeOnBoot: Boolean = false,
    val weather: WeatherInfo? = null,
    val isWeatherLoading: Boolean = false,
    val weatherHint: String = "正在取得即時天氣…"
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RadioRepository(application)
    private val weatherRepository = WeatherRepository(application)
    private val locationManager =
        application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _uiState = MutableStateFlow(
        RadioUiState(
            favoriteIds = repository.getFavoriteIds(),
            keepScreenOn = repository.isKeepScreenOn(),
            autoResumeOnBoot = repository.isAutoResumeOnBoot()
        )
    )
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingAutoPlayLast = false
    private var isLocationListening = false
    private var lastGpsLocation: Location? = null
    private var lastWeatherFetchMs: Long = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            maybeFetchWeatherForGps(location, force = false)
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlayerState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId ?: return
            val matched = _uiState.value.allStations.firstOrNull { it.id == mediaId }
            if (matched != null) {
                repository.saveLastStationId(matched.id)
                _uiState.update { it.copy(currentStation = matched, statusMessage = null) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val detail = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "網路連線逾時，正在重連…"
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "該電台來源暫時無回應（HTTP 錯誤）"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FAILED -> "該電台串流格式不相容"
                else -> "串流訊號不穩（${error.errorCodeName}），正在重連…"
            }
            _uiState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.RECONNECTING,
                    statusMessage = detail
                )
            }
        }
    }

    init {
        connectMediaController()
        loadInitialStations()
        startGpsWeatherUpdates(forceRefresh = true)
    }

    fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startGpsWeatherUpdates(forceRefresh: Boolean = true) {
        var gotGpsNow = false

        if (hasLocationPermission()) {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null && (bestLocation == null || loc.time > bestLocation!!.time)) {
                            bestLocation = loc
                        }
                    }
                }
            }

            if (bestLocation != null) {
                gotGpsNow = true
                maybeFetchWeatherForGps(bestLocation!!, force = forceRefresh)
            }

            if (!isLocationListening) {
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    runCatching {
                        if (locationManager.isProviderEnabled(provider)) {
                            locationManager.requestLocationUpdates(
                                provider,
                                15 * 60 * 1000L,
                                3000f,
                                locationListener,
                                Looper.getMainLooper()
                            )
                            isLocationListening = true
                        }
                    }
                }
            }
        }

        // 若車機尚未鎖定 GPS（如在室內停車或 CarPlay 盒子未透傳 GPS），立刻用網路定位顯示所在縣市天氣
        if (!gotGpsNow) {
            fetchWeatherViaIpFallback()
        }
    }

    private fun fetchWeatherViaIpFallback() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWeatherLoading = true, weatherHint = "正在取得所在地即時天氣…") }
            val coordsResult = weatherRepository.fetchFallbackIpCoordinates()
            coordsResult.onSuccess { (lat, lon) ->
                val result = weatherRepository.fetchWeather(lat, lon, sourceLabel = "網路定位")
                result.onSuccess { info ->
                    // 僅在尚未收到更精準的 GPS 天氣前套用網路定位天氣
                    if (lastGpsLocation == null) {
                        _uiState.update { it.copy(weather = info, isWeatherLoading = false, weatherHint = "") }
                    } else {
                        _uiState.update { it.copy(isWeatherLoading = false) }
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            isWeatherLoading = false,
                            weatherHint = if (it.weather == null) "天氣載入失敗，點此重試" else ""
                        )
                    }
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isWeatherLoading = false,
                        weatherHint = if (it.weather == null) "等待 GPS 定位（點此重試）" else ""
                    )
                }
            }
        }
    }

    private fun maybeFetchWeatherForGps(location: Location, force: Boolean) {
        val now = System.currentTimeMillis()
        val prev = lastGpsLocation
        val movedMeters = prev?.distanceTo(location) ?: Float.MAX_VALUE
        val elapsedMs = now - lastWeatherFetchMs

        if (!force && prev != null && movedMeters < 2500f && elapsedMs < 10 * 60 * 1000L) {
            return
        }

        lastGpsLocation = location
        lastWeatherFetchMs = now

        viewModelScope.launch {
            _uiState.update { it.copy(isWeatherLoading = true) }
            val result = weatherRepository.fetchWeather(location.latitude, location.longitude, sourceLabel = "GPS")
            result.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        weather = info,
                        isWeatherLoading = false,
                        weatherHint = ""
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isWeatherLoading = false,
                        weatherHint = if (it.weather == null) "天氣更新失敗，點此重試" else ""
                    )
                }
            }
        }
    }

    fun requestAutoPlayLast() {
        pendingAutoPlayLast = true
        tryAutoPlayLastIfReady()
    }

    private fun connectMediaController() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    controller.addListener(playerListener)
                    syncPlayerState()
                    tryAutoPlayLastIfReady()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun loadInitialStations() {
        viewModelScope.launch {
            val cached = repository.loadCachedStations()
            if (cached.isNotEmpty()) {
                applyStations(cached)
            }
            refreshStationsFromNetwork()
        }
    }

    fun refreshStationsFromNetwork() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusMessage = "正在同步台灣電台清單…") }
            val result = repository.fetchTaiwanStationsFromNetwork()
            result.onSuccess { stations ->
                applyStations(stations)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        statusMessage = "已同步 ${stations.size} 個台灣網路廣播電台"
                    )
                }
            }.onFailure {
                val hasLocal = _uiState.value.allStations.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        statusMessage = if (hasLocal) {
                            "離線模式：使用本機快取清單"
                        } else {
                            "無法取得電台清單，請確認車機網路連線後重試"
                        }
                    )
                }
            }
        }
    }

    private fun applyStations(stations: List<RadioStation>) {
        val lastId = repository.getLastStationId()
        _uiState.update { state ->
            val current = state.currentStation
                ?: stations.firstOrNull { it.id == mediaController?.currentMediaItem?.mediaId }
                ?: stations.firstOrNull { it.id == lastId }
            val next = state.copy(
                allStations = stations,
                currentStation = current
            )
            next.copy(filteredStations = filterStations(next))
        }
        tryAutoPlayLastIfReady()
    }

    private fun tryAutoPlayLastIfReady() {
        if (!pendingAutoPlayLast) return
        val controller = mediaController ?: return
        val station = _uiState.value.currentStation ?: return
        pendingAutoPlayLast = false
        if (!controller.isPlaying) {
            playStation(station)
        }
    }

    fun selectCategory(category: StationCategory) {
        _uiState.update { state ->
            val next = state.copy(selectedCategory = category)
            next.copy(filteredStations = filterStations(next))
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            val next = state.copy(searchQuery = query)
            next.copy(filteredStations = filterStations(next))
        }
    }

    fun toggleFavorite(stationId: String) {
        val updated = repository.toggleFavorite(stationId)
        _uiState.update { state ->
            val next = state.copy(favoriteIds = updated)
            next.copy(filteredStations = filterStations(next))
        }
    }

    fun addCustomStation(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return
        val updatedCustom = repository.addCustomStation(name, url)
        val nonCustom = _uiState.value.allStations.filterNot { it.isCustom }
        applyStations(updatedCustom + nonCustom)
        _uiState.update { it.copy(statusMessage = "已新增自訂電台：$name") }
    }

    fun deleteCustomStation(stationId: String) {
        val updatedCustom = repository.removeCustomStation(stationId)
        val nonCustom = _uiState.value.allStations.filterNot { it.isCustom }
        applyStations(updatedCustom + nonCustom)
        _uiState.update { it.copy(statusMessage = "已移除自訂電台") }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        repository.setKeepScreenOn(enabled)
        _uiState.update { it.copy(keepScreenOn = enabled) }
    }

    fun setAutoResumeOnBoot(enabled: Boolean) {
        repository.setAutoResumeOnBoot(enabled)
        _uiState.update { it.copy(autoResumeOnBoot = enabled) }
    }

    private fun RadioStation.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("台灣車機廣播 · ${category.label}")
                    .build()
            )
            .build()

    fun playStation(station: RadioStation) {
        val controller = mediaController ?: return
        repository.saveLastStationId(station.id)

        val queue = _uiState.value.filteredStations.ifEmpty { _uiState.value.allStations }
        val idx = queue.indexOfFirst { it.id == station.id }

        val windowItems = mutableListOf<MediaItem>()
        val startIndex: Int
        if (queue.size >= 2 && idx >= 0) {
            val prev = queue[(idx - 1 + queue.size) % queue.size]
            val next = queue[(idx + 1) % queue.size]
            windowItems.add(prev.toMediaItem())
            windowItems.add(station.toMediaItem())
            if (next.id != prev.id) {
                windowItems.add(next.toMediaItem())
            }
            startIndex = 1
        } else {
            windowItems.add(station.toMediaItem())
            startIndex = 0
        }

        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItems(windowItems, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()

        _uiState.update {
            it.copy(
                currentStation = station,
                playbackStatus = PlaybackStatus.BUFFERING,
                statusMessage = null
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying || controller.playbackState == Player.STATE_BUFFERING) {
            controller.pause()
        } else {
            val target = _uiState.value.currentStation
                ?: _uiState.value.filteredStations.firstOrNull()
                ?: _uiState.value.allStations.firstOrNull()
            if (target != null) {
                playStation(target)
            }
        }
    }

    fun playNext() {
        val list = _uiState.value.filteredStations.ifEmpty { _uiState.value.allStations }
        if (list.isEmpty()) return
        val currentId = _uiState.value.currentStation?.id
        val idx = list.indexOfFirst { it.id == currentId }
        val nextStation = list[(idx + 1).mod(list.size)]
        playStation(nextStation)
    }

    fun playPrevious() {
        val list = _uiState.value.filteredStations.ifEmpty { _uiState.value.allStations }
        if (list.isEmpty()) return
        val currentId = _uiState.value.currentStation?.id
        val idx = list.indexOfFirst { it.id == currentId }
        val prevIdx = if (idx <= 0) list.lastIndex else idx - 1
        playStation(list[prevIdx])
    }

    private fun syncPlayerState() {
        val controller = mediaController ?: return
        val status = when {
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            controller.isPlaying -> PlaybackStatus.PLAYING
            controller.currentMediaItem != null -> PlaybackStatus.PAUSED
            else -> PlaybackStatus.IDLE
        }
        val activeId = controller.currentMediaItem?.mediaId
        _uiState.update { state ->
            val matched = if (activeId != null) {
                state.allStations.firstOrNull { it.id == activeId } ?: state.currentStation
            } else {
                state.currentStation
            }
            state.copy(
                currentStation = matched,
                playbackStatus = status,
                statusMessage = if (status == PlaybackStatus.PLAYING) null else state.statusMessage
            )
        }
    }

    private fun filterStations(state: RadioUiState): List<RadioStation> {
        val query = state.searchQuery.trim().lowercase()
        return state.allStations.filter { st ->
            val matchesCategory = when (state.selectedCategory) {
                StationCategory.ALL -> true
                StationCategory.FAVORITES -> st.id in state.favoriteIds
                StationCategory.CUSTOM -> st.isCustom
                else -> st.category == state.selectedCategory
            }
            val matchesQuery = query.isEmpty() ||
                st.name.lowercase().contains(query) ||
                st.tags.lowercase().contains(query)
            matchesCategory && matchesQuery
        }
    }

    override fun onCleared() {
        if (isLocationListening) {
            runCatching { locationManager.removeUpdates(locationListener) }
            isLocationListening = false
        }
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
