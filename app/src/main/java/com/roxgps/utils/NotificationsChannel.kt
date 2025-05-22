package com.roxgps.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.roxgps.R

object NotificationsChannel {
    // === Perbaiki nilai konstanta agar unik ===
    const val ACTION_START_SERVICE = "com.roxgps.ACTION_START_SERVICE" // Nilai UNIK untuk START
    const val ACTION_STOP = "com.roxgps.ACTION_STOP" // Nilai UNIK untuk aksi tombol notifikasi
    const val ACTION_STOP_SERVICE = "com.roxgps.ACTION_STOP_SERVICE" // Nilai UNIK untuk perintah STOP ke Service
    // =========================================

    const val CHANNEL_ID = "rox_gps_channel"
    const val NOTIFICATION_ID = 123

    // ... metode createChannelIfNeeded, createNotification, showNotification, cancelNotification yang sudah benar ...

    // Membuat NotificationChannel jika diperlukan (untuk API 26+)
    private fun createChannelIfNeeded(context: Context) {
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT).apply {
            setName(context.getString(R.string.title))
            setDescription(context.getString(R.string.des))
        }.build().also {
            NotificationManagerCompat.from(context).createNotificationChannel(it)
        }
    }

    private fun createNotification(context: Context, options: (NotificationCompat.Builder) -> Unit): Notification {
        createChannelIfNeeded(context)
        return NotificationCompat.Builder(context, CHANNEL_ID).apply { options(this) }.build()
    }

    fun showNotification(context: Context, options: (NotificationCompat.Builder) -> Unit): Notification {
        val notification = createNotification(context, options)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        return notification
    }

    fun cancelNotification(context: Context, notificationId: Int = NOTIFICATION_ID) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}