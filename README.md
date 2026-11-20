# 台灣車機廣播（TaiwanCarRadio）v1.2.0

## v1.2.0 新增與修正內容
- **GPS 所在地即時天氣列**：使用車機原生 `LocationManager` GPS 定位（不依賴 Google Play 服務），自動查詢免金鑰 [Open-Meteo](https://veri-glow.com/open-meteo.com/en/docs/) 即時氣溫、天氣狀態、高低溫、降雨機率與濕度，並透過 Android `Geocoder` 與 [BigDataCloud](https://www.bigdatacloud.com/blog/convert-getcurrentposition-free-reversegeocoding-api) 雙層轉換顯示台灣縣市行政區名稱；行駛滿 3 公里或每 15 分鐘自動更新，也可點天氣列手動刷新。
- **包含 v1.1.0 全部修正**：換台徹底釋放舊連線與 3 筆輕量視窗（解決換台連不上）、直立大螢幕上下排版、底部 72dp 安全留白避開車機工具列。
