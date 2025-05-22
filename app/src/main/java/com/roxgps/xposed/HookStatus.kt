package com.roxgps.xposed // Atau package hook kamu

// =====================================================================
// Sealed class HookStatus (Status Xposed Hook)
// =====================================================================

/**
 * Sealed class merepresentasikan status Xposed Hook dari sudut pandang aplikasi.
 * Digunakan oleh [IXposedHookManager] untuk melaporkan status ke ViewModel/UI.
 */
sealed class HookStatus {
    // Hook belum aktif atau belum terdeteksi/terinisialisasi.
    object NotActive : HookStatus()

    // Aplikasi sedang mencoba mendeteksi/memulai hook atau service AIDL.
    object Initializing : HookStatus()

    // Xposed Hook terdeteksi di proses target, tapi binding AIDL belum berhasil.
    object DetectedButNotBound : HookStatus()

    // Xposed Module sedang mencoba melakukan binding ke RoxAidlService di proses aplikasi.
    object Binding : HookStatus()

    // Binding AIDL berhasil, RoxAidlService terhubung dan siap menerima panggilan dari hook.
    // Hook di proses target sekarang bisa memanggil getLatestFakeLocation().
    data object BoundAndReady : HookStatus() // Menggunakan hook object karena tidak butuh hook spesifik

    // Hook terdeteksi, AIDL Bound, dan RoxAidlService melaporkan bahwa hook
    // sedang aktif menerima hook faking (FakeLocationData.isStarted == true).
    data object ActiveFaking : HookStatus() // Menggunakan hook object karena tidak butuh hook spesifik

    // Terjadi error pada proses hook (misal, binding gagal, error saat inject di hook).
    // Bisa membawa pesan error.
    data class Error(val message: String?) : HookStatus()

    // TODO: Tambahkan state lain jika diperlukan (misal, TargetAppNotFound, HookDisabledBySetting, dll.)
}