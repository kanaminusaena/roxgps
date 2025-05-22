// File: com/roxgps/repository/HookStatusRepository.kt
package com.roxgps.repository // Sesuaikan package lo

// =====================================================================
// Import Library untuk HookStatusRepository
// =====================================================================

import com.roxgps.utils.Relog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================
// Repository untuk Mengelola Status Hook dan Error dari Xposed Module
// Berfungsi sebagai Source of Truth untuk state ini di dalam aplikasi utama.
// Di-update oleh RoxGpsService, diobservasi oleh MainViewModel.
// =====================================================================

@Singleton // Repository ini harus Singleton agar state-nya konsisten di seluruh aplikasi
class HookStatusRepository @Inject constructor(
    // Dependencies jika dibutuhkan (misal CoroutineScope jika ada task internal)
    // @ScopeQualifiers private val applicationScope: CoroutineScope // Jika perlu scope aplikasi
) {
    companion object {
        private const val TAG = "HookStatusRepository"
    }

    // =====================================================================
    // State Hook dan Error (Diekspos sebagai StateFlow)
    // =====================================================================

    // StateFlow untuk status apakah Xposed module berhasil nge-hook
    private val _isModuleHooked = MutableStateFlow(false) // Nilai awal: false (belum nge-hook)
    val isModuleHooked: StateFlow<Boolean> = _isModuleHooked.asStateFlow() // Diekspos sebagai StateFlow (read-only)

    // StateFlow untuk pesan error terakhir dari Xposed module
    private val _lastHookError = MutableStateFlow<String?>(null) // Nilai awal: null (tidak ada error)
    val lastHookError: StateFlow<String?> = _lastHookError.asStateFlow() // Diekspos sebagai StateFlow (read-only)

    // StateFlow untuk status dasar: apakah hook terdeteksi oleh aplikasi (melalui setHookStatus)
    private val _isHookDetected = MutableStateFlow(false)
    val isHookDetected: StateFlow<Boolean> = _isHookDetected.asStateFlow()
    // =====================================================================
    // Metode untuk Mengupdate State (Dipanggil oleh RoxGpsService)
    // =====================================================================

    /**
     * Updates the hook status based on the value reported by the Xposed module.
     * Called by [RoxAidlService].
     *
     * @param hooked True if the module successfully hooked, false otherwise.
     */
    // Metode ini tidak perlu suspend karena StateFlow aman dipanggil dari thread manapun (termasuk Binder thread)
    fun updateHookStatus(hooked: Boolean) {
        // Mengupdate nilai StateFlow internal
        _isModuleHooked.value = hooked
        Relog.i("HookStatusRepository: isModuleHooked state updated to $hooked")
         // Jika status berubah jadi true (hooked), clear error
         if (hooked) {
             _lastHookError.value = null
             Relog.i("HookStatusRepository: lastHookError cleared because hooked is true")
         }
    }

    /**
     * Updates the last hook error message reported by the Xposed module.
     * Called by [RoxGpsService].
     *
     * @param message The error message.
     */
    // Metode ini tidak perlu suspend
    fun updateHookError(message: String?) {
        // Mengupdate nilai StateFlow internal
        _lastHookError.value = message
        Relog.i("HookStatusRepository: lastHookError state updated to '$message'")
         // Jika ada error, status hooked mungkin false (meskipun setHookStatus juga seharusnya dipanggil dengan false)
         if (message != null) {
              _isModuleHooked.value = false // Pastikan status hooked false jika ada error
         }
    }

    /**
     * Mengupdate status dasar deteksi Xposed Hook.
     * Dipanggil oleh [RoxAidlService] ketika menerima panggilan AIDL setHookStatus().
     *
     * @param hooked True jika hook melaporkan dirinya aktif/terdeteksi, false sebaliknya.
     */
    fun updateSystemHookedStatus(hooked: Boolean) { // <<< METODE YANG DIBUTUHKAN
        Timber.d("$TAG: Updating system hooked status to: $hooked")
        Relog.d(TAG, "Updating system hooked status: $hooked")
        _isHookDetected.update { hooked } // Update StateFlow
    }

    /**
     * Menyimpan pesan error terakhir yang dilaporkan oleh Xposed Hook.
     * Dipanggil oleh [RoxAidlService] ketika menerima panggilan AIDL reportHookError().
     *
     * @param message Pesan error dari hook, atau null untuk menghapus error.
     */
    fun setLastHookError(message: String?) { // <<< METODE LAIN YANG DIBUTUHKAN
        Timber.d("$TAG: Setting last hook error: $message")
        Relog.d(TAG, "Setting last hook error: $message")
        _lastHookError.update { message } // Update StateFlow
    }

    // =====================================================================
    // Initialization (Opsional)
    // =====================================================================
    init {
        Relog.i("HookStatusRepository created")
    }

    // =====================================================================
    // Metode Getters (Opsional, jika tidak ingin expose StateFlow langsung)
    // ViewModel bisa panggil method ini atau mengamati StateFlow di atas.
    // Mengamati StateFlow lebih reaktif.
    // =====================================================================
    fun getCurrentHookStatus(): Boolean {
        return _isModuleHooked.value // Mengembalikan nilai StateFlow saat ini
    }

    fun getCurrentHookError(): String? {
        return _lastHookError.value // Mengembalikan nilai StateFlow saat ini
    }
}
