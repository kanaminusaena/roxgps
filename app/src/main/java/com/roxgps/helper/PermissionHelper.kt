package com.roxgps.helper // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk PermissionHelper
// =====================================================================

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import javax.inject.Inject

// import com.roxgps.ui.BaseMapActivity // Tidak perlu import BaseMapActivity spesifik jika pakai ComponentActivity

// =====================================================================
// Callback Interface untuk PermissionHelper
// =====================================================================

// Interface buat komunikasi balik ke Activity yang menggunakan PermissionHelper
interface PermissionResultListener {
    // Dipanggil saat hasil permintaan izin tunggal diterima
    fun onPermissionResult(permission: String, isGranted: Boolean)
    // Dipanggil saat hasil permintaan beberapa izin diterima
    fun onPermissionsResult(permissions: Map<String, Boolean>)
     // Opsi: Tambahin callback untuk show rationale jika diperlukan
     // fun showPermissionRationale(permission: String)
}

// =====================================================================
// Class PermissionHelper
// =====================================================================

/**
 * Helper class untuk mengelola perizinan (permissions) yang dibutuhkan oleh aplikasi.
 * Menggunakan Activity Result APIs untuk permintaan izin yang modern.
 *
 * @param activity Instance dari [ComponentActivity] yang digunakan untuk memeriksa dan mendaftarkan Activity Result Launcher.
 */
