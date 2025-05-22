package com.roxgps.repository // Atau package lain tempat kamu meletakkan Repository ini

// =====================================================================
// Import Library untuk LocationRepository
// =====================================================================

// === Import untuk Hilt ===
// ==========================

// === Import untuk Coroutine ===

// TODO: Inject Coroutine Scope (misal @ApplicationScope dari Hilt)
// import kotlinx.coroutines.cancel // Jika scope perlu dicancel eksplisit (jarang untuk ApplicationScope)
// import com.roxgps.di.ApplicationScope // <<< Asumsi Anda punya anotasi @ApplicationScope
// ============================
// === Import untuk Coroutine ===

// TODO: Import dependency lain jika diperlukan (misal, Room DAO untuk simpan ke database)
// import com.roxgps.room.LocationDao // Contoh Room DAO
// import com.roxgps.room.LocationEntity // Contoh hook class Entity Room

// === Import dependency Room DAO dan Entity ===
// =============================================

import android.content.Context
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.roxgps.helper.ILocationHelper
import com.roxgps.module.util.ApplicationScope
import com.roxgps.room.LocationDao
import com.roxgps.room.LocationEntity
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================
// LocationRepository (Untuk Mengelola Data Lokasi Real Background)
// =====================================================================

/**
 * Repository untuk menerima, menyimpan, atau memproses update lokasi real di background.
 * Menerima lokasi dari [LocationBroadcastReceiver].
 *
 * Menggunakan @Singleton dan @Inject untuk dependency injection oleh Hilt.
 * Menggunakan Coroutine Scope (@ApplicationScope) untuk menjalankan operasi background.
 */
