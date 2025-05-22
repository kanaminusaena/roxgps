package com.roxgps.helper // Pastikan package ini sesuai dengan lokasi file

import android.location.Location // Import Location standar Android jika metode callback menggunakannya

/**
 * Antarmuka callback untuk menerima hasil operasi lokasi dari ILocationHelper.
 * Kelas yang mengimplementasikan antarmuka ini akan menerima notifikasi
 * ketika lokasi tersedia, terjadi error, atau hasil permission lokasi diterima.
 *
 * Kelas seperti MapActivity akan mengimplementasikan antarmuka ini untuk
 * bereaksi terhadap update lokasi.
 */
interface LocationListener {

    /**
     * Dipanggil ketika hasil lokasi baru tersedia dari provider lokasi.
     * Implementasi di Activity/Fragment akan menggunakan hook lokasi ini
     * untuk mengupdate UI (misal, memindahkan marker di peta).
     *
     * @param location Objek Location standar Android yang berisi hook lokasi (latitude, longitude, akurasi, dll.).
     */
    fun onLocationResult(location: Location)

    /**
     * Dipanggil ketika terjadi error saat mencoba mendapatkan lokasi,
     * atau ketika provider lokasi tidak tersedia.
     * Implementasi di Activity/Fragment akan menangani error ini (misal, menampilkan pesan ke pengguna).
     *
     * @param errorMessage Pesan string yang menjelaskan error yang terjadi.
     */
    fun onLocationError(errorMessage: String)

    // Catatan: Metode onPermissionGranted dan onPermissionDenied
    // Berdasarkan refactoring, logic permission sekarang lebih banyak ditangani oleh PermissionHelper
    // dan callback-nya di PermissionResultListener.
    // Jika LocationListener ini hanya fokus pada hasil LOKASI (bukan hasil permintaan permission),
    // maka metode permission di bawah ini bisa dihapus.
    // Namun, jika kamu masih membutuhkan callback spesifik di sini terkait permission lokasi
    // yang dipicu oleh LocationHelper, uncomment dan sesuaikan:
/*
    fun onPermissionGranted() // Dipanggil jika izin lokasi diberikan setelah diminta oleh LocationHelper
    fun onPermissionDenied() // Dipanggil jika izin lokasi ditolak setelah diminta oleh LocationHelper
    */
    // TODO: Tambahkan metode callback lain jika dibutuhkan oleh ILocationHelper
    // Misalnya, callback saat status provider lokasi berubah (misal GPS diaktifkan/dinonaktifkan)
    /*fun startRealLocation(provider: String)
    fun stopFakeLocation(provider: String)*/

    // Catatan: Metode permission dipindahkan ke PermissionResultListener yang di-handle PermissionHelper.
    // Metode startRealLocation/stopFakeLocation dipindahkan ke ILocationHelper.

    // TODO: Tambahkan metode callback lain jika ILocationHelper memiliki status spesifik lain
    // yang perlu dilaporkan ke listener.
}
