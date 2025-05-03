package com.roxgps.helper // Sesuaikan dengan package utility kamu

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build // Perlu untuk RECEIVER_NOT_EXPORTED
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.roxgps.R // Import R dari project kamu
import com.roxgps.utils.NotificationsChannel // Import objek singleton NotificationsChannel

// Helper class untuk mengelola Notifikasi dan Broadcast Receiver terkait aksinya
class NotificationHelper(
    private val context: Context, // Context Activity atau Application (pilih yang sesuai lifecycle notifikasi)
    private val notificationsChannel: NotificationsChannel, // Objek NotificationsChannel yang sudah ada
    private val onStopAction: () -> Unit // Lambda yang akan dipanggil saat tombol Stop diklik
) {

    // Broadcast Receiver untuk menangani aksi dari notifikasi (misal klik tombol Stop)
    // Objek receiver ini ada di dalam helper ini sekarang
    private val stopActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Memeriksa apakah action intent sesuai dengan action Stop yang kita set di notifikasi
            if (intent?.action == NotificationsChannel.ACTION_STOP) {
                // Memanggil lambda callback yang diberikan dari Activity
                onStopAction()
            }
        }
    }

    // Menampilkan notifikasi awal (misal saat lokasi diset atau proses dimulai)
    // address: teks alamat atau info lokasi yang ditampilkan di notifikasi
    fun showStartNotification(address: String) {
        // Membuat Intent yang akan dikirim saat tombol Stop di notifikasi diklik
        val stopIntent = Intent(NotificationsChannel.ACTION_STOP).apply { // <-- UBAH INI!
             // Tidak perlu lagi: Intent(context, NotificationActionReceiver::class.java)
             // action = NotificationsChannel.ACTION_STOP // Tidak perlu diset lagi karena sudah di constructor Intent
             // Bisa tambahkan extra data lain ke intent jika diperlukan
        }

        // Membuat PendingIntent dari Intent. PendingIntent ini yang dilekatkan ke tombol di notifikasi.
        // FLAG_UPDATE_CURRENT: Jika PendingIntent dengan request code yang sama sudah ada, update extranya.
        // FLAG_IMMUTABLE: Membuat PendingIntent tidak bisa diubah (disarankan di API level 23+ untuk keamanan).
        val stopPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            0, // Request code: identifikasi PendingIntent ini (bisa 0 atau angka unik jika banyak pending intent)
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Menggunakan objek NotificationsChannel untuk benar-benar menampilkan notifikasi
        notificationsChannel.showNotification(context) { builder ->
            // Mengatur properti notifikasi menggunakan builder yang disediakan oleh NotificationsChannel
            builder.setSmallIcon(R.drawable.ic_stop) // Icon kecil notifikasi (wajib)
            builder.setContentTitle(context.getString(R.string.location_set)) // Judul notifikasi
            builder.setContentText(address) // Isi teks notifikasi
            builder.setAutoCancel(true) // Notifikasi hilang otomatis saat diklik (meski ongoing, kontennya)
            builder.setOngoing(false) // Membuat notifikasi ongoing (tidak bisa di-swipe hilang dari panel notifikasi)
            builder.setCategory(Notification.CATEGORY_EVENT) // Kategori notifikasi
            builder.priority = NotificationCompat.PRIORITY_HIGH // Prioritas notifikasi

            // Menambahkan tombol aksi "Stop" ke notifikasi
            builder.addAction(
                R.drawable.ic_stop, // Icon untuk tombol aksi
                context.getString(R.string.stop), // Teks untuk tombol aksi
                stopPendingIntent // PendingIntent yang akan dieksekusi saat tombol diklik
            )
            // Bisa tambahkan aksi lain jika ada tombol lain di notifikasi
            // builder.addAction(...)
        }
    }

    // Menghilangkan semua notifikasi yang dikelola oleh channel ini
    fun cancelNotification() {
        notificationsChannel.cancelAllNotifications(context)
    }

    // Mendaftarkan Broadcast Receiver di Context
    // Ini perlu dipanggil dari lifecycle Activity (misal di onCreate)
    fun registerReceiver() {
        // Membuat IntentFilter untuk action yang ingin didengarkan oleh receiver
        val filter = IntentFilter(NotificationsChannel.ACTION_STOP)
        // Mendaftarkan receiver
        // Menggunakan bendera RECEIVER_NOT_EXPORTED untuk keamanan (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             context.registerReceiver(stopActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
             // Untuk versi Android di bawah 33, gunakan cara lama
             context.registerReceiver(stopActionReceiver, filter)
        }
    }

    // Menghentikan pendaftaran Broadcast Receiver
    // Ini perlu dipanggil dari lifecycle Activity (misal di onDestroy) untuk mencegah memory leak
    fun unregisterReceiver() {
        // Memastikan context masih valid dan receiver sudah didaftarkan sebelum unregister
        // Tidak perlu cek isInitialized untuk stopActionReceiver karena dia objek
        try {
            context.unregisterReceiver(stopActionReceiver)
        } catch (e: IllegalArgumentException) {
            // Handle case jika receiver belum didaftarkan atau sudah di-unregister
            // Misalnya log pesan error tapi tidak crash
            e.printStackTrace() // atau logging framework lain
        }
    }

    // Fungsi lain yang mungkin berguna:
    // fun updateNotification(notificationId: Int, address: String) { ... } // Untuk update notifikasi yang sudah ada
}
