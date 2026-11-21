package com.example.remote

import android.app.*
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver()

// Android 14 запрещает запускать камеру/микрофон из фона после загрузки,
// поэтому после перезагрузки показываем уведомление: нажал, и всё снова работает.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("boot", "Перезапуск", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(2, Notification.Builder(context, "boot")
            .setContentTitle("Phone Guard остановлен")
            .setContentText("Нажми, чтобы снова запустить управление")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .build())
    }
}
