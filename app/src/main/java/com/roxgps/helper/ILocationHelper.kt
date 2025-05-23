// File: com/roxgps/helper/ILocationHelper.kt
package com.roxgps.helper

import android.location.Location
import com.roxgps.data.FakeLocationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// TODO: Periksa apakah LocationListener ini interface callback yang kamu definisikan
//       atau dari package lain. Pastikan importnya benar.
// import com.roxgps.helper.LocationListener // <<< Pastikan import ini benar

// Interface ini mendefinisikan kontrak untuk Location Helper,
// mencakup kebutuhan Activity (request real lokasi, izin, settings)
// dan Service (start/stop faking, start/stop real update untuk Service).
interface ILocationHelper {

    // === State Properties ===
    /** Status aktif/tidak aktifnya faking lokasi */
    val isFakingActive: StateFlow<Boolean>

    /** Lokasi palsu yang sedang digunakan saat ini */
    val currentFakeLocation: StateFlow<Location?>

    // === Location Methods ===
    /**
     * Request update lokasi real-time
     * @param listener Callback untuk menerima update lokasi
     */
    fun requestLocationUpdates(listener: LocationListener)

    /** Hentikan update lokasi real-time */
    fun stopLocationUpdates()

    /**
     * Dapatkan lokasi terakhir yang diketahui
     * @return Location? atau null jika tidak ada
     */
    fun getLastKnownLocation(): Location?

    /** @return true jika service lokasi (GPS/Network) aktif */
    fun isLocationServiceEnabled(): Boolean

    // === Background Location Methods ===
    /** Mulai update lokasi real di background service */
    fun startRealLocationUpdates()

    /** Hentikan update lokasi real di background service */
    fun stopRealLocationUpdates()

    /**
     * Dapatkan stream update lokasi real-time sebagai Flow
     * @return Flow<Location> stream lokasi real-time
     */
    fun getRealLocationUpdates(): Flow<Location>

    // === Faking Methods ===
    /**
     * Mulai faking lokasi
     * @param targetLocation Lokasi target yang akan di-fake
     */
    fun startFaking(targetLocation: Location)

    /** Hentikan faking lokasi */
    fun stopFaking()

    /**
     * Dapatkan data lokasi palsu untuk AIDL service
     * @param isRandomPositionEnabled Apakah posisi random diaktifkan
     * @param accuracy Akurasi lokasi (meter)
     * @param randomRange Jarak random maksimum (meter)
     * @param updateIntervalMs Interval update (ms)
     * @param desiredSpeed Kecepatan yang diinginkan (m/s)
     * @return FakeLocationData? Data lokasi palsu atau null jika faking tidak aktif
     */
    fun getFakeLocationData(
        isRandomPositionEnabled: Boolean,
        accuracy: Float,
        randomRange: Int,
        updateIntervalMs: Long,
        desiredSpeed: Float
    ): FakeLocationData?

}