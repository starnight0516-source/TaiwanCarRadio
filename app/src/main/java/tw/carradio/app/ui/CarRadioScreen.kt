package tw.carradio.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.carradio.app.model.RadioStation
import tw.carradio.app.model.StationCategory
import tw.carradio.app.model.WeatherInfo

private val BgDark = Color(0xFF0D1117)
private val PanelDark = Color(0xFF161B22)
private val CardDark = Color(0xFF1F2733)
private val CardActive = Color(0xFF13334C)
private val WeatherBg = Color(0xFF12263A)
private val AccentCyan = Color(0xFF00D2FF)
private val AccentAmber = Color(0xFFFFB300)
private val TextPrimary = Color(0xFFF0F6FC)
private val TextSecondary = Color(0xFF8B949E)
private val LiveGreen = Color(0xFF2EA043)

@Composable
fun CarRadioScreen(
    uiState: RadioUiState,
    onPlayStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectCategory: (StationCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshWeather: () -> Unit,
    onAddCustomStation: (String, String) -> Unit,
    onDeleteCustomStation: (String) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = BgDark
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LandscapeNowPlayingPanel(
                    uiState = uiState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleFavorite = onToggleFavorite,
                    onRefresh = onRefresh,
                    onRefreshWeather = onRefreshWeather,
                    onOpenAddDialog = { showAddDialog = true },
                    onOpenSettings = { showSettingsDialog = true },
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                )
                StationBrowserPanel(
                    uiState = uiState,
                    onSelectCategory = onSelectCategory,
                    onSearchChange = onSearchChange,
                    onPlayStation = onPlayStation,
                    onToggleFavorite = onToggleFavorite,
                    onDeleteCustomStation = onDeleteCustomStation,
                    gridColumns = if (configuration.screenWidthDp >= 1000) 3 else 2,
                    bottomListPadding = 24,
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Foxtron CAVIRA 直立大螢幕專屬駕駛座艙排版：
            // 上方：視線平視區（標題 + 大字即時天氣資訊卡）
            // 中間：滑動順暢的 2 欄電台選台區
            // 下方：順手好按的大按鈕播放控制座艙（預留車機底部 Dock 安全間距）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 54.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 頂部標題與工具列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "台灣車機廣播",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "v1.3",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onRefresh) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentCyan
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "更新電台", tint = TextSecondary)
                            }
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "新增自訂電台", tint = TextSecondary)
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, "設定", tint = TextSecondary)
                        }
                    }
                }

                // 頂部即時天氣大卡片（GPS 優先，無訊號秒速切換網路定位）
                WeatherStrip(
                    weather = uiState.weather,
                    isLoading = uiState.isWeatherLoading,
                    hint = uiState.weatherHint,
                    onClick = onRefreshWeather
                )

                // 中央電台瀏覽區（自動撐滿剩餘高度）
                StationBrowserPanel(
                    uiState = uiState,
                    onSelectCategory = onSelectCategory,
                    onSearchChange = onSearchChange,
                    onPlayStation = onPlayStation,
                    onToggleFavorite = onToggleFavorite,
                    onDeleteCustomStation = onDeleteCustomStation,
                    gridColumns = if (configuration.screenWidthDp >= 480) 2 else 1,
                    bottomListPadding = 12,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // 底部順手播放控制座艙（就在方向盤右手邊，大字體大按鈕絕不擁擠）
                VerticalBottomPlayerDeck(
                    uiState = uiState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }

    if (showAddDialog) {
        AddCustomStationDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                onAddCustomStation(name, url)
                showAddDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            keepScreenOn = uiState.keepScreenOn,
            autoResumeOnBoot = uiState.autoResumeOnBoot,
            stationCount = uiState.allStations.size,
            onKeepScreenOnChange = onKeepScreenOnChange,
            onAutoResumeChange = onAutoResumeChange,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun VerticalBottomPlayerDeck(
    uiState: RadioUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val current = uiState.currentStation
    val isFav = current != null && current.id in uiState.favoriteIds

    Surface(
        color = PanelDark,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2B3B4E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一列：完整電台名稱 + 直播狀態 + 一鍵愛心
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(uiState.playbackStatus)
                        if (current != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${current.category.label} · ${current.techBadge}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = current?.name ?: "點選上方任一電台開始收聽",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = uiState.statusMessage,
                            color = AccentAmber,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (current != null) {
                    Surface(
                        onClick = { onToggleFavorite(current.id) },
                        shape = CircleShape,
                        color = if (isFav) Color(0xFF3A2D0B) else CardDark,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (isFav) AccentAmber else TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // 第二列：寬敞防誤觸大控制按鈕（上一台 / 播放暫停 / 下一台）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onPrevious,
                    shape = RoundedCornerShape(18.dp),
                    color = CardDark,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SkipPrevious, "上一台", tint = TextPrimary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("上一台", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Surface(
                    onClick = onTogglePlayPause,
                    shape = RoundedCornerShape(20.dp),
                    color = AccentCyan,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PlayButtonContent(uiState.playbackStatus, iconSize = 42)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Surface(
                    onClick = onNext,
                    shape = RoundedCornerShape(18.dp),
                    color = CardDark,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("下一台", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.SkipNext, "下一台", tint = TextPrimary, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherStrip(
    weather: WeatherInfo?,
    isLoading: Boolean,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = WeatherBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF234263)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (weather != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = weatherIconFor(weather.weatherCode, weather.isDay),
                        contentDescription = weather.conditionLabel,
                        tint = if (weather.weatherCode <= 1) AccentAmber else AccentCyan,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${weather.temperatureC}°C",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${weather.locationName} · ${weather.conditionLabel}",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val subParts = mutableListOf<String>()
                        if (weather.lowC != null && weather.highC != null) {
                            subParts.add("${weather.lowC}°~${weather.highC}°")
                        }
                        if (weather.rainChancePercent != null) {
                            subParts.add("降雨 ${weather.rainChancePercent}%")
                        }
                        subParts.add("濕度 ${weather.humidityPercent}%")
                        subParts.add("${weather.sourceLabel} ${weather.updatedTimeText}")
                        Text(
                            text = subParts.joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = hint.ifBlank { "正在取得所在地即時天氣…" },
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重新整理天氣",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun weatherIconFor(code: Int, isDay: Boolean): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay
    2, 3, 45, 48 -> Icons.Default.Cloud
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Default.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Default.AcUnit
    95, 96, 99 -> Icons.Default.Thunderstorm
    else -> Icons.Default.Grain
}

@Composable
private fun LandscapeNowPlayingPanel(
    uiState: RadioUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = uiState.currentStation
    val isFav = current != null && current.id in uiState.favoriteIds

    Surface(
        modifier = modifier,
        color = PanelDark,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF2B3442))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "台灣車機廣播 v1.3",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = onRefresh) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentCyan
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "更新清單", tint = TextSecondary)
                            }
                        }
                        IconButton(onClick = onOpenAddDialog) {
                            Icon(Icons.Default.Add, "新增自訂電台", tint = TextSecondary)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, "設定", tint = TextSecondary)
                        }
                    }
                }

                WeatherStrip(
                    weather = uiState.weather,
                    isLoading = uiState.isWeatherLoading,
                    hint = uiState.weatherHint,
                    onClick = onRefreshWeather
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusPill(uiState.playbackStatus)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = current?.name ?: "請由右側選擇電台",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current?.let { "${it.category.label}  ·  ${it.techBadge}" }
                        ?: "支援背景播放與方向盤切台",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!uiState.statusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiState.statusMessage,
                        color = AccentAmber,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onPrevious,
                    shape = CircleShape,
                    color = CardDark,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SkipPrevious, "上一台", tint = TextPrimary, modifier = Modifier.size(36.dp))
                    }
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = AccentCyan,
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PlayButtonContent(uiState.playbackStatus, iconSize = 46)
                    }
                }

                Surface(
                    onClick = onNext,
                    shape = CircleShape,
                    color = CardDark,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SkipNext, "下一台", tint = TextPrimary, modifier = Modifier.size(36.dp))
                    }
                }
            }

            if (current != null) {
                Button(
                    onClick = { onToggleFavorite(current.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFav) Color(0xFF3A2D0B) else CardDark,
                        contentColor = if (isFav) AccentAmber else TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isFav) "已加入我的最愛" else "加入我的最愛",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayButtonContent(status: PlaybackStatus, iconSize: Int) {
    when (status) {
        PlaybackStatus.BUFFERING, PlaybackStatus.RECONNECTING -> {
            CircularProgressIndicator(
                color = BgDark,
                strokeWidth = 4.dp,
                modifier = Modifier.size((iconSize - 10).dp)
            )
        }
        PlaybackStatus.PLAYING -> {
            Icon(Icons.Default.Pause, "暫停", tint = BgDark, modifier = Modifier.size(iconSize.dp))
        }
        else -> {
            Icon(Icons.Default.PlayArrow, "播放", tint = BgDark, modifier = Modifier.size(iconSize.dp))
        }
    }
}

@Composable
private fun StatusPill(status: PlaybackStatus) {
    val (label, dotColor) = when (status) {
        PlaybackStatus.PLAYING -> "● 直播中" to LiveGreen
        PlaybackStatus.BUFFERING -> "● 連線中…" to AccentCyan
        PlaybackStatus.RECONNECTING -> "● 重連中…" to AccentAmber
        PlaybackStatus.PAUSED -> "● 已暫停" to TextSecondary
        PlaybackStatus.IDLE -> "● 待命" to TextSecondary
    }
    Surface(
        color = Color(0xFF212834),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            color = dotColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StationBrowserPanel(
    uiState: RadioUiState,
    onSelectCategory: (StationCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteCustomStation: (String) -> Unit,
    gridColumns: Int,
    bottomListPadding: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            placeholder = {
                Text("搜尋電台（警廣、飛碟、ICRT、HitFM、中廣）", color = TextSecondary, fontSize = 15.sp)
            },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AccentCyan) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, "清除", tint = TextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PanelDark,
                unfocusedContainerColor = PanelDark,
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = Color(0xFF2B3442),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(StationCategory.entries) { category ->
                val selected = uiState.selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCategory(category) },
                    label = {
                        Text(
                            text = category.label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = PanelDark,
                        labelColor = TextSecondary,
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = BgDark
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = Color(0xFF2B3442)
                    )
                )
            }
        }

        if (uiState.filteredStations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.isRefreshing) "正在載入台灣電台清單…" else "目前分類沒有符合的電台",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = bottomListPadding.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.filteredStations, key = { it.id }) { station ->
                    val isSelected = uiState.currentStation?.id == station.id
                    val isFav = station.id in uiState.favoriteIds
                    StationCard(
                        station = station,
                        isSelected = isSelected,
                        isFavorite = isFav,
                        onClick = { onPlayStation(station) },
                        onToggleFavorite = { onToggleFavorite(station.id) },
                        onDeleteCustom = if (station.isCustom) {
                            { onDeleteCustomStation(station.id) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    station: RadioStation,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteCustom: (() -> Unit)?
) {
    Surface(
        color = if (isSelected) CardActive else CardDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) AccentCyan else Color(0xFF2B3442)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = station.name,
                    color = if (isSelected) AccentCyan else TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${station.category.label} · ${station.techBadge}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDeleteCustom != null) {
                    IconButton(onClick = onDeleteCustom) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "刪除自訂電台",
                            tint = Color(0xFFF85149)
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFavorite) AccentAmber else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCustomStationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("新增自訂廣播串流", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("支援 HLS (.m3u8)、AAC、MP3 等網路電台串流網址。", fontSize = 13.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("電台名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("串流網址 (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url) },
                enabled = name.isNotBlank() && url.startsWith("http", ignoreCase = true)
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingsDialog(
    keepScreenOn: Boolean,
    autoResumeOnBoot: Boolean,
    stationCount: Int,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelDark,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text("車機播放設定（v1.3.0）", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("前景時保持螢幕恆亮", fontWeight = FontWeight.SemiBold)
                        Text("避免行車中螢幕休眠", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = onKeepScreenOnChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = BgDark, checkedTrackColor = AccentCyan)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("車機開機自動續播", fontWeight = FontWeight.SemiBold)
                        Text("發動車機時自動開啟並續播上次電台", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoResumeOnBoot,
                        onCheckedChange = onAutoResumeChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = BgDark, checkedTrackColor = AccentCyan)
                    )
                }
                Text(
                    text = "已載入 $stationCount 個電台；天氣採用 GPS 優先＋網路定位自動備援（Open-Meteo 即時氣象）。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("完成") }
        }
    )
}
