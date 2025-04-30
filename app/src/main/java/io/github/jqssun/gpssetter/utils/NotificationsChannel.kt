package io.github.jqssun.gpssetter.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.jqssun.gpssetter.R

object NotificationsChannel {
    const val ACTION_STOP = "io.github.jqssun.gpssetter.ACTION_STOP"
    const val CHANNEL_ID = "gps_setter_channel"
    const val NOTIFICATION_ID = 123

    private fun createChannelIfNeeded(context: Context) {
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.title))
            .setDescription(context.getString(R.string.des))
            .build().also {
                NotificationManagerCompat.from(context).createNotificationChannel(it)
            }
    }

    /**
     * Builds a notification for use with ForegroundService.
     * Does NOT call notify() - use startForeground(id, notification) in your Service.
     */
    fun buildNotification(context: Context, options: (NotificationCompat.Builder) -> Unit): Notification {
        createChannelIfNeeded(context)
        return NotificationCompat.Builder(context, CHANNEL_ID).apply { options(this) }.build()
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}