package io.github.jqssun.gpssetter.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import android.os.Build
import io.github.jqssun.gpssetter.R

object NotificationsChannel {
    private const val TAG = "NotificationsChannel"
    
    // Channel constants
    const val CHANNEL_ID = "location_service_channel"
    const val NOTIFICATION_ID = 1001
    
    // Actions
    const val ACTION_STOP = "io.github.jqssun.gpssetter.action.STOP_SERVICE"

    fun createNotificationChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                FileLogger.log("Membuat notification channel", TAG, "D")
                
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                
                FileLogger.log("Notification channel berhasil dibuat", TAG, "I")
            }
        } catch (e: Exception) {
            FileLogger.log("Gagal membuat notification channel: ${e.message}", TAG, "E")
        }
    }

    fun createServiceNotification(
        context: Context,
        address: String?,
        isRunning: Boolean
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(address ?: context.getString(R.string.location_set))
            .setOngoing(isRunning)
            .setAutoCancel(!isRunning)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun updateNotification(context: Context, address: String?, isRunning: Boolean) {
        try {
            val notification = createServiceNotification(context, address, isRunning)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            FileLogger.log("Gagal memperbarui notifikasi: ${e.message}", TAG, "E")
        }
    }

    fun cancelNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            FileLogger.log("Gagal membatalkan notifikasi: ${e.message}", TAG, "E")
        }
    }

    fun isChannelCreated(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.getNotificationChannel(CHANNEL_ID) != null
        } else {
            true
        }
    }
}
