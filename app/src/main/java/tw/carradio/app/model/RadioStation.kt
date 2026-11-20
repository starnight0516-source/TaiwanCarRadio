package tw.carradio.app.model

enum class StationCategory(val label: String) {
    ALL("全部電台"),
    FAVORITES("我的最愛"),
    NEWS_TRAFFIC("新聞/交通"),
    POP_MUSIC("流行音樂"),
    CLASSICAL_JAZZ("古典/爵士"),
    TAIWANESE_HAKKA("台語/客語"),
    ENGLISH_INTL("英語/國際"),
    EDUCATION_LIFE("教育/綜合"),
    CUSTOM("自訂電台")
}

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val tags: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val isHls: Boolean = false,
    val clickCount: Int = 0,
    val isCustom: Boolean = false,
    val category: StationCategory = StationCategory.EDUCATION_LIFE
) {
    val techBadge: String
        get() {
            val parts = mutableListOf<String>()
            if (codec.isNotBlank()) parts.add(codec.uppercase())
            if (bitrate > 0) parts.add("${bitrate}k")
            if (isHls || streamUrl.contains(".m3u8", ignoreCase = true)) parts.add("HLS")
            return if (parts.isEmpty()) "LIVE" else parts.joinToString(" · ")
        }
}
