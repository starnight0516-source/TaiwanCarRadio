package tw.carradio.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tw.carradio.app.model.RadioStation
import tw.carradio.app.model.StationCategory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RadioRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("tw_car_radio_prefs", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "tw_stations_cache.json")

    private val apiMirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    companion object {
        private const val KEY_FAVORITES = "favorite_station_ids"
        private const val KEY_CUSTOM_STATIONS = "custom_stations_json"
        private const val KEY_LAST_STATION_ID = "last_station_id"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_AUTO_RESUME_BOOT = "auto_resume_boot"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }

    fun getFavoriteIds(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun toggleFavorite(stationId: String): Set<String> {
        val current = getFavoriteIds().toMutableSet()
        if (!current.add(stationId)) {
            current.remove(stationId)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return current
    }

    fun getLastStationId(): String? = prefs.getString(KEY_LAST_STATION_ID, null)

    fun saveLastStationId(stationId: String) {
        prefs.edit().putString(KEY_LAST_STATION_ID, stationId).apply()
    }

    fun isKeepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun isAutoResumeOnBoot(): Boolean = prefs.getBoolean(KEY_AUTO_RESUME_BOOT, false)

    fun setAutoResumeOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME_BOOT, enabled).apply()
    }

    fun getCustomStations(): List<RadioStation> {
        val raw = prefs.getString(KEY_CUSTOM_STATIONS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val url = obj.optString("streamUrl").trim()
                val name = obj.optString("name").trim()
                if (url.isBlank() || name.isBlank()) return@mapNotNull null
                RadioStation(
                    id = obj.optString("id").ifBlank { "custom_$idx" },
                    name = name,
                    streamUrl = url,
                    tags = obj.optString("tags", "自訂串流"),
                    codec = if (url.contains(".m3u8", true)) "HLS" else "LIVE",
                    isHls = url.contains(".m3u8", true),
                    isCustom = true,
                    category = StationCategory.CUSTOM
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addCustomStation(name: String, streamUrl: String): List<RadioStation> {
        val list = getCustomStations().toMutableList()
        list.add(
            0,
            RadioStation(
                id = "custom_${UUID.randomUUID()}",
                name = name.trim(),
                streamUrl = streamUrl.trim(),
                tags = "自訂串流",
                codec = if (streamUrl.contains(".m3u8", true)) "HLS" else "LIVE",
                isHls = streamUrl.contains(".m3u8", true),
                isCustom = true,
                category = StationCategory.CUSTOM
            )
        )
        saveCustomStations(list)
        return list
    }

    fun removeCustomStation(stationId: String): List<RadioStation> {
        val list = getCustomStations().filterNot { it.id == stationId }
        saveCustomStations(list)
        return list
    }

    private fun saveCustomStations(stations: List<RadioStation>) {
        val arr = JSONArray()
        stations.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("id", st.id)
                    put("name", st.name)
                    put("streamUrl", st.streamUrl)
                    put("tags", st.tags)
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_STATIONS, arr.toString()).apply()
    }

    suspend fun loadCachedStations(): List<RadioStation> = withContext(Dispatchers.IO) {
        val custom = getCustomStations()
        if (!cacheFile.exists()) return@withContext custom
        val cached = runCatching {
            parseRadioBrowserJson(cacheFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
        custom + cached
    }

    suspend fun fetchTaiwanStationsFromNetwork(): Result<List<RadioStation>> =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (mirror in apiMirrors) {
                try {
                    val endpoint =
                        "$mirror/json/stations/bycountrycodeexact/TW?hidebroken=true&order=clickcount&reverse=true"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 12000
                        setRequestProperty("User-Agent", "TaiwanCarRadio/1.2 (Android Car HeadUnit)")
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        if (conn.responseCode in 200..299) {
                            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            val parsed = parseRadioBrowserJson(body)
                            if (parsed.isNotEmpty()) {
                                cacheFile.writeText(body, Charsets.UTF_8)
                                prefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
                                return@withContext Result.success(getCustomStations() + parsed)
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Throwable) {
                    lastError = e
                }
            }
            Result.failure(lastError ?: IllegalStateException("無法連線至電台清單伺服器"))
        }

    private fun parseRadioBrowserJson(rawJson: String): List<RadioStation> {
        val arr = JSONArray(rawJson)
        val seenUrls = HashSet<String>()
        val result = ArrayList<RadioStation>(arr.length())

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lastCheckOk = obj.optInt("lastcheckok", 1)
            if (lastCheckOk == 0) continue

            val resolvedUrl = obj.optString("url_resolved").ifBlank { obj.optString("url") }.trim()
            val name = obj.optString("name").trim()
            if (resolvedUrl.isBlank() || name.isBlank()) continue
            if (!resolvedUrl.startsWith("http://") && !resolvedUrl.startsWith("https://")) continue
            if (!seenUrls.add(resolvedUrl.lowercase())) continue

            val tags = obj.optString("tags", "").trim()
            val codec = obj.optString("codec", "").trim()
            val bitrate = obj.optInt("bitrate", 0)
            val isHls = obj.optInt("hls", 0) == 1 || resolvedUrl.contains(".m3u8", ignoreCase = true)
            val clickCount = obj.optInt("clickcount", 0)
            val uuid = obj.optString("stationuuid").ifBlank { "tw_${resolvedUrl.hashCode()}" }

            result.add(
                RadioStation(
                    id = uuid,
                    name = name,
                    streamUrl = resolvedUrl,
                    tags = tags,
                    codec = codec,
                    bitrate = bitrate,
                    isHls = isHls,
                    clickCount = clickCount,
                    isCustom = false,
                    category = inferCategory(name, tags)
                )
            )
        }
        return result
    }

    private fun inferCategory(name: String, tags: String): StationCategory {
        val text = "$name $tags".lowercase()
        return when {
            listOf("警廣", "交通", "新聞", "news", "飛碟", "ufo", "中廣新聞", "news98", "98新聞", "漢聲", "rti", "央廣").any { it in text } ->
                StationCategory.NEWS_TRAFFIC

            listOf("古典", "愛樂", "classical", "jazz", "爵士", "piano", "symphony", "philharmonic", "lounge").any { it in text } ->
                StationCategory.CLASSICAL_JAZZ

            listOf("台語", "臺語", "客語", "客家", "寶島", "hakka", "hokkien", "taiwanese", "微笑", "快樂", "鄉土").any { it in text } ->
                StationCategory.TAIWANESE_HAKKA

            listOf("icrt", "english", "英語", "bbc", "voa", "international", "intl").any { it in text } ->
                StationCategory.ENGLISH_INTL

            listOf("hit", "pop", "music", "音樂", "kiss", "best", "asia", "亞洲", "好事", "流行", "rock", "kpop", "jpop", "oldies", "懷舊", "金曲", "城市廣播", "飛揚", "中廣音樂", "i radio").any { it in text } ->
                StationCategory.POP_MUSIC

            else -> StationCategory.EDUCATION_LIFE
        }
    }
}
