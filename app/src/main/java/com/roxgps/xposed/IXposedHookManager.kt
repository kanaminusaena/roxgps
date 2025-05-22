package com.roxgps.xposed

import kotlinx.coroutines.flow.StateFlow

// Import jika ada callback atau status yang perlu dikirim Manager Hook (opsional)
// import com.roxgps.repository.HookStatusRepository // Contoh jika Manager Hook update status hook
// TODO: Import Sealed Class HookStatus

// TODO: Import Flow jika Manager mengekspos status sebagai Flow (Sudah kita putuskan ya!)

/**
 * Interface (Kontrak) untuk Manager Xposed Hook di aplikasi utama.
 * Didefinisikan di aplikasi utama agar ILocationHelper bisa memanggilnya.
 * Implementasinya akan mengelola proses binding AIDL di sisi aplikasi
 * dan interaksi lain yang diperlukan dengan Xposed Module.
 */
interface IXposedHookManager {

    /**
     * Memberi tahu Manager Hook bahwa aplikasi utama ingin memulai faking.
     * Manager ini bertanggung jawab memastikan binding AIDL aktif (jika belum)
     * dan memberi sinyal ke Xposed Module (misal, via AIDL atau cara lain)
     * bahwa faking hook siap diambil.
     */
    fun enableFakingMechanism(enable: Boolean)

    // TODO: Tambahkan method lain jika ILocationHelper atau bagian lain app utama perlu berinteraksi dengan Xposed Module melalui Manager ini.
    // Contoh: Method untuk mengecek status binding AIDL, melaporkan status Manager itu sendiri, dll.
    /**
     * Metode yang dipanggil oleh RoxAidlService untuk melaporkan status aktual
     * dari proses binding AIDL dan interaksi dengan Xposed Hook.
     *
     * @param status Status Hook yang dilaporkan oleh Service.
     */
    fun reportHookStatus(status: HookStatus) // <<< Tambahkan metode untuk melaporkan status dari Service

    // === Metode untuk Mengontrol Hook Lifecycle (Dipanggil dari Aplikasi Utama) ===
    // Metode ini mungkin dipanggil dari Service atau ViewModel.
    /** Memulai proses binding ke Xposed Hook Service. */
    fun startHookServiceBinding()

    /** Menghentikan proses binding ke Xposed Hook Service. */
    fun stopHookServiceBinding()


    // === Metode untuk Melaporkan Status dari Hook (Dipanggil oleh RoxAidlService) ===
    // Metode ini dipanggil oleh Service AIDL (RoxAidlService) untuk melaporkan status yang datang dari Xposed Module.

    /** Melaporkan status koneksi hook (terhubung/tidak). Dipanggil oleh RoxAidlService saat setHookStatus dari hook. */
    fun setHookConnected(connected: Boolean) // <<< TAMBAHKAN METODE INI

    /** Melaporkan pesan error dari Xposed Hook Module. Dipanggil oleh RoxAidlService saat reportHookError dari hook. */
    fun reportHookErrorFromHook(message: String) // <<< TAMBAHKAN METODE INI

    /** Melaporkan status cek sistem hook (misal, hook aktif/tidak, compatibility). Dipanggil oleh RoxAidlService saat notifySystemCheck dari hook. */
    fun notifySystemCheckCompleted() // <<< TAMBAHKAN METODE INI

    /**
     * Mengekspos status koneksi dan faking dari Xposed Hook.
     * Komponen lain (ViewModel, UI) bisa mengamati Flow ini untuk memperbarui tampilan status.
     */
    val hookStatus: StateFlow<HookStatus> // <<< TAMBAHKAN PROPERTI INI (Sebagai StateFlow<HookStatus>)

    // TODO: Tambahkan metode lain di sini jika ada komunikasi lain dari hook ke Manager


    // === Properti untuk Mengekspos Status Hook ke UI (Dipanggil dari ViewModel/UI) ===
    // ViewModel atau komponen UI akan mengamati properti ini untuk memperbarui tampilan status hook.
    // Implementasi di IXposedHookManagerImpl akan menggunakan StateFlow atau SharedFlow.

    // Contoh: Mengekspos status koneksi (Binding, Bound, Disconnected, Error)
    // val hookConnectionStatus: StateFlow<HookStatus> // Contoh properti status

    // Contoh: Mengekspos pesan error terbaru dari hook
    // val latestHookError: SharedFlow<String> // Contoh properti error
}