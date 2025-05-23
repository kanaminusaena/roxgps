package com.roxgps.helper

import android.location.Location

/**
 * Interface untuk menerima callback location updates.
 * Digunakan oleh kedua flavor untuk menerima update lokasi.
 *
 * @author loserkidz
 * @since 2025-05-23 13:41:23
 */
interface LocationListener {
    /**
     * Dipanggil ketika lokasi baru tersedia
     * @param location Lokasi terbaru
     */
    fun onLocationResult(location: Location)

    /**
     * Dipanggil ketika terjadi error dalam mendapatkan lokasi
     * @param errorMessage Pesan error yang terjadi
     */
    fun onLocationError(errorMessage: String)
}