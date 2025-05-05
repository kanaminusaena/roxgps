package com.roxgps.helper // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk NotificationHelper
// =====================================================================

import android.app.Notification // Untuk tipe data Notification (digunakan di setCategory)
import android.app.PendingIntent // Untuk PendingIntent
import android.content.BroadcastReceiver // Untuk BroadcastReceiver object
import android.content.Context // Untuk Context
import android.content.Intent // Untuk Intent
import android.content.IntentFilter // Untuk IntentFilter
import android.os.Build // Untuk cek versi Android (PendingIntent flags, Receiver exported)
import androidx.core.app.NotificationCompat // Untuk NotificationCompat.Builder (API notifikasi umum)
import androidx.core.app.NotificationManagerCompat // Untuk membatalkan notifikasi
import com.roxgps.R // Import R untuk resources (drawable, string)
import com.roxgps.utils.NotificationsChannel // Import objek NotificationsChannel singleton (asumsi ini pengelola channel dan ID notifikasi)

/**
 * Helper class buat ngurusin logika notifikasi, termasuk menampilkan dan membatalkan notifikasi,
 * serta menangani aksi dari tombol di notifikasi (misal tombol Stop).
 *
 * @param context Context (Activity atau Service) yang digunakan untuk menampilkan notifikasi dan mendaftarkan receiver.
 * @param notificationsChannel Objek singleton [NotificationsChannel] yang mengelola channel notifikasi aplikasi dan ID notifikasi.
 * // onStopAction lambda DIPINDAHKAN dari constructor ke method registerReceiver()
 */
