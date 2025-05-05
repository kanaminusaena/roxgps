// File: com/roxgps/repository/HookStatusRepository.kt
package com.roxgps.repository // Sesuaikan package lo

// =====================================================================
// Import Library untuk HookStatusRepository
// =====================================================================

import kotlinx.coroutines.flow.MutableStateFlow // Untuk StateFlow yang bisa diupdate
import kotlinx.coroutines.flow.StateFlow // Untuk mengekspos StateFlow
import kotlinx.coroutines.flow.asStateFlow // Untuk mengonversi MutableStateFlow ke StateFlow
import javax.inject.Inject // Untuk Inject
import javax.inject.Singleton // Untuk menandai sebagai Singleton

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

    // =====================================================================
    // State Hook dan Error (Diekspos sebagai StateFlow)
    // =====================================================================

    // StateFlow untuk status apakah Xposed module berhasil nge-hook
    private val _isModuleHooked = MutableStateFlow(false) // Nilai awal: false (belum nge-hook)
    val isModuleHooked: StateFlow<Boolean> = _isModuleHooked.asStateFlow() // Diekspos sebagai StateFlow (read-only)

    // StateFlow untuk pesan error terakhir dari Xposed module
    private val _lastHookError = MutableStateFlow<String?>(null) // Nilai awal: null (tidak ada error)
    val lastHookError: StateFlow<String?> = _lastHookError.asStateFlow() // Diekspos sebagai StateFlow (read-only)


    // =====================================================================
    // Metode untuk Mengupdate State (Dipanggil oleh RoxGpsService)
    // =====================================================================

    /**
     * Updates the hook status based on the value reported by the Xposed module.
     * Called by [RoxGpsService].
     *
     * @param hooked True if the module successfully hooked, false otherwise.
     */
    // Metode ini tidak perlu suspend karena StateFlow aman dipanggil dari thread manapun (termasuk Binder thread)
    fun updateHookStatus(hooked: Boolean) {
        // Mengupdate nilai StateFlow internal
        _isModuleHooked.value = hooked
        Timber.d("HookStatusRepository: isModuleHooked state updated to $hooked")
         // Jika status berubah jadi true (hooked), clear error
         if (hooked) {
             _lastHookError.value = null
             Timber.d("HookStatusRepository: lastHookError cleared because hooked is true")
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
        Timber.d("HookStatusRepository: lastHookError state updated to '$message'")
         // Jika ada error, status hooked mungkin false (meskipun setHookStatus juga seharusnya dipanggil dengan false)
         if (message != null) {
              _isModuleHooked.value = false // Pastikan status hooked false jika ada error
         }
    }

    // =====================================================================
    // Initialization (Opsional)
    // =====================================================================
    init {
        Timber.d("HookStatusRepository created")
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
