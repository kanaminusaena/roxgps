package io.github.jqssun.gpssetter.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.jqssun.gpssetter.utils.NotificationsChannel

class LocationService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STOP action: skip notification, just stop
        if (intent?.action == NotificationsChannel.ACTION_STOP) {
            NotificationsChannel.cancelNotification(this)
            val stoppedIntent = Intent("io.github.jqssun.gpssetter.ACTION_SERVICE_STOPPED")
            sendBroadcast(stoppedIntent)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Always show notification IMMEDIATELY for all other cases (even null intent)
        val address = intent?.getStringExtra("address") ?: "Location set"
        try {
            val notification: Notification = NotificationsChannel.buildNotification(this) { builder ->
                val stopIntent = Intent(this, LocationService::class.java).apply {
                    action = NotificationsChannel.ACTION_STOP
                }
                val stopPendingIntent = android.app.PendingIntent.getService(
                    this, 0, stopIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                builder.setSmallIcon(io.github.jqssun.gpssetter.R.drawable.ic_stop)
                    .setContentTitle(getString(io.github.jqssun.gpssetter.R.string.location_set))
                    .setContentText(address)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_EVENT)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .addAction(
                        io.github.jqssun.gpssetter.R.drawable.ic_stop,
                        getString(io.github.jqssun.gpssetter.R.string.stop),
                        stopPendingIntent
                    )
            }
            startForeground(NotificationsChannel.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Defensive: If notification creation fails, stop to avoid crash
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}