// Anotasi @Inject constructor() agar Hilt bisa menyediakan instance
class NotificationHelper @Inject constructor(
    // Hilt bisa menyediakan Context
    @ApplicationContext // atau @ActivityContext jika scope-nya Activity
    private val context: Context,
    // Asumsi NotificationsChannel juga disediakan oleh Hilt atau object singleton
    private val notificationsChannel: NotificationsChannel // Objek singleton channel notifikasi (Asumsi Hilt bisa provide ini)
) {

    // Broadcast Receiver INTERNAL helper untuk menangani aksi dari notifikasi.
    // Dia dideklarasikan di dalam helper dan siklus hidupnya dikelola oleh helper.
    // Callback onReceive AKAN MEMANGGIL lambda yang diberikan di registerReceiver().
    private val stopActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Cek apakah action Intent sesuai dengan action Stop yang kita harapkan
            if (intent?.action == NotificationsChannel.ACTION_STOP) { // Menggunakan konstanta action dari NotificationsChannel. BAGUS!
                // Memanggil lambda aksi Stop. Lambda ini disimpan oleh method registerReceiver().
                 // Bagaimana receiver ini tahu lambda mana yang harus dipanggil?
                 // Opsi 1: Simpan lambda di properti class.
                 // Opsi 2: Receiver internal memanggil method di helper yang punya akses ke lambda terakhir.
                 // Opsi 1 (simpan lambda):
                 this@NotificationHelper.onStopActionCallback?.invoke() // Membutuhkan properti onStopActionCallback
            }
        }
    }

     // Properti untuk menyimpan lambda aksi Stop terakhir yang diberikan ke registerReceiver()
     private var onStopActionCallback: (() -> Unit)? = null


    // =====================================================================
    // Method Publik untuk Mengelola Notifikasi
    // =====================================================================

    /**
     * Menampilkan notifikasi awal saat proses dimulai.
     * Logic diambil dari kode asli BaseMapActivity/MapActivity.
     *
     * @param address String alamat atau teks lain yang akan ditampilkan di notifikasi.
     */
    fun showStartNotification(address: String) {
        // Membuat Intent yang akan dikirim saat tombol Stop di notifikasi diklik.
        // Intent ini hanya perlu action string yang unik. Targetnya adalah BroadcastReceiver internal di helper ini.
        val stopIntent = Intent(NotificationsChannel.ACTION_STOP) // Menggunakan konstanta action dari NotificationsChannel. BAGUS!

        // Membuat PendingIntent dari Intent stopAction. PendingIntent ini yang dilekatkan ke tombol di notifikasi.
        // getBroadcast() digunakan karena action ini akan diterima oleh BroadcastReceiver internal.
        // Flags penting untuk keamanan dan update.
        val stopPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context, // Context (dari constructor helper)
            0, // Request code (bisa 0 atau angka unik jika perlu membedakan pending intent)
            stopIntent, // Intent yang akan dikirim
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_UPDATE_CURRENT (jika intent berubah) dan FLAG_IMMUTABLE (wajib >= S)
        )

        // Menggunakan objek NotificationsChannel untuk benar-benar menampilkan notifikasi.
        // Asumsi NotificationsChannel.showNotification(context, builder lambda) yang sebenarnya membuat notifikasi.
        notificationsChannel.showNotification(context) { builder -> // Memanggil method di objek channel notifikasi. BAGUS!
            // Mengatur properti dasar notifikasi menggunakan NotificationCompat.Builder.
            builder.setSmallIcon(R.drawable.ic_stop) // Icon notifikasi (Resource ID OK)
            builder.setContentTitle(context.getString(R.string.location_set)) // Judul notifikasi (Resource ID OK)
            builder.setContentText(address) // Isi teks notifikasi (misal: alamat)
            builder.setOngoing(true) // Notifikasi tidak bisa di-swipe away - cocok untuk proses berjalan
            builder.setCategory(Notification.CATEGORY_SERVICE) // Kategori notifikasi - cocok untuk service
            builder.priority = NotificationCompat.PRIORITY_HIGH // Prioritas tinggi

            // Menambahkan tombol aksi "Stop" ke notifikasi.
            builder.addAction( // Menambahkan aksi
                R.drawable.ic_stop, // Icon tombol (Resource ID OK)
                context.getString(R.string.stop), // Teks tombol (Resource ID OK)
                stopPendingIntent // PendingIntent yang akan dijalankan saat tombol diklik (sudah dibuat di atas)
            )

             // Jika notifikasi perlu content intent (saat notifikasi diklik, buka Activity), tambahkan di sini:
             // val contentIntent = Intent(context, TargetActivity::class.java)
             // val contentPendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)
             // builder.setContentIntent(contentPendingIntent)
        }
    }

    /**
     * Menghentikan/menghapus notifikasi.
     * Logic diambil dari kode asli BaseMapActivity/MapActivity.
     * Menggunakan NotificationsChannel.
     */
    fun cancelNotification() {
        // Memanggil method di objek channel notifikasi untuk menghapus notifikasi.
        notificationsChannel.cancelNotification(context) // Asumsi NotificationsChannel punya method ini dan butuh Context. BAGUS!
         // Opsi: Jika perlu membatalkan SEMUA notifikasi yang dibuat oleh channel ini:
         // NotificationManagerCompat.from(context).cancelAll()
         // Tapi biasanya cancelNotification(context) di NotificationsChannel sudah handle ID spesifik.
    }

    /**
     * Mendaftarkan BroadcastReceiver internal secara dinamis.
     * HARUS dipanggil saat siklus hidup yang sesuai (misal: onCreate/onResume Activity/Service).
     * Menerima lambda untuk aksi Stop.
     *
     * @param onStopAction Lambda yang akan dijalankan saat tombol Stop diklik.
     */
    fun registerReceiver(onStopAction: () -> Unit) { // Lambda dipindahkan ke sini
        this.onStopActionCallback = onStopAction // Simpan lambda di properti
        val filter = IntentFilter(NotificationsChannel.ACTION_STOP) // Filter untuk action Stop (OK)
        // Daftarkan receiver dengan filter
        // Menggunakan flag Context.RECEIVER_NOT_EXPORTED untuk keamanan (Android 13+).
        // Receiver ini hanya akan menerima broadcast dari dalam aplikasi lo sendiri.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stopActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED) // Context (dari constructor)
        } else {
            context.registerReceiver(stopActionReceiver, filter) // Context (dari constructor)
        }
         // CATATAN: Jika receiver ini perlu menerima broadcast dari process LAIN (misal dari Xposed module),
         // lo mungkin perlu menggunakan flag Context.RECEIVER_EXPORTED, tapi HATI-HATI dengan keamanan.
         // Untuk kasus tombol notifikasi yang diklik di SystemUI, broadcastnya akan dikirim ke aplikasi lo,
         // jadi RECEIVER_NOT_EXPORTED seharusnya CUKUP jika receiver hanya untuk handle tombol notifikasi.
    }

    /**
     * Melepaskan pendaftaran BroadcastReceiver internal.
     * PENTING untuk dipanggil di siklus hidup yang sesuai (misal: onDestroy Activity/Service)
     * untuk mencegah memory leak.
     */
    fun unregisterReceiver() {
        context.unregisterReceiver(stopActionReceiver) // Context (dari constructor)
        this.onStopActionCallback = null // Hapus referensi lambda setelah unregister
    }
}
