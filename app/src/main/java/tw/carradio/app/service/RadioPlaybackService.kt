package tw.carradio.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import tw.carradio.app.MainActivity

/**
 * v1.1 修正重點：
 * 1. 切換頻道時於 onMediaItemTransition 立即取消舊的重連排程並重置重試計數，避免舊電台重連干擾新電台。
 * 2. 改用標準 Chrome User-Agent 並允許跨協定轉址（HTTP <-> HTTPS），避免部分電台 CDN 阻擋。
 * 3. 重連前改以 seekToDefaultPosition() 回到直播最新時間點，避免卡在舊緩衝位置。
 */
class RadioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val browserUserAgent =
            "Mozilla/5.0 (Linux; Android 13; CarHeadUnit) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(browserUserAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 換台時立刻清除上一個頻道的重連排程，避免誤觸發或狀態衝突
                reconnectAttempt = 0
                cancelPendingReconnect()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    reconnectAttempt = 0
                    cancelPendingReconnect()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                scheduleReconnect(exoPlayer)
            }
        })

        player = exoPlayer

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionPendingIntent)
            .build()
    }

    private fun scheduleReconnect(exoPlayer: ExoPlayer) {
        if (reconnectAttempt >= 4 || exoPlayer.currentMediaItem == null) return
        cancelPendingReconnect()
        val delayMs = (2_500L * (1 shl reconnectAttempt)).coerceAtMost(16_000L)
        reconnectAttempt++
        val task = Runnable {
            if (exoPlayer.currentMediaItem != null) {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
        reconnectRunnable = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cancelPendingReconnect()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