class PermissionHelper @Inject constructor( // <-- Tambahkan @Inject constructor() di sini
    private val activity: ComponentActivity
) { // Pakai ComponentActivity biar lebih fleksibel

    // Listener untuk mengirim hasil permission kembali.
    // Helper perlu tahu listener mana yang aktif saat request terakhir.
    private var activePermissionResultListener: PermissionResultListener? = null

    // Activity Result Launcher untuk menangani permintaan izin tunggal
    private val requestSinglePermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Mendapatkan listener yang aktif saat request terakhir
            val lastUsedListener = this.activePermissionResultListener
            // Kirim hasil ke listener
            // Kita perlu tahu permission String mana yang diminta.
            // Ini bisa disimpan di properti terpisah atau method onPermissionResult perlu parameter permission String.
            // Mari kita ubah callback onPermissionResult di listener agar punya parameter permission String.
            // (Interface PermissionResultListener di atas sudah diperbarui)

            // Untuk request SinglePermission, kita perlu menyimpan permission yang diminta
            val requestedPermission = this.lastRequestedPermission // <- Membutuhkan properti untuk menyimpan permission terakhir
            if (requestedPermission != null) {
                lastUsedListener?.onPermissionResult(requestedPermission, isGranted)
            } else {
                 // Kasus error: callback dipanggil tapi tidak tahu permission apa yang diminta terakhir
                 // Ini seharusnya tidak terjadi jika alurnya benar.
            }
             this.activePermissionResultListener = null // Reset listener setelah digunakan
             this.lastRequestedPermission = null // Reset permission terakhir
        }

    // Properti untuk menyimpan permission String yang terakhir kali diminta menggunakan requestSinglePermissionLauncher
    private var lastRequestedPermission: String? = null


    // Activity Result Launcher untuk menangani permintaan beberapa izin
    private val requestMultiplePermissionsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            // Mendapatkan listener yang aktif saat request terakhir
            val lastUsedListener = this.activePermissionResultListener
            // Kirim hasil ke listener
            lastUsedListener?.onPermissionsResult(permissions) // Mengirim map hasil permission

            this.activePermissionResultListener = null // Reset listener setelah digunakan
            // Untuk requestMultiplePermissions, tidak ada properti "permission terakhir" tunggal yang perlu direset.
        }


    // =====================================================================
    // Method Publik untuk Cek Status Permissions
    // =====================================================================

    /**
     * Memeriksa apakah izin tunggal tertentu telah diberikan.
     *
     * @param permission String izin yang ingin diperiksa (contoh: Manifest.permission.CAMERA).
     * @return True jika izin diberikan, False jika tidak.
     */
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Memeriksa apakah semua izin dalam daftar telah diberikan.
     *
     * @param permissions Array of String izin yang ingin diperiksa.
     * @return True jika semua izin diberikan, False jika ada yang belum.
     */
    fun checkPermissions(permissions: Array<String>): Boolean {
        return permissions.all { checkPermission(it) }
    }

    /**
     * Memeriksa apakah izin lokasi (ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION) sudah diberikan.
     *
     * @return True jika kedua izin lokasi diberikan, False jika tidak.
     */
    fun checkLocationPermissions(): Boolean {
        return checkPermissions(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

    /**
     * Memeriksa apakah izin notifikasi (POST_NOTIFICATIONS untuk Android 13+) sudah diberikan
     * atau apakah notifikasi diaktifkan di SettingsCompose (untuk versi < 13).
     *
     * @return True jika notifikasi diizinkan/aktif, False jika tidak.
     */
    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Untuk Android 13+, cek permission POST_NOTIFICATIONS
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Untuk versi di bawah 13, cek apakah notifikasi diaktifkan di SettingsCompose
            NotificationManagerCompat.from(activity).areNotificationsEnabled()
        }
    }


    // =====================================================================
    // Method Publik untuk Meminta Permissions
    // =====================================================================

    /**
     * Meminta izin tunggal ke pengguna.
     * Hasil dilaporkan melalui listener yang diberikan.
     *
     * @param permission String izin yang ingin diminta.
     * @param listener Listener untuk menerima hasil (granted/denied).
     */
    fun requestPermission(permission: String, listener: PermissionResultListener) {
        this.activePermissionResultListener = listener // Simpan listener aktif
        this.lastRequestedPermission = permission // Simpan permission yang diminta
        requestSinglePermissionLauncher.launch(permission) // Luncurkan permintaan
    }

    /**
     * Meminta beberapa izin ke pengguna.
     * Hasil dilaporkan melalui listener yang diberikan.
     *
     * @param permissions Array of String izin yang ingin diminta.
     * @param listener Listener untuk menerima hasil (granted/denied per izin).
     */
    fun requestPermissions(permissions: Array<String>, listener: PermissionResultListener) {
        this.activePermissionResultListener = listener // Simpan listener aktif
        // Tidak perlu menyimpan daftar permission yang diminta di properti tunggal
        requestMultiplePermissionsLauncher.launch(permissions) // Luncurkan permintaan
    }

    /**
     * Meminta izin lokasi (ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION) ke pengguna.
     * Hasil dilaporkan melalui listener yang diberikan.
     *
     * @param listener Listener untuk menerima hasil (granted/denied per izin).
     */
    fun requestLocationPermissions(listener: PermissionResultListener) {
        requestPermissions(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ), listener) // Panggil method requestPermissions umum
    }

    /**
     * Meminta izin notifikasi (POST_NOTIFICATIONS untuk Android 13+) ke pengguna.
     * Untuk versi < 13, buka SettingsCompose notifikasi aplikasi.
     * Hasil untuk versi >= 13 dilaporkan melalui listener yang diberikan.
     *
     * @param listener Listener untuk menerima hasil (granted/denied) untuk versi >= 13.
     * Untuk versi < 13, callback listener TIDAK dipanggil,
     * karena user diarahkan ke SettingsCompose.
     */
    fun requestNotificationPermission(listener: PermissionResultListener? = null) { // listener bisa null untuk versi < 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Untuk Android 13+, minta permission POST_NOTIFICATIONS pakai launcher
            if (listener != null) { // Pastikan listener tidak null jika mau pakai callback
                 requestPermission(Manifest.permission.POST_NOTIFICATIONS, listener) // Panggil method requestPermission tunggal
            } else {
                 // Jika listener null tapi dipanggil di Android 13+, request akan jalan tanpa callback
                requestSinglePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Untuk versi di bawah 13, langsung arahkan ke SettingsCompose notifikasi aplikasi
            openAppNotificationSettings()
            // Di sini listener.onPermissionResult atau onPermissionsResult TIDAK dipanggil
        }
    }


    // =====================================================================
    // Method Helper Internal / Utility (dipanggil dari method publik)
    // =====================================================================

    // Membuka pengaturan izin aplikasi spesifik di perangkat.
    // Dipanggil dari requestNotificationPermission untuk versi < 13
    private fun openAppNotificationSettings() {
         val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
         intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
         activity.startActivity(intent)
    }

    // Opsi: Method untuk menampilkan dialog rasionalisasi
    // fun shouldShowRequestPermissionRationale(permission: String): Boolean {
    //     return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    // }
    // Logic ini bisa dipakai sebelum requestPermission/requestPermissions untuk menentukan
    // apakah perlu menampilkan dialog penjelasan custom.
}
