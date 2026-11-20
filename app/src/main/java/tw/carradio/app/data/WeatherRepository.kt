package tw.carradio.app.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.carradio.app.model.WeatherInfo
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v1.3 雙保險天氣定位引擎：
 * 1. GPS 有座標時直接使用精準 GPS 經緯度 + 行政區名稱。
 * 2. 若車機／CarPlay 盒子在地下室或尚未透傳 GPS，立即透過免金鑰 HTTPS IP 定位（ipwho.is / geojs.io）
 *    秒速顯示所在縣市即時天氣，等 GPS 鎖定後自動升級為精準 GPS 行政區！
 */
class WeatherRepository(private val context: Context) {

    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        sourceLabel: String = "GPS"
    ): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val weatherUrl =
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=1"

            val body = httpGet(weatherUrl)
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val daily = root.optJSONObject("daily")

            val temp = current.optDouble("temperature_2m", Double.NaN)
            require(!temp.isNaN()) { "天氣資料缺少氣溫欄位" }

            val apparent = current.optDouble("apparent_temperature", temp)
            val humidity = current.optInt("relative_humidity_2m", 0)
            val code = current.optInt("weather_code", 0)
            val isDay = current.optInt("is_day", 1) == 1

            val highC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() }?.roundToInt()
            val lowC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() }?.roundToInt()
            val rainChance = daily?.optJSONArray("precipitation_probability_max")?.optInt(0)

            val locationName = resolvePlaceName(latitude, longitude)
            val timeStr = SimpleDateFormat("HH:mm", Locale.TAIWAN).format(Date())

            WeatherInfo(
                locationName = locationName,
                sourceLabel = sourceLabel,
                temperatureC = temp.roundToInt(),
                apparentTempC = apparent.roundToInt(),
                humidityPercent = humidity,
                conditionLabel = wmoCodeToChinese(code),
                weatherCode = code,
                isDay = isDay,
                highC = highC,
                lowC = lowC,
                rainChancePercent = rainChance,
                updatedTimeText = timeStr
            )
        }
    }

    suspend fun fetchFallbackIpCoordinates(): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. ipwho.is (HTTPS, free, no API key)
            runCatching {
                val json = JSONObject(httpGet("https://ipwho.is/"))
                if (json.optBoolean("success", false)) {
                    val lat = json.optDouble("latitude", Double.NaN)
                    val lon = json.optDouble("longitude", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) return@runCatching Pair(lat, lon)
                }
            }
            // 2. geojs.io fallback (HTTPS, free, no API key)
            val geoJson = JSONObject(httpGet("https://get.geojs.io/v1/ip/geo.json"))
            val lat = geoJson.optString("latitude").toDoubleOrNull()
            val lon = geoJson.optString("longitude").toDoubleOrNull()
            require(lat != null && lon != null) { "無法取得網路定位座標" }
            Pair(lat, lon)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePlaceName(lat: Double, lon: Double): String {
        runCatching {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.TRADITIONAL_CHINESE)
                val results = geocoder.getFromLocation(lat, lon, 1)
                val addr = results?.firstOrNull()
                if (addr != null) {
                    val city = addr.adminArea ?: addr.locality ?: ""
                    val district = addr.subLocality ?: addr.subAdminArea ?: ""
                    val combined = listOf(city, district)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .joinToString(" ")
                    if (combined.isNotBlank()) return combined
                }
            }
        }

        runCatching {
            val url =
                "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                    "?latitude=$lat&longitude=$lon&localityLanguage=zh-Hant"
            val json = JSONObject(httpGet(url))
            val city = json.optString("city").ifBlank { json.optString("principalSubdivision") }.trim()
            val locality = json.optString("locality").trim()
            val combined = listOf(city, locality)
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" ")
            if (combined.isNotBlank()) return combined
        }

        return String.format(Locale.US, "%.2f, %.2f", lat, lon)
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "TaiwanCarRadio/1.3 (Android Car HeadUnit)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun wmoCodeToChinese(code: Int): String = when (code) {
        0 -> "晴朗"
        1 -> "大致晴朗"
        2 -> "多雲時晴"
        3 -> "陰天"
        45, 48 -> "起霧"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "凍雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66, 67 -> "冰雨"
        71, 73, 75, 77 -> "降雪"
        80 -> "短暫陣雨"
        81 -> "陣雨"
        82 -> "強陣雨"
        85, 86 -> "陣雪"
        95 -> "雷陣雨"
        96, 99 -> "劇烈雷雨"
        else -> "多雲"
    }
}
