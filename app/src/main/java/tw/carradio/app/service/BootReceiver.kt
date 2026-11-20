package tw.carradio.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tw.carradio.app.MainActivity
import tw.carradio.app.data.RadioRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val repo = RadioRepository(context)
            if (repo.isAutoResumeOnBoot() && repo.getLastStationId() != null) {
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_AUTO_PLAY_LAST, true)
                }
                runCatching { context.startActivity(launch) }
            }
        }
    }
}
