// File: [Proyek Aplikasi Utama Kamu]/src/main/java/com/roxgps/hook/HookConfiguration.kt
// (Sesuaikan dengan package hook kamu)

package com.roxgps.hook

import android.os.Parcelable // Import Parcelable
import kotlinx.parcelize.Parcelize // Anotasi Parcelize

/**
 * Data class Parcelable untuk menampung konfigurasi setting Xposed Hook.
 * Objek ini akan dikirim dari aplikasi utama ke Xposed Module melalui AIDL.
 * Harus mengimplementasikan Parcelable agar bisa dikirim melalui AIDL.
 */
@Parcelize // Anotasi Kotlin Parcelize untuk otomatis implementasi Parcelable
data class HookConfiguration(
    // === Tambahkan properti untuk SEMUA setting yang ingin kamu kirim ke hook ===
    val isStarted: Boolean = false, // Apakah faking diaktifkan?
    val isRandomPositionEnabled: Boolean = false, // Apakah random position aktif?
    val accuracy: Float = 1.0f, // Akurasi yang diinginkan
    val randomRange: Int = 50, // Range untuk random position (meter)
    // TODO: Tambahkan setting lain yang relevan di sini.
    //       Misal: updateInterval (jika hook menggunakan ini), provider default, dll.
    val updateIntervalMs: Long = 80, // Interval update di hook (ms)
    // val defaultProvider: String = "gps" // Contoh setting provider default

    // TODO: Jika ada daftar package yang diabaikan (ignorePkg), kirim juga di sini.
    //       val ignorePackageList: List<String> = emptyList()

) : Parcelable // Implementasikan Parcelable