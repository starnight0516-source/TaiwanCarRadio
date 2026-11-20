package tw.carradio.app.model

data class WeatherInfo(
    val locationName: String,
    val sourceLabel: String, // "GPS" 或 "網路定位"
    val temperatureC: Int,
    val apparentTempC: Int,
    val humidityPercent: Int,
    val conditionLabel: String,
    val weatherCode: Int,
    val isDay: Boolean,
    val highC: Int?,
    val lowC: Int?,
    val rainChancePercent: Int?,
    val updatedTimeText: String
)
