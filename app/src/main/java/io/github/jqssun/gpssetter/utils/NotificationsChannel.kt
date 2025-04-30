package io.github.jqssun.gpssetter.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.jqssun.gpssetter.R

object NotificationsChannel {
    const val ACTION_STOP = "io.github.jqssun.gpssetter.ACTION_STOP"
    const val CHANNEL_ID = "gps_setter_channel"
    const val NOTIFICATION_ID = 123
    private const val TAG = "NotificationsChannel"

    private fun createChannelIfNeeded(context: Context) {
        try {
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.title)) // Should be clear, e.g. "GPS Setter"
                .setDescription(context.getString(R.string.des)) // E.g. "Location update notifications"
                .build().also {
                    NotificationManagerCompat.from(context).createNotificationChannel(it)
                }
        } catch (e: Exception) {
            // Log or ignore - channel might already exist or ROM bug
            Log.d(TAG, "Failed to create notification channel", e)
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
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}