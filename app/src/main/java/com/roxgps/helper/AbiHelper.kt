package com.roxgps.helper

import android.os.Build
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class untuk memeriksa dan mengelola informasi ABI (Application Binary Interface) perangkat.
 * Di-inject menggunakan Hilt sebagai Singleton.
 * MinSdk adalah 30, jadi Build.SUPPORTED_ABIS selalu tersedia.
 */
@Singleton
class AbiHelper @Inject constructor() {

    // ABI yang didukung oleh aplikasi Anda.
    // Anda HARUS mengganti ini dengan ABI yang *sebenarnya* didukung oleh APK Anda
    // Berdasarkan 'abiFilters' di build.gradle (module) atau struktur folder 'lib' di APK Anda.
    // Contoh umum:
    private val SUPPORTED_APP_ABIS = arrayOf(
        "arm64-v8a",   // Umum untuk perangkat 64-bit ARM
        "armeabi-v7a", // Umum untuk perangkat 32-bit ARM (sering dibutuhkan untuk backward compatibility)
        "x86_64",      // Untuk emulator atau perangkat Intel 64-bit
        "x86"          // Untuk emulator atau perangkat Intel 32-bit
    )

    /**
     * Memeriksa apakah perangkat mendukung setidaknya satu ABI yang didukung oleh aplikasi ini.
     * Karena minSdk adalah 30, Build.SUPPORTED_ABIS dijamin tersedia.
     * @return true jika perangkat mendukung setidaknya satu ABI yang dibutuhkan aplikasi, false jika tidak.
     */
    fun isDeviceAbiSupported(): Boolean {
        val deviceSupportedAbis = Build.SUPPORTED_ABIS // Ini selalu tersedia di minSdk 30
        Timber.d("Device Supported ABIs: ${deviceSupportedAbis.joinToString()}")

        for (deviceAbi in deviceSupportedAbis) {
            if (SUPPORTED_APP_ABIS.contains(deviceAbi)) {
                Timber.d("Device ABI '$deviceAbi' is supported by the app.")
                return true
            }
        }
        Timber.w("None of the device ABIs are supported by the app. Device ABIs: ${deviceSupportedAbis.joinToString()}, App Supported ABIs: ${SUPPORTED_APP_ABIS.joinToString()}")
        return false
    }

    /**
     * Mendapatkan daftar ABI yang didukung oleh perangkat ini.
     * @return Array of Strings berisi ABI yang didukung perangkat. Selalu tersedia di minSdk 30.
     */
    fun getSupportedDeviceAbis(): Array<String> {
        return Build.SUPPORTED_ABIS
    }

    /**
     * Mendapatkan ABI yang paling disukai oleh perangkat (yang pertama dalam daftar).
     * @return String ABI yang paling disukai, atau null jika daftar ABI kosong (sangat jarang terjadi).
     */
    fun getPrimaryDeviceAbi(): String? {
        return Build.SUPPORTED_ABIS.firstOrNull()
    }
}