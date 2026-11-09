package app.woorichadiary.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.woorichadiary.DiaryApp

/** Records signals while the UI is hidden, including while driving. Never speaks on its own. */
class DiaryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "우리 차 일기", NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(
            1,
            Notification.Builder(this, CHANNEL)
                .setContentTitle("우리 차가 오늘을 기억하고 있어요")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .build(),
        )
        (application as DiaryApp).controller.startVehicle()
        return START_STICKY
    }

    private companion object {
        const val CHANNEL = "diary"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, DiaryService::class.java))
        }
    }
}
