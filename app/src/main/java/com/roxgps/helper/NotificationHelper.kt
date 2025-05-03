package com.roxgps.helper // Pastikan package ini sesuai

import android.app.Notification // Untuk tipe data Notification
import android.app.PendingIntent // Untuk PendingIntent
import android.content.BroadcastReceiver // Untuk BroadcastReceiver object
import android.content.Context // Untuk Context
import android.content.Intent // Untuk Intent
import android.content.IntentFilter // Untuk IntentFilter
import android.os.Build // Untuk cek versi Android
import androidx.core.app.NotificationCompat // Untuk NotificationCompat.Builder
import com.roxgps.R // Import R untuk resources (drawable, string)
import com.roxgps.utils.NotificationsChannel // Import objek NotificationsChannel singleton

// Helper class buat ngurusin logika notifikasi
// Dideklarasi di Activity dan dipanggil fungsi-fungsinya
class NotificationHelper(
    private val context: Context,
    private val notificationsChannel: NotificationsChannel, // Objek singleton channel notifikasi
    private val onStopAction: () -> Unit // Lambda yang dijalankan saat tombol Stop di notifikasi diklik
) {

    // Broadcast Receiver untuk menangani aksi dari notifikasi (objek di dalam helper)
    private val stopActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Cek apakah action Intent sesuai dengan action Stop
            if (intent?.action == NotificationsChannel.ACTION_STOP) {
                onStopAction.invoke() // Panggil lambda aksi Stop yang di-pass dari Activity
            }
        }
    }

    // Menampilkan notifikasi awal saat proses dimulai
    fun showStartNotification(address: String) {
        // Membuat Intent yang akan dikirim saat tombol Stop di notifikasi diklik
        // Intent hanya perlu action string, tidak perlu target kelas Receiver spesifik yang terpisah
        val stopIntent = Intent(NotificationsChannel.ACTION_STOP) // <-- Intent hanya pakai action string

        // Membuat PendingIntent dari Intent. PendingIntent ini yang dilekatkan ke tombol di notifikasi.
        // getBroadcast() digunakan karena action ini akan diterima oleh BroadcastReceiver
        val stopPendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(
                context,
                0, // Request code (bisa 0 atau angka unik)
                stopIntent, // Intent dengan action string
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Flags yang direkomendasikan
            )
        } else {
             PendingIntent.getBroadcast(
                context,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
             )
        }


        // Menggunakan objek NotificationsChannel untuk benar-benar menampilkan notifikasi
        notificationsChannel.showNotification(context) { builder ->
            // Mengatur properti dasar notifikasi
            builder.setSmallIcon(R.drawable.ic_stop) // Icon notifikasi
            builder.setContentTitle(context.getString(R.string.location_set)) // Judul notifikasi
            builder.setContentText(address) // Isi teks notifikasi (misal: alamat)
            builder.setOngoing(true) // Notifikasi tidak bisa di-swipe away
            builder.setCategory(Notification.CATEGORY_SERVICE) // Kategori notifikasi
            builder.priority = NotificationCompat.PRIORITY_HIGH // Prioritas tinggi

            // Menambahkan tombol aksi "Stop" ke notifikasi
            builder.addAction(
                R.drawable.ic_stop, // Icon tombol
                context.getString(R.string.stop), // Teks tombol (pastikan string 'stop' ada di strings.xml)
                stopPendingIntent // PendingIntent yang akan dijalankan saat tombol diklik
            )

             // Jika notifikasi perlu content intent (saat notifikasi diklik), tambahkan di sini:
             // val contentIntent = Intent(context, TargetActivity::class.java)
             // val contentPendingIntent = PendingIntent.getActivity(...)
             // builder.setContentIntent(contentPendingIntent)
        }
    }

    // Menghentikan/menghapus notifikasi
    fun cancelNotification() {
        notificationsChannel.cancelNotification(context) // Panggil fungsi cancel di NotificationsChannel
    }

    // Mendaftarkan BroadcastReceiver secara dinamis
    fun registerReceiver() {
        val filter = IntentFilter(NotificationsChannel.ACTION_STOP) // Filter untuk action Stop
        // Daftarkan receiver dengan filter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stopActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED) // Flag untuk Android 13+
        } else {
            context.registerReceiver(stopActionReceiver, filter)
        }

    }

    // Melepaskan pendaftaran BroadcastReceiver (penting di onDestroy Activity)
    fun unregisterReceiver() {
        context.unregisterReceiver(stopActionReceiver)
    }
}
