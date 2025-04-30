package io.github.jqssun.gpssetter.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.jqssun.gpssetter.utils.FileLogger
import io.github.jqssun.gpssetter.utils.NotificationsChannel

class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"
        const val EXTRA_ADDRESS = "address"
        const val ACTION_SERVICE_STOPPED = "io.github.jqssun.gpssetter.ACTION_SERVICE_STOPPED"
        const val ACTION_STOP = "io.github.jqssun.gpssetter.ACTION_STOP"
    }

    private var currentAddress: String? = null
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        FileLogger.log("Service onCreate dipanggil", TAG, "I")
        // Pastikan channel sudah dibuat
        if (!NotificationsChannel.isChannelCreated(this)) {
            NotificationsChannel.createNotificationChannel(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FileLogger.log("Service onStartCommand dipanggil dengan action: ${intent?.action}", TAG, "I")

        when (intent?.action) {
            ACTION_STOP -> {
                FileLogger.log("Menerima perintah stop dari notifikasi", TAG, "I")
                stopServiceGracefully()
                return START_NOT_STICKY
            }
            else -> {
                // Ambil alamat dari intent jika ada
                intent?.getStringExtra(EXTRA_ADDRESS)?.let { address ->
                    currentAddress = address
                    FileLogger.log("Menerima alamat baru: $address", TAG, "D")
                }

                if (!isServiceRunning) {
                    startServiceInForeground()
                } else {
                    updateNotification()
                }
            }
        }

        return START_STICKY
    }

    private fun startServiceInForeground() {
        try {
            FileLogger.log("Memulai service dalam foreground", TAG, "I")
            val notification = NotificationsChannel.createServiceNotification(
                context = this,
                address = currentAddress,
                isRunning = true
            )
            startForeground(NotificationsChannel.NOTIFICATION_ID, notification)
            isServiceRunning = true
            FileLogger.log("Service berhasil dimulai dalam foreground", TAG, "I")
        } catch (e: Exception) {
            FileLogger.log("Gagal memulai foreground service: ${e.message}", TAG, "E")
            stopSelf()
        }
    }

    private fun updateNotification() {
        try {
            FileLogger.log("Memperbarui notifikasi dengan alamat: $currentAddress", TAG, "D")
            NotificationsChannel.updateNotification(
                context = this,
                address = currentAddress,
                isRunning = isServiceRunning
            )
            FileLogger.log("Notifikasi berhasil diperbarui", TAG, "I")
        } catch (e: Exception) {
            FileLogger.log("Gagal memperbarui notifikasi: ${e.message}", TAG, "E")
        }
    }

    private fun stopServiceGracefully() {
        try {
            FileLogger.log("Menghentikan service secara graceful", TAG, "I")
            
            // Batalkan notifikasi
            NotificationsChannel.cancelNotification(this)
            
            // Kirim broadcast bahwa service dihentikan
            sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
            
            // Hentikan foreground service
            if (isServiceRunning) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isServiceRunning = false
            }
            
            // Hentikan service
            stopSelf()
            
            FileLogger.log("Service berhasil dihentikan", TAG, "I")
        } catch (e: Exception) {
            FileLogger.log("Error saat menghentikan service: ${e.message}", TAG, "E")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.log("Service onDestroy dipanggil", TAG, "I")
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}