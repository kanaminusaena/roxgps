package com.roxgps // Pastikan package ini sesuai dengan kelas Application lo

import androidx.appcompat.app.AppCompatDelegate // Untuk tema
import android.app.Application // Class Application
import dagger.hilt.android.HiltAndroidApp // Anotasi Hilt Application
// import com.roxgps.utils.PrefManager // Import PrefManager (sudah dipakai di bawah)
import kotlinx.coroutines.CoroutineScope // Untuk CoroutineScope
import kotlinx.coroutines.Dispatchers // Untuk Dispatchers
import timber.log.Timber // Untuk Timber logging
import com.roxgps.utils.FileLogger // Import FileLogger (sudah dipakai di bawah)
import com.roxgps.utils.PrefManager // Import PrefManager

// =====================================================================
// Global Instance Aplikasi (Dihapus - Gunakan Hilt Injection)
// =====================================================================

// lateinit var gsApp: App // <-- DIHAPUS!

// =====================================================================
// Application Class (@HiltAndroidApp)
// =====================================================================

// Anotasi Hilt untuk menandai ini adalah Application class entry point Hilt
@HiltAndroidApp
class App : Application() { // Nama class 'App' OK

    // =====================================================================
    // CoroutineScope Level Global (Bisa Disediakan Hilt)
    // =====================================================================

    // CoroutineScope ini bisa didefinisikan di sini atau disediakan via Hilt Module.
    // Jika disediakan Hilt, ini bisa dihapus dan di-inject ke kelas lain.
    val globalScope = CoroutineScope(Dispatchers.Default) // <-- Bisa disediakan oleh Hilt


    // =====================================================================
    // Companion Object untuk Inisialisasi Statis
    // =====================================================================

    companion object {
        // Metode inisialisasi umum (misal untuk library logging seperti Timber)
        fun commonInit() {
            // Hanya inisialisasi Timber di Debug build
            if (BuildConfig.DEBUG) { // Menggunakan BuildConfig
                Timber.plant(Timber.DebugTree()) // Inisialisasi Timber. BAGUS!
            }
        }
         // Metode ini bisa dipanggil dari Application.onCreate()
    }

    // =====================================================================
    // Metode Lifecycle Aplikasi
    // =====================================================================

    // Metode onCreate dipanggil saat proses aplikasi dimulai
    override fun onCreate() {
        super.onCreate()
        // Panggil metode inisialisasi statis (jika ada)
        commonInit() // Memanggil commonInit di companion object. BAGUS!

        // --- Inisialisasi FileLogger ---
        // Ini adalah tempat yang TEPAT untuk memanggil FileLogger.init().
        // Dipanggil paling awal di lifecycle aplikasi.
        // Melewati 'this' (instance Application) sebagai Context.
        FileLogger.init(this) // <-- INI SUDAH BETUL! Logger diinisialisasi di sini.
        // -----------------------------

        // Set tema default aplikasi berdasarkan setting di PrefManager
        // Membaca nilai dari PrefManager (object singleton)
        AppCompatDelegate.setDefaultNightMode(PrefManager.darkTheme) // Menggunakan PrefManager. BAGUS!

        // Metode inisialisasi lain di sini jika ada
    }

    // Metode lifecycle Application lainnya jika di-override
    // override fun onTerminate() { ... }
    // override fun onLowMemory() { ... }
    // override fun onConfigurationChanged(...) { ... }
}
