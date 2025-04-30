package io.github.jqssun.gpssetter.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.utils.FileLogger
import io.github.jqssun.gpssetter.utils.NotificationsChannel

class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"
        const val EXTRA_ADDRESS = "address"
        const val ACTION_SERVICE_STOPPED = "io.github.jqssun.gpssetter.ACTION_SERVICE_STOPPED"
    }

    override fun onCreate() {
        super.onCreate()
        FileLogger.log("Service onCreate dijalankan", TAG, "I")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Segera buat dan tampilkan notifikasi untuk mencegah ANR
        val notification = if (intent?.action == NotificationsChannel.ACTION_STOP) {
            FileLogger.log("Menerima perintah stop service", TAG, "I")
            handleStopAction()
            null
        } else {
            FileLogger.log("Memulai service dengan intent: ${intent?.extras}", TAG, "I")
            createAndStartNotification(intent)
        }

        return if (notification != null) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }

    private fun handleStopAction(): Boolean {
        return try {
            FileLogger.log("Menjalankan proses penghentian service", TAG, "I")
            NotificationsChannel.cancelNotification(this)
            sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            FileLogger.log("Service berhasil dihentikan", TAG, "I")
            true
        } catch (e: Exception) {
            FileLogger.log("Error saat menghentikan service: ${e.message}", TAG, "E")
            false
        }
    }

    private fun createAndStartNotification(intent: Intent?): Notification? {
        return try {
            val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: getString(R.string.location_set)
            FileLogger.log("Membuat notifikasi untuk alamat: $address", TAG, "D")

            val notification = createNotification(address)
            startForeground(NotificationsChannel.NOTIFICATION_ID, notification)
            FileLogger.log("Notifikasi foreground berhasil ditampilkan", TAG, "I")
            notification
        } catch (e: Exception) {
            FileLogger.log("Gagal membuat/menampilkan notifikasi: ${e.message}", TAG, "E")
            stopSelf()
            null
        }
    }

    private fun createNotification(address: String): Notification {
        FileLogger.log("Memulai pembuatan notifikasi", TAG, "D")
        return NotificationsChannel.buildNotification(this) { builder ->
            val stopIntent = Intent(this, LocationService::class.java).apply {
                action = NotificationsChannel.ACTION_STOP
            }
            val stopPendingIntent = android.app.PendingIntent.getService(
                this,
                0,
                stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            builder.setSmallIcon(R.drawable.ic_stop)
                .setContentTitle(getString(R.string.location_set))
                .setContentText(address)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(
                    R.drawable.ic_stop,
                    getString(R.string.stop),
                    stopPendingIntent
                )
        }.also {
            FileLogger.log("Notifikasi berhasil dibuat", TAG, "D")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.log("Service onDestroy dipanggil", TAG, "I")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}