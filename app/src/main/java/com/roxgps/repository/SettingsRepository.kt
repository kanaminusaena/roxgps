// File: com/roxgps/repository/SettingsRepository.kt
package com.roxgps.repository

// === IMPORT YANG DIPERLUKAN ===
import com.roxgps.datastore.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// TODO: Hapus import yang tidak digunakan seperti StateFlow, DataStore, dll.

// =====================================================================
// Interface untuk Repository yang mengelola setting konfigurasi aplikasi.
// Ini adalah sumber kebenaran untuk setting seperti akurasi, random position, dll.
// Metode GET bersifat ASINKRONUS (mengembalikan Flow) agar cocok dengan DataStore.
// =====================================================================
interface SettingsRepository {

    // === Properti Flow untuk Mendapatkan Nilai Setting Konfigurasi (ASINKRONUS) ===
    // Anggota ini mengembalikan Flow yang memancarkan nilai setting terbaru.
    // IMPLEMENTASINYA ADA DI SettingsRepositoryImpl MENGGUNAKAN dataStore.hook.map {}

    // Mengembalikan Flow yang memancarkan objek AppSettings lengkap
    // Opsional, kamu bisa punya ini atau hanya properti Flow individual
    val appSettingsFlow: Flow<AppSettings>

    // Properti Flow untuk setting individual
    val isRandomPositionEnabled: Flow<Boolean> // Apakah random position aktif?
    val accuracyLevel: Flow<Float> // Level akurasi
    val randomRange: Flow<Int> // Range untuk random position
    val updateIntervalMs: Flow<Long> // Interval update di hook (ms)
    // TODO: Tambahkan properti Flow lain sesuai setting di file .proto kamu
    val desiredSpeed: Flow<Float> // Contoh properti lain jika ada di .proto


    // === Metode Suspend untuk Mengupdate/Mengubah Nilai Setting ===
    // Metode ini bersifat suspend karena menulis ke DataStore adalah operasi yang memblokir.

    // Metode utama untuk mengupdate seluruh objek setting secara atomic
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings): AppSettings

    // Metode untuk mengupdate setting individual
    suspend fun updateIsRandomPositionEnabled(isEnabled: Boolean)
    suspend fun updateAccuracyLevel(accuracy: Float)
    suspend fun updateRandomRange(range: Int)
    suspend fun updateUpdateIntervalMs(interval: Long)
    // TODO: Tambahkan metode update lain sesuai setting di file .proto kamu
    suspend fun updateDesiredSpeed(speed: Float) // Contoh metode update lain

    val isRandomPositionEnabledState: StateFlow<Boolean> // <<< TAMBAHKAN INI
    val accuracyLevelState: StateFlow<Float>             // <<< TAMBAHKAN INI
    val randomRangeState: StateFlow<Int>                 // <<< TAMBAHKAN INI
    val updateIntervalMsState: StateFlow<Long>           // <<< TAMBAHKAN INI
    val desiredSpeedState: StateFlow<Float>

    // TODO: Jika ada metode lain di interface (misal delete all), tambahkan di sini.
}

// TODO: Implementasi konkret dari SettingsRepository ada di SettingsRepositoryImpl.kt
//       Pastikan SettingsRepositoryImpl mengimplementasikan SEMUA anggota interface ini.