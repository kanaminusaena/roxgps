package com.roxgps.helper // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk ILocationHelper Interface
// =====================================================================

import android.content.Intent // Jika method membuka settings
import android.location.Location // Jika method mengembalikan Location
import android.content.Context // Jika method butuh Context
import android.net.Uri // Jika method membuka settings aplikasi

// Import LocationListener interface yang sudah ada
// import com.roxgps.helper.LocationListener // Asumsi package LocationListener sama dengan helper


// =====================================================================
// ILocationHelper Interface
// =====================================================================

// Interface ini mendefinisikan kontrak untuk Location Helper.
// Class apapun yang mengimplementasikan interface ini HARUS menyediakan
// implementasi untuk semua method yang dideklarasikan di sini.
// Activity atau class lain yang membutuhkan Location Helper akan
// tergantung pada interface ini, BUKAN implementasi spesifik (Google/MapLibre).
interface ILocationHelper {

    // Memulai proses request lokasi terakhir yang diketahui atau update lokasi baru.
    // Hasil atau error dikirim melalui LocationListener.
    // Parameter listener diperlukan agar implementasi helper tahu ke mana mengirim callback.
    fun requestLocationUpdates(listener: LocationListener)

    // Menghentikan semua update lokasi yang sedang berjalan.
    // Penting untuk dipanggil saat tidak lagi membutuhkan lokasi (misal di onDestroy Activity).
    fun stopLocationUpdates()

    // Memeriksa apakah layanan lokasi (GPS/Network) aktif di perangkat.
    fun isLocationServiceEnabled(): Boolean

    // Memeriksa apakah izin lokasi (ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION) sudah diberikan.
    // Method ini tidak meminta izin, hanya mengecek status.
    fun checkLocationPermissions(): Boolean

    // Meminta izin lokasi ke pengguna.
    // Hasil (granted/denied) dilaporkan melalui LocationListener.
    // Implementasi akan menggunakan Activity Result API atau ActivityCompat.requestPermissions.
    // Mungkin butuh Context/Activity sebagai parameter, atau Helper diinisialisasi dengan Context/Activity.
    // Kita asumsikan Helper diinisialisasi dengan Activity/Context di constructor.
    fun requestLocationPermissions()

    // Membuka pengaturan lokasi di perangkat.
    // Mungkin perlu Context atau Activity untuk startActivity. Asumsi Helper diinisialisasi dengan Context/Activity.
    fun openLocationSettings()

    // Membuka pengaturan izin aplikasi spesifik di perangkat.
    // Mungkin perlu Context atau Activity untuk startActivity. Asumsi Helper diinisialisasi dengan Context/Activity.
    fun openAppPermissionSettings()

    // Opsi: Tambahin method lain jika diperlukan di Activity
    // fun getLastKnownLocation(): Location? // Jika perlu akses lokasi terakhir tanpa request baru
}
