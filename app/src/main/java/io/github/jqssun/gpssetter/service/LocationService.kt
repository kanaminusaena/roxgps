package io.github.jqssun.gpssetter.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.utils.NotificationsChannel

/**
 * Service untuk menangani pembaruan lokasi dan notifikasi.
 * Service ini bertanggung jawab untuk:
 * - Menampilkan notifikasi persisten saat pembaruan lokasi aktif
 * - Menangani permintaan berhenti melalui aksi notifikasi
 * - Menyiarkan perubahan status service
 */
class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"
        const val EXTRA_ADDRESS = "address"
        const val ACTION_SERVICE_STOPPED = "io.github.jqssun.gpssetter.ACTION_SERVICE_STOPPED"
    }

    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.d(TAG, "Service onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "Service onDestroy")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            if (intent?.action == NotificationsChannel.ACTION_STOP) {
                handleStopAction()
                START_NOT_STICKY
            } else {
                handleStartAction(intent)
                START_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan dalam service: ${e.message}", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    /**
     * Menangani aksi penghentian service
     */
    private fun handleStopAction() {
        Log.d(TAG, "Menghentikan service")
        NotificationsChannel.cancelNotification(this)
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Menangani aksi memulai service
     */
    private fun handleStartAction(intent: Intent?) {
        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: getString(R.string.location_set)
        Log.d(TAG, "Memulai service dengan alamat: $address")
        
        try {
            val notification = createNotification(address)
            startForeground(NotificationsChannel.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat notifikasi: ${e.message}", e)
            stopSelf()
        }
    }

    /**
     * Membuat notifikasi untuk service
     */
    private fun createNotification(address: String): Notification {
        return NotificationsChannel.buildNotification(this) { builder ->
            val stopIntent = createStopIntent()
            val stopPendingIntent = createStopPendingIntent(stopIntent)
            
            builder.apply {
                setSmallIcon(R.drawable.ic_stop)
                setContentTitle(getString(R.string.location_set))
                setContentText(address)
                setOngoing(true)
                setAutoCancel(false)
                setCategory(Notification.CATEGORY_EVENT)
                setPriority(NotificationCompat.PRIORITY_HIGH)
                addAction(
                    R.drawable.ic_stop,
                    getString(R.string.stop),
                    stopPendingIntent
                )
            }
        }
    }

    /**
     * Membuat intent untuk aksi stop
     */
    private fun createStopIntent() = Intent(this, LocationService::class.java).apply {
        action = NotificationsChannel.ACTION_STOP
    }

    /**
     * Membuat PendingIntent untuk aksi stop
     */
    private fun createStopPendingIntent(stopIntent: Intent) = android.app.PendingIntent.getService(
        this,
        0,
        stopIntent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    override fun onBind(intent: Intent?): IBinder? = null
}