package com.roxgps.service // Sesuaikan package

import com.roxgps.ipc.IRoxGpsService // Import interface AIDL
import java.util.concurrent.atomic.AtomicReference // Untuk thread-safe

// =====================================================================
// Object untuk Menampung Referensi ke AIDL Service Instance
// Digunakan oleh komponen di aplikasi utama untuk mengakses Service AIDL
// (yang berjalan di proses yang sama) secara thread-safe.
// =====================================================================

/**
 * A thread-safe holder for the [IRoxGpsService] binder instance.
 * The [RoxGpsAidlService] sets and clears this reference during its lifecycle.
 * Other components in the same process can access the running service instance via this holder.
 */
object AidlServiceHolder {

    // Menggunakan AtomicReference untuk thread-safe access
    // Instance Service AIDL yang sedang berjalan, yang mengimplementasikan IRoxGpsService.
    private val serviceInstance = AtomicReference<IRoxGpsService?>(null)

    /**
     * Sets the currently running [IRoxGpsService] instance.
     * Should be called by the Service's onCreate method.
     */
    fun setService(service: IRoxGpsService?) {
        serviceInstance.set(service)
        // Opsi: Log status set/clear service
        // Timber.d("AidlServiceHolder: Service instance set: ${service != null}")
    }

    /**
     * Gets the currently running [IRoxGpsService] instance.
     * Returns null if the service is not running or not yet bound/created.
     */
    fun getService(): IRoxGpsService? {
        return serviceInstance.get()
    }
}
