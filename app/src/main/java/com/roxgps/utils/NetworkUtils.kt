package com.roxgps.utils // Sesuaikan dengan package utility umum di project kamu

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

// Objek utility untuk fungsi-fungsi terkait jaringan/koneksi
// Menggunakan 'object' agar fungsinya bisa dipanggil langsung tanpa membuat instance kelas
object NetworkUtils {

    /**
     * Memeriksa apakah perangkat saat ini terhubung ke jaringan internet.
     *
     * @param context Context dari aplikasi atau Activity.
     * @return true jika terhubung ke Wi-Fi, Seluler, atau Ethernet, false jika tidak.
     */
    fun isNetworkConnected(context: Context): Boolean {
        // Mendapatkan ConnectivityManager dari sistem service
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Untuk Android Marshmallow (API 23) dan yang lebih baru
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Mendapatkan jaringan aktif saat ini
            val network = connectivityManager.activeNetwork ?: return false // Jika tidak ada jaringan aktif, kembalikan false
            // Mendapatkan kemampuan (capabilities) dari jaringan aktif
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Jika tidak ada capabilities, kembalikan false

            // Memeriksa apakah jaringan aktif memiliki salah satu transport (Wi-Fi, Seluler, Ethernet)
            return
