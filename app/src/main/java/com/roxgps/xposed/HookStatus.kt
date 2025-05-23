package com.roxgps.xposed

/**
 * Sealed class merepresentasikan status Xposed Hook dari sudut pandang aplikasi.
 * Digunakan oleh [IXposedHookManager] untuk melaporkan status ke ViewModel/UI.
 *
 * @author loserkidz
 * @since 2025-05-23 09:12:48
 */
sealed class HookStatus {
    /**
     * Hook belum aktif atau belum terdeteksi/terinisialisasi.
     */
    object NotActive : HookStatus() {
        override fun toString() = "NotActive"
    }

    /**
     * Aplikasi sedang mencoba mendeteksi/memulai hook atau service AIDL.
     */
    object Initializing : HookStatus() {
        override fun toString() = "Initializing"
    }

    /**
     * Xposed Hook terdeteksi di proses target, tapi binding AIDL belum berhasil.
     */
    object DetectedButNotBound : HookStatus() {
        override fun toString() = "DetectedButNotBound"
    }

    /**
     * Xposed Module sedang mencoba melakukan binding ke RoxAidlService di proses aplikasi.
     */
    object Binding : HookStatus() {
        override fun toString() = "Binding"
    }

    /**
     * Binding AIDL berhasil, RoxAidlService terhubung dan siap menerima panggilan dari hook.
     * Hook di proses target sekarang bisa memanggil getLatestFakeLocation().
     */
    data object BoundAndReady : HookStatus() {
        override fun toString() = "BoundAndReady"
    }

    /**
     * Hook terdeteksi, AIDL Bound, dan RoxAidlService melaporkan bahwa hook
     * sedang aktif menerima hook faking (FakeLocationData.isStarted == true).
     */
    data object ActiveFaking : HookStatus() {
        override fun toString() = "ActiveFaking"
    }

    /**
     * Terjadi error pada proses hook (misal, binding gagal, error saat inject di hook).
     * @param message Optional error message yang menjelaskan masalah
     */
    data class Error(val message: String?) : HookStatus() {
        override fun toString() = "Error(${message ?: "Unknown error"})"
    }

    /**
     * Helper function untuk mengecek apakah status menunjukkan hook siap digunakan
     */
    fun isReady(): Boolean = this is BoundAndReady || this is ActiveFaking

    /**
     * Helper function untuk mengecek apakah status menunjukkan error
     */
    fun isError(): Boolean = this is Error

    /**
     * Helper function untuk mengecek apakah status menunjukkan proses inisialisasi
     */
    fun isInitializing(): Boolean = this is Initializing || this is DetectedButNotBound || this is Binding

    companion object {
        /**
         * Convert string ke HookStatus
         * @param status String representasi dari status
         * @param errorMessage Optional error message jika status adalah "Error"
         * @return HookStatus yang sesuai
         */
        fun fromString(status: String, errorMessage: String? = null): HookStatus {
            return when (status.lowercase()) {
                "notactive" -> NotActive
                "initializing" -> Initializing
                "detectedbutnotbound" -> DetectedButNotBound
                "binding" -> Binding
                "boundandready" -> BoundAndReady
                "activefaking" -> ActiveFaking
                "error" -> Error(errorMessage)
                else -> Error("Invalid status: $status")
            }
        }
    }
}