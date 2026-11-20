# 台灣車機廣播（TaiwanCarRadio）Android APK 專案

專為 **車用安卓機（Android 7.0+ 橫屏車機）** 打造的台灣網路廣播播放器，支援即時串流、背景播放（切換導航不中斷）、方向盤多媒體按鍵切台、斷線自動重連，並自動同步全台網路廣播電台清單。

## 核心特色
- **全台網路廣播電台自動同步**：透過免費公開的 [Radio Browser API](https://docs.radio-browser.info/)（內建 `de1` / `nl1` / `at1` 三節點容錯）即時抓取台灣所有可用電台（自動排除失效連結），並快取於本機達成秒開。
- **支援所有主流格式與自訂電台**：採用 [AndroidX Media3](https://developer.android.com/jetpack/androidx/releases/media3)（ExoPlayer + HLS），原生支援 `.m3u8`、AAC、MP3、Icecast/Shoutcast，並可於 App 內手動新增自訂串流網址。
- **真正車機背景播放**：透過 `MediaSessionService` 前景服務搭配 `WAKE_MODE_NETWORK`、音訊焦點自動避讓（導航語音／來電自動暫停與恢復）及隧道斷網指數退避重連。
- **車用橫屏大按鈕 UI**：深色抗眩光駕駛介面、超大播放／上下台按鈕、一鍵收藏、分類篩選與即時關鍵字搜尋，並支援車機方向盤多媒體鍵。

---

## 如何產出可安裝的 `.apk` 檔案？

### 方法 A：免安裝電腦環境 — 使用 GitHub Actions 雲端自動打包（最推薦）
1. 將本 ZIP 解壓縮後的全部檔案上傳到你的免費 GitHub 儲存庫（Repository）。
2. 點選儲存庫上方的 **Actions** 頁籤 → 選擇 **Build TaiwanCarRadio APK** → 按 **Run workflow**（或推送程式碼時會自動觸發）。
3. 約 2～3 分鐘編譯完成後，在該次執行結果頁面下方 **Artifacts** 區塊下載 `TaiwanCarRadio-APK`，解壓即可取得 `app-debug.apk`。

### 方法 B：使用 Android Studio 本機編譯
1. 安裝 [Android Studio](https://developer.android.com/studio)（需 JDK 17 與 Android SDK 35）。
2. 開啟解壓後的 `TaiwanCarRadio` 資料夾，等待 Gradle Sync 完成。
3. 點選選單 **Build → Build Bundle(s) / APK(s) → Build APK(s)**，產出檔案位於 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 安裝到車用安卓機步驟
1. 將 `app-debug.apk` 複製到隨身碟（USB）或透過手機藍牙／雲端下載到車機。
2. 在安卓車機開啟「檔案管理員」，點選 `app-debug.apk` 並允許「安裝未知來源應用程式」。
3. 首次開啟請允許通知權限（供背景播放通知欄顯示），建議於車機系統的「電池優化／背景白名單」中將「台灣車機廣播」設為不受限制，確保切換導航時背景播放完全不被系統清理。
