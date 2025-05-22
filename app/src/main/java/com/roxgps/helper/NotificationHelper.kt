package com.roxgps.helper // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk NotificationHelper
// =====================================================================

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import com.roxgps.R
import com.roxgps.ui.MapActivity
import com.roxgps.utils.Relog
import com.roxgps.utils.NotificationsChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

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
    private val notificationsChannel: NotificationsChannel // Objek singleton channel notifikasi (Asumsi Hilt bisa provide ini)+

) {
companion object {
    private const val TAG = "NotificationsHelper"
}
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
    fun showStartNotification(address: String): Notification { // <--- Tambahkan ": Notification" di sini
        // Membuat Intent yang akan dikirim saat tombol Stop di notifikasi diklik.
        // Intent ini hanya perlu action string yang unik. Targetnya adalah BroadcastReceiver internal di helper ini.
        val stopIntent = Intent(NotificationsChannel.ACTION_STOP) // Menggunakan konstanta action dari NotificationsChannel. BAGUS!

        // Membuat PendingIntent dari Intent stopAction. PendingIntent ini yang dilekatkan ke tombol di notifikasi.
        // getBroadcast() digunakan karena action ini akan diterima oleh BroadcastReceiver internal.
        // Flags penting untuk keamanan dan update.
        val stopPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context, // Context (dari constructor helper)
            111, // Request code (bisa 0 atau angka unik jika perlu membedakan pending intent)
            stopIntent, // Intent yang akan dikirim
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_UPDATE_CURRENT (jika intent berubah) dan FLAG_IMMUTABLE (wajib >= S)
        )

        // === TAMBAHKAN KODE INI UNTUK contentIntent (klik body notifikasi) ===
        val activityIntent = Intent(context, MapActivity::class.java).apply { // Ganti MapActivity::class.java jika Activity lain
            // Optional: Tambahkan flags Intent jika diperlukan (misal: clear top, single top)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Optional: Tambahkan hook atau extra jika handleIntent butuh info
            putExtra("from_notification", true)
            // Optional: Tambahkan extra lain jika perlu spesifik tab/state
            // putExtra("target_screen", "map_default")
        }
        val activityPendingIntent = PendingIntent.getActivity(
            context,
            112, // Request code (harus unik dari PendingIntent lain jika perlu dibedakan)
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Menggunakan objek NotificationsChannel untuk benar-benar menampilkan notifikasi.
        // Asumsi NotificationsChannel.showNotification(context, builder lambda) yang sebenarnya membuat notifikasi.
        // === Tangkap hasil kembalian Notification dari panggilan ke notificationsChannel ===
        val notification = notificationsChannel.showNotification(context) { builder -> // <--- Tangkap hasilnya di sini
            // Menambahkan contentIntent ke builder notifikasi (klik body)
            builder.setContentIntent(activityPendingIntent) // <--- Tambahkan baris ini
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
        // ======================================

        // === Kembalikan objek Notification yang didapat ===
        return notification // <--- Tambahkan baris ini
        // =================================================
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
    // Di dalam kelas NotificationHelper

    fun registerReceiver(onStopAction: () -> Unit) {
        this.onStopActionCallback = onStopAction
        val filter = IntentFilter(NotificationsChannel.ACTION_STOP)

        // === MODIFIKASI BAGIAN INI ===
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Untuk Android 13 (API 33) dan di atasnya, harus eksplisit NOT_EXPORTED
            Context.RECEIVER_NOT_EXPORTED
        } else {
            // Untuk API di bawah 33 (API 26-32), flag ini belum ada.
            // Gunakan overload registerReceiver dengan parameter flags (tersedia sejak API 26)
            // dan berikan flags 0, yang secara implisit (untuk unprotected broadcast)
            // di API < 33 berarti receiver ini bisa di-export, tapi ini perilaku API lama.
            // Linter di versi baru akan puas karena Anda menggunakan overload dengan flags.
            0
        }

        // Gunakan metode registerReceiver dengan parameter flags (tersedia sejak API 26)
        // Ini akan digunakan baik di bawah API 33 maupun di atasnya
        context.registerReceiver(stopActionReceiver, filter, flags)
        // =============================

        Relog.i(TAG, "BroadcastReceiver for ACTION_STOP registered with flags: $flags") // Opsional untuk logging
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