@Singleton // Tandai sebagai Singleton agar Hilt hanya membuat satu instance
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context, // Application Context (jika perlu akses resource/database)
    // TODO: Inject Coroutine Scope (misal @ApplicationScope) agar bisa launch coroutine
    @ApplicationScope private val applicationScope: CoroutineScope, // <<< Contoh Inject ApplicationScope
    // TODO: Inject Room DAO atau dependency penyimpanan lain jika menyimpan lokasi
    // === Inject Room DAO ===
    private val locationDao: LocationDao,
    private val prefManager: PrefManager, // Injeksi PrefManager
    private val locationHelper: ILocationHelper // Injeksi ILocationHelper untuk update lokasi
) {

companion object{
    private const val TAG: String = "LocationRepository"
}
    // TODO: Definisikan Coroutine Scope jika tidak di-inject
    // Jika @ApplicationScope tidak di-inject, buat scope manual (pastikan lifecycle-nya sesuai)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Contoh scope manual di Repository
    // TODO: Definisikan StateFlow atau Flow lain jika Repository mengekspos hook lokasi background terbaru atau riwayat
    val allLocations: Flow<List<LocationEntity>> = locationDao.getAllLocations()
    private val _currentLocation = MutableLiveData<Location>()
    val currentLocation: LiveData<Location> = _currentLocation
    // Flow dari ILocationHelper
    private val _realLocationUpdates: Flow<Location> = locationHelper.getRealLocationUpdates()
    // =====================================================================
    // Metode yang Dipanggil dari LocationBroadcastReceiver atau Komponen Lain
    // =====================================================================

    /**
     * Menerima update lokasi real dari [LocationBroadcastReceiver] dan memprosesnya.
     * Metode ini dipanggil dari [BroadcastReceiver.onReceive], yang harus selesai cepat.
     * Operasi yang memakan waktu (misal, simpan ke database) harus diluncurkan di Coroutine.
     *
     * @param location Objek Location yang diterima dari API lokasi.
     */
    fun saveBackgroundLocation(location: Location) { // Tidak perlu suspend fun jika mendelegasikan ke Coroutine Scope internal
        Timber.i("$TAG: Received background location update for saving.")
        Relog.i(TAG, "Received BG location: ${location.latitude}, ${location.longitude}")

        // === Luncurkan Coroutine untuk Melakukan Operasi Background ===
        // Gunakan Coroutine Scope yang di-inject (@ApplicationScope) atau scope internal Repository.
        // Dispatchers.IO cocok untuk operasi database/disk atau network.
        // applicationScope.launch(Dispatchers.IO) { // Jika menggunakan scope yang di-inject
        repositoryScope.launch(Dispatchers.IO) {
        //CoroutineScope(Dispatchers.IO).launch { // <<< Contoh menggunakan CoroutineScope(Dispatchers.IO) manual jika scope tidak di-inject
            Timber.v("$TAG: Launching coroutine to save location.")
            try {
                // TODO: Lakukan operasi penyimpanan atau pemrosesan lokasi di sini.
                //       Misal: Simpan objek Location ke database Room.
                //       val locationEntity = LocationEntity.fromLocation(location) // Konversi Location ke Entity Room
                //       locationDao.insert(locationEntity) // Panggil Room DAO untuk menyimpan
                // TODO: Konversi objek Location ke Entity Room
                val locationEntity = LocationEntity.fromLocation(location) // Menggunakan helper method di Entity
                // Contoh sederhana: Hanya log dan beri indikasi bahwa 'save' berhasil secara async
                // TODO: Panggil DAO Room untuk menyimpan Entity
                locationDao.insertLocation(locationEntity) // <<< Panggil metode insert di DAO yang di-inject
                Timber.i("$TAG: Successfully processed/saved background location: ${location.latitude}, ${location.longitude}", "LocationLog")
                Relog.i(TAG, "Processed/Saved BG location: ${location.latitude}, ${location.longitude}")

            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to save/process background location.")
                Relog.i(TAG, "Failed to process BG location.")
                // TODO: Tangani error penyimpanan/pemrosesan (misal, log error, laporkan ke komponen lain jika perlu)
            }
        }
        // Catatan: Metode saveBackgroundLocation() ini sendiri selesai dengan cepat setelah coroutine diluncurkan.
        // Operasi penyimpanan/pemrosesan actual berjalan di background di dalam coroutine.
        // Ini penting agar onReceive() di BroadcastReceiver bisa selesai dengan cepat.
    }
    // Flow untuk lokasi terakhir yang diketahui dari PrefManager
    // Ini mencerminkan lokasi yang terakhir disetel/di-fake, bukan lokasi real-time dari sensor.
    val lastKnownSetLocation: Flow<Location?> = prefManager.latitude
        .combine(prefManager.longitude) { lat, lon ->
            if (lat != 0.0f && lon != 0.0f) { // Pastikan koordinat valid
                Location("pref_manager_location").apply {
                    latitude = lat.toDouble()
                    longitude = lon.toDouble()
                }
            } else {
                null
            }
        }
    // MutableStateFlow untuk menyimpan lokasi real-time yang didapat dari ILocationHelper
    // Misalnya, jika ILocationHelper memberikan update GPS asli.
    private val _currentActiveLocation = MutableStateFlow<Location?>(null)
    //val currentActiveLocation: StateFlow<Location?> = _currentActiveLocation.asStateFlow()
    // Menggabungkan dan mengubahnya menjadi StateFlow
    val currentActiveLocation: StateFlow<Location?> = _realLocationUpdates
        .combine(locationHelper.isFakingActive) { realLoc, isFaking ->
            // Logika untuk menentukan lokasi aktif (real atau fake)
            if (isFaking && locationHelper.currentFakeLocation.value != null) {
                locationHelper.currentFakeLocation.value
            } else {
                realLoc
            }
        }
        .stateIn(
            // Scope di repository, bukan viewModelScope, karena repository adalah singleton
            repositoryScope, // Pastikan repositoryScope sudah diinisialisasi
            SharingStarted.WhileSubscribed(5000), // Start ketika ada observer, stop setelah 5 detik jika tidak ada
            null // Nilai awal (belum ada lokasi yang diketahui)
        )

    init {
        // Gabungkan aliran lokasi real dan lokasi fake.
        // Pilih mana yang harus ditampilkan berdasarkan status faking.
        locationHelper.isFakingActive // Mengamati apakah faking aktif
            .combine(locationHelper.currentFakeLocation) { isFaking, fakeLocation ->
                // Jika faking aktif dan lokasi palsu ada, gunakan lokasi palsu
                if (isFaking && fakeLocation != null) {
                    fakeLocation
                } else {
                    null // Jika tidak, kembalikan null untuk "jalur palsu"
                }
            }
            .combine(locationHelper.getRealLocationUpdates()) { fakeOrNull, realLocation ->
                // Jika fakeOrNull memiliki nilai (artinya faking aktif), gunakan itu.
                // Jika tidak, gunakan realLocation.
                fakeOrNull ?: realLocation
            }
            .onEach { activeLocation ->
                // Saat ada lokasi aktif baru, perbarui _currentActiveLocation
                _currentActiveLocation.value = activeLocation
            }
            .launchIn(repositoryScope) // Ini adalah bagian KRUSIAL. Meluncurkan Flow di Coroutine Scope.
    }

    // Metode untuk memperbarui lokasi yang disimpan di PrefManager (dari UI atau Service)
    suspend fun updateSetLocation(location: Location) {
        prefManager.setLocation(location.latitude.toFloat(), location.longitude.toFloat())
    }

    // Metode untuk mendapatkan lokasi terakhir yang disetel (ini membaca dari PrefManager)
    suspend fun getLastSetLocation(): Location? {
        val lat = prefManager.latitude.first()
        val lon = prefManager.longitude.first()
        return if (lat != 0.0f || lon != 0.0f) {
            Location("last_set").apply {
                latitude = lat.toDouble()
                longitude = lon.toDouble()
            }
        } else {
            null
        }
    }

    // Anda mungkin juga memiliki metode seperti:
    // fun startRealLocationUpdates() { locationHelper.startRealLocationUpdates() }
    // fun stopRealLocationUpdates() { locationHelper.stopRealLocationUpdates() }
    // fun startFaking(location: Location) { locationHelper.startFaking(location) }
    // fun stopFaking() { locationHelper.stopFaking() }
    // ... tapi ini sebenarnya lebih cocok dikelola oleh Service karena terkait lifecycle
    // Service yang memulai/menghentikan ILocationHelper.

    // TODO: Tambahkan metode lain jika Repository perlu fungsi lain (misal, get riwayat lokasi, delete lokasi).
    // Contoh:
    /*
    // suspend fun getRecentLocations(): Flow<List<LocationEntity>> { ... } // Contoh expose hook dari database
    // suspend fun deleteAllLocations() { ... } // Contoh hapus hook
    */


    // =====================================================================
    // Initialization (Optional)
    // =====================================================================
    init {
        Timber.d("LocationRepository created")
        Relog.i(TAG, "LocationRepository created.")
        // Tidak ada inisialisasi khusus yang blocking di sini.
    }

    // =====================================================================
    // Cleanup Repository (Optional, untuk Singleton)
    // =====================================================================
    // Jarang dibutuhkan secara eksplisit untuk Repository Singleton sederhana
    // @PreDestroy // Anotasi ini dari Javax, perlu dependensi
    // fun cleanup() {
    //    Timber.d("LocationRepository cleanup")
    //     Relog.i(TAG, "LocationRepository cleanup.")
    //    // Batalkan Coroutine Scope jika dibuat secara manual di sini
    //    // repositoryScope.cancel() // Contoh jika menggunakan scope manual
    // }
}