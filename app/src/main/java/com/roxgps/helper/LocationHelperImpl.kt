// File: com/roxgps/helper/impl/LocationHelperImpl.kt
package com.roxgps.helper // >>> SESUAIKAN PACKAGE INI <<<

// =====================================================================
// Import Library
// =====================================================================

// === Coroutines dan Flow ===

// === Logging ===

// === Random dan Math dari Java ===
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.roxgps.data.FakeLocationData
import com.roxgps.repository.SettingsRepository
import com.roxgps.utils.PrefManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random


// TODO: Import LocationListener jika kamu mendefinisikan interface callback ini
// import com.roxgps.helper.LocationListener // <<< Pastikan import ini jika digunakan


// =====================================================================
// Implementasi ILocationHelper
// =====================================================================

@Singleton // Ditandai sebagai Singleton oleh Hilt
class LocationHelperImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val permissionHelper: PermissionHelper,
    private val prefManager: PrefManager // Injeksi PrefManager
    // TODO: Inject dependency lain jika perlu (misal LocationManager jika mengelola real location di sini)
) : ILocationHelper { // Mengimplementasikan interface ILocationHelper

    // Coroutine Scope untuk tugas Helper. Hidup selama Singleton ini aktif.
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationListener: LocationListener? = null

    // =====================================================================
    // State Internal Helper (Menggunakan StateFlow)
    // =====================================================================

    // Status faking aktif/tidak aktif
    private val _isFakingActive = MutableStateFlow(false)
    override val isFakingActive: StateFlow<Boolean> = _isFakingActive.asStateFlow()

    // Lokasi palsu yang *sedang disimulasikan* oleh loop pergerakan
    private val _currentSimulatedLocation = MutableStateFlow<Location?>(null)

    // Lokasi *target* yang ditetapkan user saat memulai faking
    private val _userTargetLocation = MutableStateFlow<Location?>(null)

    // === Properti Publik dari Interface ILocationHelper ===
    // Interface ILocationHelper kamu membutuhkan properti ini.
    // Properti publik ini mengekspos StateFlow lokasi simulasi.
    // Jika di interface namanya beda (misal currentSimulatedLocation), sesuaikan di sini.
    // ASUMSI INTERFACE PUNYA 'val currentFakeLocation: StateFlow<Location?>'
    override val currentFakeLocation: StateFlow<Location?> = _currentSimulatedLocation.asStateFlow() // <<< Ini implementasi properti interface

    // Job untuk mengelola coroutine update periodik
    private var fakingUpdateJob: Job? = null

    // Callback untuk pembaruan lokasi nyata (jika tidak menggunakan Flow untuk itu secara langsung)
    private val realLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                locationListener?.onLocationResult(location)
                Timber.tag("LocationHelperImpl").d("Real location update: $location")
                // Anda juga bisa mengupdate _realLocationUpdatesFlow jika ingin memancarkan setiap update di sana
            }
        }
    }
    // =====================================================================
    // Implementasi Metode Kontrol Faking
    // =====================================================================

    // Dipanggil oleh Service untuk memulai faking
    override fun startFaking(targetLocation: Location) {
        Timber.d("LocationHelper: startFaking() called with target: ${targetLocation.latitude}, ${targetLocation.longitude}")

        // Simpan lokasi target user.
        _userTargetLocation.value = targetLocation

        // Set lokasi simulasi awal sama dengan lokasi target.
        _currentSimulatedLocation.value = targetLocation

        // Set status faking menjadi true.
        _isFakingActive.value = true

        // Jika Job update berkala belum berjalan, mulai yang baru.
        if (fakingUpdateJob == null || !fakingUpdateJob!!.isActive) {
            fakingUpdateJob = helperScope.launch {
                Timber.d("LocationHelper: Faking update loop started.")

                // Loop berjalan selama coroutine aktif DAN faking aktif
                while (isActive && _isFakingActive.value) {

                    // Dapatkan interval update dari setting
                    val updateIntervalMs = settingsRepository.updateIntervalMsState.value // Dari StateFlow Setting

                    // Dapatkan kecepatan dari setting
                    val desiredSpeedMps = settingsRepository.desiredSpeedState.value // Dari StateFlow Setting

                    // === LOGIKA PENGHITUNGAN LOKASI BERGERAK ===

                    val currentSimulatedLocation = _currentSimulatedLocation.value // Lokasi palsu saat ini
                    val userTargetLocation = _userTargetLocation.value // Lokasi target user

                    // Cek apakah hook tersedia dan kecepatan/interval positif
                    if (currentSimulatedLocation != null && userTargetLocation != null && desiredSpeedMps > 0 && updateIntervalMs > 0) {
                        // === START of the main simulation IF block ===

                        // Hitung jarak yang harus ditempuh dalam interval ini (meter)
                        val distanceToMoveThisInterval = desiredSpeedMps * (updateIntervalMs / 1000.0)

                        // Hitung jarak SISA ke lokasi target user (meter)
                        val distanceRemaining = currentSimulatedLocation.distanceTo(userTargetLocation)

                        // Cek apakah sudah dekat atau sudah mencapai target user
                        val epsilon = 0.5 // Threshold (meter)
                        if (distanceRemaining <= distanceToMoveThisInterval + epsilon) {
                            // Jika sudah sangat dekat, set lokasi persis di target user
                            _currentSimulatedLocation.value = userTargetLocation
                            // Opsi: Hentikan faking otomatis saat sampai:
                            // _isFakingActive.value = false
                            // fakingUpdateJob?.cancel() // Akan membatalkan loop ini
                            Timber.d("LocationHelper: Reached user target location.")

                        } else {
                            // Jika belum sampai, hitung lokasi baru setelah bergerak

                            // Hitung arah (bearing) dari lokasi simulasi saat ini ke lokasi target user (derajat)
                            val bearing = currentSimulatedLocation.bearingTo(userTargetLocation)

                            // === Perhitungan Geodetik (Menggunakan java.lang.Math) ===
                            val earthRadius = 6371000.0 // Radius bumi (meter)

                            val currentLatRad = Math.toRadians(currentSimulatedLocation.latitude) // Menggunakan Math
                            val currentLonRad = Math.toRadians(currentSimulatedLocation.longitude) // Menggunakan Math
                            val bearingRad = Math.toRadians(bearing.toDouble()) // Menggunakan Math

                            val newLatRad = asin(
                                sin(currentLatRad) * cos(distanceToMoveThisInterval / earthRadius) +
                                        cos(currentLatRad) * sin(
                                    distanceToMoveThisInterval / earthRadius
                                ) * cos(bearingRad)
                            ) // Menggunakan Math

                            val newLonRad = currentLonRad + atan2(
                                sin(bearingRad) * sin(distanceToMoveThisInterval / earthRadius) * cos(
                                    currentLatRad
                                ),
                                cos(distanceToMoveThisInterval / earthRadius) - sin(
                                    currentLatRad
                                ) * sin(newLatRad)
                            ) // Menggunakan Math

                            val newLatitude = Math.toDegrees(newLatRad) // Menggunakan Math
                            val newLongitude = Math.toDegrees(newLonRad) // Menggunakan Math
                            // === Akhir Perhitungan Geodetik ===

                            // === Buat objek Location baru untuk posisi yang dihitung ===
                            val newSimulatedLocation = Location(currentSimulatedLocation.provider).apply {
                                latitude = newLatitude
                                longitude = newLongitude
                                accuracy = settingsRepository.accuracyLevelState.value // Akurasi dari setting
                                speed = desiredSpeedMps // Kecepatan simulasi
                                time = System.currentTimeMillis() // Waktu update
                                // TODO: Set bearing, altitude, dll. jika perlu
                                // bearing = bearing // Jika field ada
                            }

                            // === Update StateFlow _currentSimulatedLocation ===
                            _currentSimulatedLocation.value = newSimulatedLocation // UPDATE LOKASI SIMULASI
                            Timber.v("LocationHelper: Updated simulated location to: ${newSimulatedLocation.latitude}, ${newSimulatedLocation.longitude}")
                        } // === END of the inner IF/ELSE for reaching target ===

                    } // === END of the main simulation IF block ===
                    else { // <<< ELSE DARI if (currentSimulatedLocation != null && ...)
                        // Jika hook tidak lengkap, kecepatan/interval 0, atau sudah di target dan tidak bergerak lagi.
                        // Di sini kita hanya perlu log atau handle kasus kenapa simulasi TIDAK berjalan.
                        // Kita tidak perlu mengakses distanceRemaining atau epsilon di sini.
                        if (desiredSpeedMps <= 0 || updateIntervalMs <= 0) {
                            Timber.w("LocationHelper: Speed or Interval is zero/negative. No movement simulation.")
                        } else if (currentSimulatedLocation == null || userTargetLocation == null) {
                            Timber.w("LocationHelper: Simulated or target location is null. Cannot simulate movement.")
                        }
                        // HAPUS BARIS else if (distanceRemaining <= epsilon) { ... } DARI SINI
                    } // <<< AKHIR DARI ELSE

                    // === MENUNGGU HINGGA INTERVAL UPDATE BERIKUTNYA ===
                    if (updateIntervalMs > 0) {
                        delay(updateIntervalMs)
                    } else {
                        delay(100) // Delay minimal jika interval <= 0
                    }

                } // Akhir while loop

                // Kode di sini dijalankan setelah loop while berakhir (misal, faking dihentikan dari luar)
                Timber.d("LocationHelper: Faking update loop finished.")
            } // Akhir helperScope.launch
        }

        Timber.d("LocationHelper: Faking started.") // Log bahwa faking dimulai
    }

    // Dipanggil oleh Service untuk menghentikan faking
    override fun stopFaking() {
        Timber.d("LocationHelper: stopFaking() called.")

        _isFakingActive.value = false // Set status faking nonaktif

        // Batalkan coroutine timer update
        fakingUpdateJob?.cancel() // Batalkan Job jika sedang berjalan
        fakingUpdateJob = null // Set Job menjadi null setelah dibatalkan

        // Bersihkan lokasi simulasi dan lokasi target
        _currentSimulatedLocation.value = null
        _userTargetLocation.value = null

        Timber.d("LocationHelper: Faking stopped.")
    }


    // =====================================================================
    // Implementasi Metode Menyediakan Data untuk Hook (getFakeLocationData)
    // =====================================================================

    // Dipanggil oleh AIDL Service untuk mendapatkan hook lokasi palsu.
    // Metode ini bersifat SINKRONUS!
    override fun getFakeLocationData(
        isRandomPositionEnabled: Boolean,
        accuracy: Float, // Akurasi dari setting
        randomRange: Int,
        updateIntervalMs: Long,
        desiredSpeed: Float,
        // TODO: Tambahkan parameter setting lain jika ada
    ): FakeLocationData? {
        // Timber.v("LocationHelper: getFakeLocationData() called.") // Opsional: log verbose jika dipanggil terus

        // Cek status faking dan pastikan LOKASI SIMULASI ada
        if (!_isFakingActive.value || _currentSimulatedLocation.value == null) {
            // Timber.v("LocationHelper: getFakeLocationData() returning null (not active or no simulated location).") // Opsional log
            return null // Kembalikan null jika faking tidak aktif atau lokasi simulasi belum ada
        }

        // Ambil lokasi simulasi saat ini sebagai lokasi dasar untuk random offset
        val baseLocation = _currentSimulatedLocation.value!! // Lokasi simulasi saat ini (dijamin tidak null di sini)

        val baseLatitude = baseLocation.latitude
        val baseLongitude = baseLocation.longitude

        val fakeLatitude: Double
        val fakeLongitude: Double
        val fakeAccuracy: Float = accuracy // Akurasi dari parameter setting


        // === LOGIKA RANDOM OFFSET ===
        // Menggunakan setting isRandomPositionEnabled dan randomRange
        if (isRandomPositionEnabled) {
            // Menghitung offset random dalam jarak dan arah
            val randomOffsetMeters = Random.nextDouble(0.0, randomRange.toDouble()) // Jarak random dalam meter (0 hingga randomRange)
            val randomDirection = Random.nextDouble() * 2 * Math.PI // Arah random dalam radian (0 hingga 2*PI)

            // Mengkonversi offset meter ke perubahan Latitude/Longitude
            val earthCircumferenceInMeters = 40075000.0

            val latOffset = randomOffsetMeters * cos(randomDirection) / (earthCircumferenceInMeters / 360.0)
            val lonOffset = randomOffsetMeters * sin(randomDirection) / (earthCircumferenceInMeters / 360.0 * cos(
                Math.toRadians(baseLatitude)
            ))

            fakeLatitude = baseLatitude + latOffset
            fakeLongitude = baseLongitude + lonOffset

        } else {
            // Jika random position tidak aktif, gunakan lokasi simulasi saat ini tanpa offset
            fakeLatitude = baseLatitude
            fakeLongitude = baseLongitude
        }

        // =====================================================================
        // === AKHIR LOGIKA PENGHITUNGAN LOKASI PALSU UNTUK HOOK ===
        // =====================================================================

        // Buat objek FakeLocationData yang akan dikirim via AIDL
        return FakeLocationData(
            latitude = fakeLatitude, // Lokasi palsu yang dihitung (base + random offset)
            longitude = fakeLongitude,
            isStarted = _isFakingActive.value, // Status isStarted dari StateFlow internal Helper
            accuracy = fakeAccuracy // Akurasi dari parameter setting
        )
    }
    // =====================================================================
    // Implementasi Metode Interface LAINNYA (Placeholder)
    // =====================================================================
    // Metode-metode ini ada di interface ILocationHelper tapi implementasinya
    // kompleks (butuh LocationManager, PermissionChecker, Intent, dll).
    // Kita berikan implementasi dasar placeholder. Kamu bisa mengisinya nanti.

    // Metode untuk Kebutuhan Activity (dan Service jika perlu real update)
    @SuppressLint("MissingPermission") // Anotasi karena izin akan dicek di tempat lain
    override fun getRealLocationUpdates(): Flow<Location> = callbackFlow {
        if (!permissionHelper.checkLocationPermissions()) {
            // Anda mungkin ingin mengirim error atau melengkapi flow jika tidak ada izin
            close(IllegalStateException("Location permissions not granted"))
            return@callbackFlow
        }
        if (!isLocationServiceEnabled()) {
            close(IllegalStateException("Location services are disabled"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L) // 1 detik interval
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL) // Tidak menunggu activity
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trySend(location) // Mengirim lokasi ke Flow
                    Timber.tag("LocationHelperImpl").d("Flow real location update: $location")
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null) // null untuk looper di thread utama

        // Fungsi suspend ini akan dieksekusi ketika Flow tidak lagi dikonsumsi (misal, Activity berhenti)
        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Timber.tag("LocationHelperImpl").d("Real location updates stopped.")
        }
    }
    // =====================================================================
    // Implementasi Metode Interface LAINNYA (Placeholder)
    // =====================================================================
    // Metode-metode ini ada di interface ILocationHelper tapi implementasinya
    // kompleks (butuh LocationManager, PermissionChecker, Intent, dll).
    // Kita berikan implementasi dasar placeholder. Kamu bisa mengisinya nanti.

    // Metode untuk Kebutuhan Activity (dan Service jika perlu real update)
    override fun requestLocationUpdates(listener: LocationListener) { // >>> Pastikan LocationListener diimpor jika digunakan <<<
        // TODO("Implement real location updates logic here if needed")
        Timber.w("LocationHelper: requestLocationUpdates() called. Not implemented yet.")
        // Jika LocationListener adalah interface yang kamu definisikan:
        // listener.onLocationUpdate(null) // Contoh: panggil callback dengan null atau error
    }

    override fun stopLocationUpdates() {
        // TODO("Implement stopping real location updates here if needed")
        Timber.w("LocationHelper: stopLocationUpdates() called. Not implemented yet.")
    }

    override fun getLastKnownLocation(): Location? {
        // TODO("Implement fetching last known real location here if needed")
        Timber.w("LocationHelper: getLastKnownLocation() called. Not implemented yet. Returning null.")
        return null // Placeholder
    }

    override fun isLocationServiceEnabled(): Boolean {
        // TODO("Implement checking if location service (GPS/Network) is enabled")
        Timber.w("LocationHelper: isLocationServiceEnabled() called. Not implemented yet. Returning true as placeholder.")
        return true // Placeholder
    }

    override fun checkLocationPermissions(): Boolean {
        // TODO("Implement checking location permissions here")
        Timber.w("LocationHelper: checkLocationPermissions() called. Not implemented yet. Returning true as placeholder.")
        return true // Placeholder
    }

    // Metode untuk membuka settings
    override fun openLocationSettings(context: Context) {
        // TODO("Implement opening location settings intent here")
        Timber.w("LocationHelper: openLocationSettings() called. Not implemented yet.")
    }

    override fun openAppPermissionSettings(context: Context) {
        // TODO("Implement opening app permission settings intent here")
        Timber.w("LocationHelper: openAppPermissionSettings() called. Not implemented yet.")
    }

    // Metode untuk Mengelola Update Lokasi REAL di Background (Dipanggil oleh Service)
    // Mungkin ada di Service atau Helper
    override fun startRealLocationUpdates() {
        // TODO("Implement starting real location updates for Service background")
        Timber.w("LocationHelper: startRealLocationUpdates() called. Not implemented yet.")
    }

    override fun stopRealLocationUpdates() {
        // TODO("Implement stopping real location updates for Service background")
        Timber.w("LocationHelper: stopRealLocationUpdates() called. Not implemented yet.")
    }

    // TODO: Tambahkan metode lain jika ada di interface ILocationHelper

    // =====================================================================
    // Cleanup CoroutineScope jika Helper BUKAN Singleton
    // Karena ini Singleton, scope hidup selama aplikasi.
    // =====================================================================
    /*
    fun cleanup() {
        helperScope.cancel()
    }
    */
}