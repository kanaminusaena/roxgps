package io.github.jqssun.gpssetter.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.service.LocationService

object NotificationsChannel {
    private const val TAG = "NotificationsChannel"
    
    // Channel constants
    const val CHANNEL_ID = "location_service_channel"
    const val NOTIFICATION_ID = 1001
    
    // Actions
    const val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.action.STOP_SERVICE"
    
    /**
     * Membuat dan menginisialisasi notification channel untuk Android O dan yang lebih baru
     */
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

    /**
     * Membuat notifikasi untuk location service
     */
    fun createServiceNotification(
        context: Context,
        address: String?,
        isRunning: Boolean
    ): Notification {
        FileLogger.log("Membuat notifikasi service dengan alamat: $address", TAG, "D")
        
        try {
            // Buat Intent untuk action stop
            val stopIntent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
            }

            // Buat PendingIntent dengan flag yang tepat
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val stopPendingIntent = PendingIntent.getService(
                context,
                0,
                stopIntent,
                flags
            )

            // Buat notification builder
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stop)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(address ?: context.getString(R.string.location_set))
                .setOngoing(isRunning)
                .setAutoCancel(!isRunning)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(
                    R.drawable.ic_stop,
                    context.getString(R.string.stop),
                    stopPendingIntent
                )
                .build()
                .apply {
                    flags = flags or Notification.FLAG_NO_CLEAR
                }
        } catch (e: Exception) {
            FileLogger.log("Gagal membuat notifikasi: ${e.message}", TAG, "E")
            throw e
        }
    }

    /**
     * Memperbarui notifikasi yang sudah ada
     */
    fun updateNotification(context: Context, address: String?, isRunning: Boolean) {
        try {
            FileLogger.log("Memperbarui notifikasi dengan alamat: $address", TAG, "D")
            
            val notification = createServiceNotification(context, address, isRunning)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            FileLogger.log("Notifikasi berhasil diperbarui", TAG, "I")
        } catch (e: Exception) {
            FileLogger.log("Gagal memperbarui notifikasi: ${e.message}", TAG, "E")
        }
    }

    /**
     * Membatalkan notifikasi yang sedang ditampilkan
     */
    fun cancelNotification(context: Context) {
        try {
            FileLogger.log("Membatalkan notifikasi", TAG, "D")
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            
            FileLogger.log("Notifikasi berhasil dibatalkan", TAG, "I")
        } catch (e: Exception) {
            FileLogger.log("Gagal membatalkan notifikasi: ${e.message}", TAG, "E")
        }
    }

    /**
     * Memeriksa apakah notification channel sudah dibuat
     */
    fun isChannelCreated(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.getNotificationChannel(CHANNEL_ID) != null
        } else {
            true
        }
    }
}