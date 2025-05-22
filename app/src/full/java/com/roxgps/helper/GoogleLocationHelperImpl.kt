// File: com/roxgps/helper/GoogleLocationHelperImpl.kt // <<< Asumsikan nama file ini
package com.roxgps.helper // Sesuaikan dengan package helper kamu

// =====================================================================
// Import Library untuk GoogleLocationHelperImpl (Setelah Perbaikan Duplikasi)
// =====================================================================

// TODO: Periksa apakah LocationListener ini interface callback yang kamu definisikan atau dari package lain
// TODO: Hapus import ActivityScoped jika scope diubah ke Singleton
// import dagger.hilt.android.scopes.ActivityScoped // <<< HAPUS INI
// TODO: Ganti anotasi scope menjadi Singleton

// Import Interface ILocationHelper
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.roxgps.data.FakeLocationData
import com.roxgps.service.LocationBroadcastReceiver
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import com.roxgps.xposed.IXposedHookManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================
// Class GoogleLocationHelperImpl (Implementasi SPESIFIK GOOGLE dari ILocationHelper)
// =====================================================================

// Helper class untuk mengelola status faking lokasi, menyediakan hook untuk AIDL,
// serta mengelola lokasi REAL, permission, dan settings (idealnya dipisah).
@Singleton // <<< GANTI ANOTASI SCOPE KE SINGLETON
class GoogleLocationHelperImpl @Inject constructor( // <<< Pastikan nama Class GoogleLocationHelperImpl dan ada @Inject constructor
    @ApplicationContext private val context: Context, // Context level Aplikasi di-inject
    private val prefManager: PrefManager, // PrefManager di-inject (jika digunakan untuk setting lain)
    private val xposedHookManager: IXposedHookManager, // IXposedHookManager di-inject
    private val locationRequest: LocationRequest,
    private val permissionHelper: PermissionHelper // Inject PermissionHelper
) : ILocationHelper { // <<< Implementasikan Interface ILocationHelper

    companion object {
        private const val TAG = "GoogleLocationHelperImpl" // TAG untuk logging
        private const val BG_LOCATION_REQUEST_CODE = 12345 // Request code untuk PendingIntent
    }

    // === State untuk Faking (Sesuai Interface ILocationHelper) ===
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // MutableStateFlow internal untuk menyimpan status faking aktif (default false)
    private val _isFakingActive = MutableStateFlow(false)
    // StateFlow publik yang diekspos (read-only)
    override val isFakingActive: StateFlow<Boolean> = _isFakingActive.asStateFlow()

    // MutableStateFlow internal untuk menyimpan lokasi target palsu (default null)
    private val _currentFakeLocation = MutableStateFlow<Location?>(null)
    // StateFlow publik yang diekspos (read-only)
    override val currentFakeLocation: StateFlow<Location?> = _currentFakeLocation.asStateFlow()

    // === State dan Helper untuk Lokasi REAL (Idealnya Dipisah) ===

    // Menginisialisasi FusedLocationProviderClient. SPESIFIK GOOGLE.
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context) // Menggunakan context level Aplikasi

    // Simpan referensi listener yang aktif saat ini untuk update lokasi REAL (biasanya UI).
    private var activityLocationListener: LocationListener? = null // Untuk update lokasi REAL di Activity/UI

    // Callback dari FusedLocationProviderClient saat update lokasi REAL diterima (untuk UI). SPESIFIK GOOGLE.
    private val activityLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location? = locationResult.lastLocation
            if (lastLocation != null) {
                Relog.v("$TAG: Received UI location update: ${lastLocation.latitude}, ${lastLocation.longitude}", "LocationLog")
                // Kirim lokasi real ke listener (biasanya Activity/ViewModel yang menampilkan di peta)
                activityLocationListener?.onLocationResult(lastLocation)
            } else {
                Relog.w("$TAG: Real location update received but lastLocation is null.")
                activityLocationListener?.onLocationError("No real location available in update.")
            }
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            Relog.d("$TAG: Location availability changed: ${p0.isLocationAvailable}")
            if (!p0.isLocationAvailable) {
                activityLocationListener?.onLocationError("Layanan lokasi saat ini tidak tersedia.")
            }
        }
    }

    // PendingIntent untuk update lokasi REAL di background.
    private var bgLocationPendingIntent: PendingIntent? = null


    // === Implementasi Metode Umum (Permission, Services, AppSettings) ===
    // TODO: Metode-metode ini idealnya dipisah ke kelas terpisah (misal PermissionHelper, SettingsHelper)
    override fun checkLocationPermissions(): Boolean {
        Relog.d("$TAG: checkLocationPermissions called using PermissionHelper")
        return permissionHelper.checkPermissions(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    override fun isLocationServiceEnabled(): Boolean {
        Relog.d("$TAG: isLocationServiceEnabled called.")

        val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        Relog.d("$TAG: Location service enabled: $isEnabled")
        return isEnabled
    }

    // TODO: Metode requestLocationPermissions tidak ada di sini. Mungkin ada di Activity/ViewModel yang pakai Activity Result Launcher.
    // override fun requestLocationPermissions() { ... }


    // Membuka pengaturan lokasi di perangkat.
    // Menggunakan context level Aplikasi dengan flag NEW_TASK.
    override fun openLocationSettings(context: Context) { // Parameter context ini tidak perlu jika pakai @ApplicationContext
        Relog.d("$TAG: openLocationSettings called.")
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            // Menggunakan context level Aplikasi dari constructor
            this.context.startActivity(intent) // <<< Gunakan context dari constructor
            Relog.i("$TAG: Opened location settings.")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to open location settings.")
        }
    }

    // Membuka pengaturan izin aplikasi spesifik di perangkat.
    // Menggunakan context level Aplikasi dengan flag NEW_TASK.
    override fun openAppPermissionSettings(context: Context) { // Parameter context ini tidak perlu jika pakai @ApplicationContext
        Relog.d("$TAG: openAppPermissionSettings called.")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Menggunakan context level Aplikasi dari constructor dengan label '@'
            val uri = Uri.fromParts("package", this@GoogleLocationHelperImpl.context.packageName, null) // <<< PERBAIKI DI SINI
            setData(uri)
        }
        try {
            // Menggunakan context level Aplikasi dari constructor
            this.context.startActivity(intent) // Ini sudah benar di luar blok apply
            Relog.i("$TAG: Opened app permission settings.")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to open app permission settings.")
        }
    }

    /**
     * Mengambil lokasi terakhir yang diketahui (real) sekali saja.
     * Menggunakan LocationManager.
     *
     * @return Location? Lokasi terakhir yang diketahui atau null.
     */
    @SuppressLint("MissingPermission") // Suppress karena checkPermission() harus dipanggil oleh pemanggil.
    override fun getLastKnownLocation(): Location? { // <<< Pastikan signature ini SAMA dengan di interface
        Relog.d("${TAG}: getLastKnownLocation called.")
        // --- LOGIKA MENDAPATKAN LOKASI REAL TERAKHIR MENGGUNAKAN LOCATIONMANAGER ---
        // Gunakan LocationManager.getLastKnownLocation(provider)
        // Ini bersifat blocking tapi bisa mengembalikan null jika lokasi belum tersedia atau provider mati.

        if (!permissionHelper.checkPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))) {
            Relog.w("$TAG: Permissions not granted for getLastKnownLocation.")
            return null
        }

        // Coba dari provider GPS, lalu Network. Ambil yang paling baru jika keduanya ada.
        var lastGpsLocation: Location? = null
        var lastNetworkLocation: Location? = null

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled) {
            lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            Relog.d("${TAG}: Last known GPS location: $lastGpsLocation")
        } else {
            Relog.d("${TAG}: GPS provider is not enabled.")
        }

        if (isNetworkEnabled) {
            lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            Relog.d("${TAG}: Last known Network location: $lastNetworkLocation")
        } else {
            Relog.d("${TAG}: Network provider is not enabled.")
        }


        // Kembalikan yang paling baru
        val lastKnownLocation = when {
            lastGpsLocation != null && lastNetworkLocation != null -> {
                // Kedua ada, bandingkan waktu (ambil yang lebih baru)
                if (lastGpsLocation.time > lastNetworkLocation.time) lastGpsLocation else lastNetworkLocation
            }
            lastGpsLocation != null -> lastGpsLocation // Hanya GPS yang ada
            lastNetworkLocation != null -> lastNetworkLocation // Hanya Network yang ada
            else -> null // Tidak ada provider yang enabled atau tidak ada last known location sama sekali
        }

        if (lastKnownLocation != null) {
            Relog.v("${TAG}: Got last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}", "LocationLog")
        } else {
            Relog.d("${TAG}: getLastKnownLocation returned null from all enabled providers.")
            // TODO: Laporkan ke UI jika tidak bisa mendapatkan last known location (jika diperlukan)
        }

        return lastKnownLocation
        // ---------------------------------------------
    }
    // Mengimplementasikan requestLocationUpdates dari ILocationHelper (INI UNTUK LOKASI REAL DI UI)
    // Menerima LocationListener (untuk callback di UI) sebagai parameter.
    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(listener: LocationListener) {
        Relog.d("$TAG: requestLocationUpdates (REAL for UI) called.")
        this.activityLocationListener = listener

        // Gunakan PermissionHelper untuk cek permission
        if (!permissionHelper.checkPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))) {
            listener.onLocationError("Location permissions not granted.")
            Relog.e("$TAG: Cannot request real location updates, location permissions not granted.")
            return
        }

        if (!isLocationServiceEnabled()) {
            listener.onLocationError("Location services disabled.")
            Relog.e("$TAG: Cannot request real location updates, location service is disabled.")
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                activityLocationCallback,
                Looper.getMainLooper()
            )
            Relog.i("$TAG: FusedLocationClient requestLocationUpdates started for UI.")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to request real location updates.")
            listener.onLocationError("Failed to request real location updates: ${e.message}")
        }
    }

    // Mengimplementasikan stopLocationUpdates dari ILocationHelper (INI UNTUK LOKASI REAL DI UI)
    override fun stopLocationUpdates() { // <<< Implementasi metode REAL Location Updates
        Relog.d("$TAG: stopLocationUpdates (REAL for UI) called.")
        // Menggunakan context level Aplikasi untuk remove location updates
        fusedLocationClient.removeLocationUpdates(activityLocationCallback) // Hapus update lokasi REAL untuk UI
        Relog.d("$TAG: Stopped real location updates for activity listener.")
        this.activityLocationListener = null // Hapus listener UI
    }


    // === Implementasi Metode Faking (Sesuai Interface ILocationHelper) ===
    // Menggabungkan logika dari startFakeLocationUpdates & startFaking lama

    /**
     * Memulai proses faking lokasi palsu.
     * Helper akan menyimpan lokasi target ini di StateFlow dan menandai faking aktif.
     * Memanggil Xposed Hook Manager untuk mengaktifkan mekanisme faking.
     *
     * @param targetLocation Objek Location yang menjadi lokasi target palsu.
     */
    override fun startFaking(targetLocation: Location) { // <<< Implementasi Metode Faking
        Relog.d("$TAG: startFaking called for ${targetLocation.latitude}, ${targetLocation.longitude}.")

        // Hentikan update lokasi REAL di background jika aktif saat faking dimulai
        stopRealLocationUpdates()
        Relog.d("$TAG: Called stopRealLocationUpdates to prevent interference.")

        // Simpan lokasi target di StateFlow
        _currentFakeLocation.value = targetLocation
        // Tandai faking aktif di StateFlow
        _isFakingActive.value = true

        Relog.i("$TAG: Faking state set to START: ${targetLocation.latitude}, ${targetLocation.longitude}. isFakingActive = true.")

        // Beri tahu Xposed Hook Manager bahwa faking diaktifkan
        xposedHookManager.enableFakingMechanism(true)
        Relog.i("$TAG: Xposed Hook activation triggered via Manager.")
    }

    /**
     * Menghentikan proses faking lokasi palsu.
     * Helper akan menandai faking tidak aktif di StateFlow dan membersihkan lokasi target.
     * Memanggil Xposed Hook Manager untuk menonaktifkan mekanisme faking.
     */
    override fun stopFaking() { // <<< Implementasi Metode Faking
        Relog.d("$TAG: stopFaking called.")

        // Bersihkan lokasi target di StateFlow
        _currentFakeLocation.value = null
        // Tandai faking tidak aktif di StateFlow
        _isFakingActive.value = false

        Relog.i("$TAG: Faking state set to STOP. isFakingActive = false.")

        // Jika perlu, mulai lagi update lokasi REAL di background setelah faking berhenti
        startRealLocationUpdates()
        Relog.d("$TAG: Called startRealLocationUpdates to resume normal behavior.")

        // Beri tahu Xposed Hook Manager bahwa faking dinonaktifkan
        xposedHookManager.enableFakingMechanism(false)
        Relog.i("$TAG: Xposed Hook deactivation triggered via Manager.")
    }

    /**
     * Menyediakan hook lokasi palsu dan status faking saat dipanggil oleh AIDL Service.
     * Metode ini dipanggil oleh RoxAidlService.getLatestFakeLocation().
     * Helper AKAN MEMBACA status faking dan lokasi target dari state internalnya.
     * Helper akan menggabungkan hook lokasi dari state internal dengan parameter setting konfigurasi yang diterima
     * (termasuk desiredSpeed) dan nilai dari lokasi asli terakhir (untuk default natural).
     *
     * @param isRandomPositionEnabled Setting random position.
     * @param accuracy Setting akurasi.
     * @param randomRange Setting random range.
     * @param updateIntervalMs Setting update interval.
     * @param desiredSpeed Setting kecepatan yang diinginkan user (misal untuk simulasi rute/random/joystick).
     * TODO: Tambahkan parameter setting konfigurasi lain jika ada.
     *
     * @return Objek FakeLocationData? yang berisi lokasi palsu dan status isStarted.
     * Mengembalikan null jika faking tidak aktif.
     */
    // Signature Sesuai ILocationHelper yang Baru (parameter setting + desiredSpeed)
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions() jika dipanggil dari luar
    override fun getFakeLocationData(
        isRandomPositionEnabled: Boolean, // Setting dari UI/SettingsRepo
        accuracy: Float, // Akurasi setting (dari UI/SettingsRepo)
        randomRange: Int, // Random range setting (dari UI/SettingsRepo)
        updateIntervalMs: Long, // Update interval setting (dari UI/SettingsRepo)
        desiredSpeed: Float, // <<< Parameter desiredSpeed (dari UI/SettingsRepo)
        // TODO: Tambahkan parameter setting konfigurasi lain jika ada
    ): FakeLocationData? {

        // Log ini bisa terlalu banyak jika dipanggil sangat sering. Gunakan level Debug (D) atau Verbose (V).
        // Relog.v("$TAG: getFakeLocationData() called by AIDL Service with settings: Acc=$accuracy, Random=$isRandomPositionEnabled, Range=$randomRange, Interval=$updateIntervalMs, DesiredSpeed=$desiredSpeed")

        // Ambil status faking internal helper dari StateFlow
        val isActive = isFakingActive.value // <<< Gunakan StateFlow untuk status faking

        // Jika faking tidak aktif, kembalikan null.
        if (!isActive) {
            Relog.d("$TAG: getFakeLocationData requested, but faking is not active. Returning null.")
            return null // Mengembalikan null jika tidak sedang faking
        }

        // === Faking Aktif. Ambil hook lokasi target dan coba lokasi asli terakhir untuk default ===

        // Ambil lokasi target dari StateFlow helper
        val targetLocation = currentFakeLocation.value // <<< Lokasi target yang disimpan saat startFaking/update

        // Jika lokasi target di helper null saat faking aktif? Ini seharusnya tidak terjadi jika startFaking dipanggil benar. Log warning.
        if (targetLocation == null) {
            Relog.w("$TAG: Faking is active, but targetLocation is null! Returning null.")
            return null
        }

        // Coba ambil lokasi asli terakhir yang diketahui sistem (untuk default natural properti lain)
        // Perlu cek permission di sini sebelum getLastLocation jika metode ini bisa dipanggil tanpa permission check sebelumnya.
        // Asumsi permission sudah dicek oleh caller (misal RoxAidlService atau komponen lain), tapi kita cek lagi untuk aman.
        var lastRealLocation: Location? = null
        try {
            // Menggunakan fusedLocationClient (sudah diinisialisasi dengan context)
            val task = fusedLocationClient.lastLocation
            // Menggunakan Tasks.await HANYA jika yakin metode ini TIDAK dipanggil di Main Thread!
            // Jika RoxAidlService memanggil ini di main thread, ini akan ANR!
            // Idealnya, getLastKnownLocation helper itu asynchronous.
            // Untuk demo ini, kita gunakan await, tapi hati-hati.
            // Lebih aman: Buat metode async di LocationHelper dan panggil dari RoxAidlService di coroutine.
            // Cek permission sebelum panggil getLastLocation task
            if (checkLocationPermissions()) {
                // Lakukan await di background thread jika RoxAidlService memanggil ini di coroutine
                // Untuk saat ini, asumsikan caller handle threading atau hati-hati.
                lastRealLocation = Tasks.await(task)
            } else {
                Relog.w("$TAG: Permissions not granted for getLastLocation (for natural defaults). Cannot get last real location.")
            }

            if (lastRealLocation != null) {
                Relog.v("$TAG: Got last real location for defaults: ${lastRealLocation.latitude}, ${lastRealLocation.longitude}", "LocationLog")
            } else {
                Relog.d("$TAG: Last real location is null, using hardcoded defaults for speed/bearing/altitude/provider/accuracy (if not set by setting or desired).")
            }
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to get last real location for defaults: ${e.message}")
            lastRealLocation = null // Pastikan null jika ada error
        }


        // === Buat objek FakeLocationData ===
        // Gunakan hook dari targetLocation (state internal helper) untuk lat/lon.
        // Gunakan parameter setting yang diterima.
        // Gunakan lastRealLocation atau hardcoded default untuk properti lain jika tidak di-set di targetLocation.

        // --- Hitung nilai akhir untuk properti FakeLocationData ---
        val finalLatitude = targetLocation.latitude
        val finalLongitude = targetLocation.longitude

        // Akurasi: PRIORITAS setting dari UI/Repo. Jika setting 0 atau negatif (invalid), gunakan akurasi real atau default.
        val finalAccuracy = if (accuracy > 0) accuracy else lastRealLocation?.accuracy ?: 1.0f // <<< Gunakan setting UI > lastReal > default

        // Speed: PRIORITAS desiredSpeed (jika > 0) > targetLocation > lastReal > default hardcoded
        // Jika desiredSpeed > 0, user ingin bergerak dengan kecepatan ini.
        // Jika desiredSpeed <= 0 (atau default), gunakan fallback (targetLocation speed, lastReal speed, default 0).
        val finalSpeed = if (desiredSpeed > 0) desiredSpeed
        else targetLocation.speed.takeIf { it != 0.0f && !it.isNaN() }
            ?: lastRealLocation?.speed?.takeIf { !it.isNaN() } ?: 0.0f // <<< Gunakan desiredSpeed > targetLocation > lastReal > default. Cek juga NaN.

        // Bearing: PRIORTIAS targetLocation > lastReal > default hardcoded (Bearing biasanya dihitung jika speed > 0)
        // Jika speed > 0, bearing idealnya dihitung berdasarkan pergerakan.
        // Untuk saat ini, kita gunakan fallback dari targetLocation atau lastRealLocation.
        val finalBearing = targetLocation.bearing.takeIf { it != 0.0f && !it.isNaN() } ?: lastRealLocation?.bearing?.takeIf { !it.isNaN() } ?: 0.0f // <<< Gunakan targetLocation > lastReal > default. Cek juga NaN.
        // TODO: Jika mode faking adalah random atau joystick, logika menghitung bearing akan lebih kompleks dan harus diletakkan di sini atau method lain.

        // Altitude: PRIORITAS dari targetLocation > lastReal > default hardcoded
        val finalAltitude = targetLocation.altitude.takeIf { !it.isNaN() } ?: lastRealLocation?.altitude?.takeIf { !it.isNaN() } ?: 0.0 // <<< Gunakan targetLocation > lastReal > default. Cek juga NaN.

        // Provider: PRIORITAS dari targetLocation > lastReal > "faked"
        val finalProvider = targetLocation.provider?.takeIf { it.isNotBlank() } ?: lastRealLocation?.provider?.takeIf { it.isNotBlank() } ?: "faked" // <<< Gunakan targetLocation > lastReal > default "faked". Cek blank.

        // Time & Elapsed Realtime: Selalu ambil timestamp sistem saat ini saat FakeLocationData dibuat
        val finalTime = System.currentTimeMillis() // <<< Timestamp sistem saat ini
        val finalElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() // <<< Timestamp sistem saat ini


        // === Buat objek FakeLocationData TANPA NAMED ARGUMENTS ===
        // Pastikan urutan parameter sesuai dengan konstruktor FakeLocationData
        val fakeData = FakeLocationData(
            finalLatitude, // 1. latitude (Double)
            finalLongitude, // 2. longitude (Double)
            finalAccuracy, // 3. accuracy (Float) <<< PERBAIKAN: Sekarang mengirim Float di sini
            finalSpeed, // 4. speed (Float)
            finalBearing, // 5. bearing (Float)
            finalAltitude, // 6. altitude (Double)
            finalTime, // 7. time (Long)
            finalElapsedRealtimeNanos, // 8. elapsedRealtimeNanos (Long)
            finalProvider, // 9. provider (String?)
            isActive, // 10. isStarted (Boolean) <<< PERBAIKAN: Sekarang mengirim Boolean di sini
            isRandomPositionEnabled, // 11. isRandomPositionEnabled (Boolean)
            randomRange, // 12. randomRange (Int)
            updateIntervalMs // 13. updateIntervalMs (Long)
            // TODO: Isi parameter setting lain sesuai konstruktor FakeLocationData
        )

        // === Gunakan variabel lokal di log string ===
        Relog.d("$TAG: getFakeLocationData requested. Returning FakeLocationData: Lat=${fakeData.latitude}, Lon=${fakeData.longitude}, Started=${fakeData.isStarted}, Acc=${fakeData.accuracy}, Speed=${fakeData.speed}, Bearing=${fakeData.bearing}, Alt=${fakeData.altitude}, Provider=${fakeData.provider}")

        return fakeData // Kembalikan objek FakeLocationData yang sudah lengkap
    }

    // === Metode untuk Mengelola Update Lokasi REAL di Background ===
    // TODO: Pindahkan ini ke kelas atau komponen yang menangani background real location.

    @SuppressLint("MissingPermission")
    override fun startRealLocationUpdates() { // <<< Implementasi Metode REAL Background Updates
        Relog.d("$TAG: startRealLocationUpdates (Background) called.")
        if (!checkLocationPermissions()) {
            Relog.e("$TAG: Cannot start background real location updates, location permissions not granted.")
            return
        }
        if (!isLocationServiceEnabled()) {
            Relog.e("$TAG: Cannot start background real location updates, location service is disabled.")
            return
        }
        // Cek apakah update background sudah aktif
        if (bgLocationPendingIntent != null) {
            Relog.d("$TAG: Background location updates already seem active, skipping start.")
            return
        }

        // Buat LocationRequest untuk background (misal akurasi lebih rendah, interval lebih lama)
        val backgroundLocationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60000) // 1 menit interval
            .setMinUpdateIntervalMillis(30000) // Minimal 30 detik antar update
            .setWaitForAccurateLocation(false) // Tidak perlu tunggu akurat
            .build()

        // Buat Intent dan PendingIntent untuk LocationBroadcastReceiver
        val intent = Intent(context, LocationBroadcastReceiver::class.java).apply {
            action = LocationBroadcastReceiver.ACTION_PROCESS_LOCATION
        }

        // Perlu flag IMMUTABLE untuk PendingIntent di Android 12+
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        bgLocationPendingIntent = PendingIntent.getBroadcast(
            context,
            BG_LOCATION_REQUEST_CODE,
            intent,
            flags // Menggunakan flags yang diperbarui
        )

        // Request update lokasi background
        fusedLocationClient.requestLocationUpdates(backgroundLocationRequest, bgLocationPendingIntent!!)
        Relog.i("$TAG: FusedLocationClient background location updates started via PendingIntent.")
    }

    override fun stopRealLocationUpdates() { // <<< Implementasi Metode REAL Background Updates
        Relog.d("$TAG: stopRealLocationUpdates (Background) called.")
        // Hentikan update hanya jika PendingIntent sudah dibuat
        if (bgLocationPendingIntent != null) {
            // Hentikan update menggunakan PendingIntent yang sama dengan yang digunakan saat request.
            fusedLocationClient.removeLocationUpdates(bgLocationPendingIntent!!)
            Relog.i("$TAG: FusedLocationClient background location updates stopped.")
            // Reset PendingIntent setelah dihentikan
            bgLocationPendingIntent = null
        } else {
            Relog.d("$TAG: stopRealLocationUpdates called but no background PendingIntent active.")
        }
    }
    // --- Implementasi getRealLocationUpdates(): Flow<Location> ---
    // Ini adalah bagian yang hilang dan perlu ditambahkan
    @SuppressLint("MissingPermission")
    override fun getRealLocationUpdates(): Flow<Location> = callbackFlow {
        // Gunakan PermissionHelper untuk cek permission
        if (!permissionHelper.checkPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))) {
            Relog.e("$TAG: Location permissions not granted for getRealLocationUpdates flow.")
            close(IllegalStateException("Location permissions not granted"))
            return@callbackFlow
        }

        if (!isLocationServiceEnabled()) {
            Relog.e("$TAG: Location services disabled for getRealLocationUpdates flow.")
            close(IllegalStateException("Location services disabled"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()

        val flowLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Relog.v("$TAG: Real Location Flow: ${location.latitude}, ${location.longitude}")
                    trySend(location).isSuccess
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Relog.w("$TAG: Real Location Flow: Location availability changed to unavailable.")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                flowLocationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { exception ->
                close(exception)
            }

            awaitClose {
                Relog.d("$TAG: Removing location updates from Flow")
                fusedLocationClient.removeLocationUpdates(flowLocationCallback)
            }
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Error in location updates Flow")
            close(e)
        }
    }

    // TODO: Tambahkan metode helper internal atau cleanup jika diperlukan.

    // Catatan Penting tentang getFakeLocationData():
    // - Signature metode ini (dengan banyak parameter lokasi) SANGAT berbeda dari arsitektur
    //   yang gue usulkan sebelumnya (di mana getFakeLocationData hanya membaca dari StateFlow internal).
    // - Ini berarti caller (RoxAidlService) yang bertanggung jawab MENDAPATKAN semua detail
    //   lokasi (lat, lon, speed, dsb) dan setting konfigurasi, lalu memberikannya sebagai parameter
    //   saat memanggil getFakeLocationData di helper ini.
    // - Helper ini hanya menggunakan parameter yang diterima untuk membuat FakeLocationData,
    //   sementara status faking diambil dari StateFlow internal (isFakingActive.value).
    // - Pastikan ILocationHelper interface kamu memang punya signature getFakeLocationData seperti ini.
}