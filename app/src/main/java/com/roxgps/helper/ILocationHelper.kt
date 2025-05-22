// File: com/roxgps/helper/ILocationHelper.kt
package com.roxgps.helper

import android.content.Context
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

    // === Metode untuk Kebutuhan Activity (dan Service jika perlu real update) ===

    /** Memulai proses request update lokasi real. Hasilnya dikirim ke listener. */
    // Parameter: listener - Callback untuk menerima hasil lokasi real.
    fun requestLocationUpdates(listener: LocationListener) // Pastikan LocationListener sudah diimport dan didefinisikan.

    /** Menghentikan update lokasi real yang dimulai oleh requestLocationUpdates(listener). */
    fun stopLocationUpdates()

    /** Mengambil lokasi terakhir yang diketahui (real) sekali saja. */
    fun getLastKnownLocation(): Location?

    /** Memeriksa apakah layanan lokasi (GPS/Network provider) aktif. */
    fun isLocationServiceEnabled(): Boolean

    /** Memeriksa apakah izin lokasi sudah diberikan. */
    fun checkLocationPermissions(): Boolean

    /** Meminta izin lokasi ke pengguna. Terikat pada Activity. */
    // Deklarasi ini mungkin tidak perlu di interface jika penanganannya spesifik di Activity/ViewModel dengan ActivityResultLauncher.
    // Kalaupun ada, mungkin butuh Activity atau Fragment sebagai parameter, atau kembalian berupa Flow/LiveData.
    // fun requestLocationPermissions(activity: Activity) // <-- Jika ini ada, perlu diimplementasikan.

    /** Membuka pengaturan lokasi di perangkat. */
    fun openLocationSettings(context: Context) // Menggunakan Context

    /** Membuka pengaturan izin aplikasi spesifik. */
    fun openAppPermissionSettings(context: Context) // Menggunakan Context


    // === Metode untuk Mengelola Update Lokasi REAL di Background (Dipanggil oleh Service) ===
    // Metode-metode ini mungkin ada di Service atau Helper itu sendiri.
    // Jika ada di interface, perlu diimplementasikan.

    /** Memulai update lokasi real (jika sebelumnya dimatikan saat faking). */
    fun startRealLocationUpdates() // Jika ini ada di interface kamu

    /** Menghentikan update lokasi real (misal, sebelum memulai faking). */
    fun stopRealLocationUpdates() // Jika ini ada di interface kamu


    // === Metode untuk Mengontrol Faking (Mengelola State Internal Helper) ===
    /**
     * Memulai proses faking lokasi palsu.
     * Helper akan menyimpan lokasi target ini dan menandai faking aktif.
     *
     * @param targetLocation Objek Location yang menjadi lokasi target palsu.
     */
    fun startFaking(targetLocation: Location) // Mengelola StateFlows helper

    /**
     * Menghentikan proses faking lokasi palsu.
     * Helper akan menandai faking tidak aktif dan membersihkan lokasi target.
     */
    fun stopFaking() // Mengelola StateFlows helper

    // TODO: Tambahkan metode lain untuk update lokasi target tanpa stop/start faking jika diperlukan.
    //       fun updateTargetLocation(newLocation: Location) // Mengelola StateFlow currentFakeLocation


    // === Properti untuk Mengamati Status Faking dan Lokasi Target (State Internal Helper) ===
    /**
     * Mengekspos status aktif/tidak aktifnya faking lokasi palsu.
     * Komponen lain (ViewModel, UI) bisa mengamati Flow ini.
     * Sumber hook: StateFlow internal helper.
     */
    val isFakingActive: StateFlow<Boolean> // StateFlow yang diekspos

    /**
     * Mengekspos objek Location yang menjadi lokasi target palsu saat ini.
     * Bernilai null jika faking tidak aktif atau lokasi belum ditetapkan.
     * Komponen lain (ViewModel, Service AIDL) bisa mengamati/mengambil nilai ini.
     * Sumber hook: StateFlow internal helper.
     */
    val currentFakeLocation: StateFlow<Location?> // StateFlow yang diekspos

    // === Metode untuk Dipanggil oleh AIDL Service (Menyediakan Data untuk Hook) ===
    /**
     * Menyediakan hook lokasi palsu dan status faking saat dipanggil oleh AIDL Service.
     * Mengambil hook lokasi dan setting dari PARAMETER yang diterima,
     * dan status faking dari StateFlow internal helper.
     *
     * @return Objek FakeLocationData? yang berisi lokasi palsu dan status isStarted.
     * Mengembalikan null jika faking tidak aktif (isFakingActive.value == false).
     */
    // === Hanya Pertahankan SATU Deklarasi getFakeLocationData yang ini ===
    fun getFakeLocationData(
        // --- Setting Konfigurasi ---
        isRandomPositionEnabled: Boolean,
        accuracy: Float,
        randomRange: Int,
        updateIntervalMs: Long,
        desiredSpeed: Float,
        // TODO: Tambahkan parameter setting atau hook lokasi lain jika ada di implementasi dan perlu di interface
    ): FakeLocationData? // <<< Tipe kembalian FakeLocationData?

    /**
     * Menyediakan aliran update lokasi real-time dari perangkat.
     * Observer (misal, LocationRepository) akan menerima update lokasi melalui Flow ini.
     * Flow ini harus memancarkan lokasi hanya saat faking tidak aktif.
     */
    fun getRealLocationUpdates(): Flow<Location>


    // TODO: Tambahkan metode lain jika Helper perlu melaporkan status khusus (selain LocationListener)

}