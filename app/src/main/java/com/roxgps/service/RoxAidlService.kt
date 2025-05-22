package com.roxgps.service // Sesuaikan package service kamu

// import android.util.Log // Log tidak lagi dibutuhkan jika semua panggilan dihapus
// Import kelas AIDL yang dihasilkan
// Menggunakan package yang benar untuk FakeLocation
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.RemoteException
import com.roxgps.IRoxAidlService
import com.roxgps.data.FakeLocationData
import com.roxgps.helper.ILocationHelper
import com.roxgps.repository.HookStatusRepository
import com.roxgps.repository.SettingsRepository
import com.roxgps.repository.SettingsRepositoryImpl
import com.roxgps.repository.TokenRepository
import com.roxgps.utils.Relog
import com.roxgps.xposed.HookStatus
import com.roxgps.xposed.IXposedHookManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

// =====================================================================
// RoxAidlService
// Service ini mengimplementasikan antarmuka AIDL untuk komunikasi antar-proses.
// =====================================================================
// === Import untuk Xposed Hook Manager dan Status Hook ===

// ========================================================
/**
 * Service di aplikasi utama yang mengimplementasikan interface AIDL.
 * Service ini akan diikat (bind) oleh Xposed Module di proses target.
 * Tugasnya:
 * 1. Menyediakan hook lokasi palsu dan status faking (dari ILocationHelper) ke hook.
 * 2. Menerima laporan status hook dan error dari hook (via setHookStatus, reportHookError).
 * 3. Menyediakan token atau hook lain yang dibutuhkan hook (via getLatestToken).
 * 4. Melaporkan status binding AIDL dan status faking aktual ke IXposedHookManager
 * agar UI/ViewModel bisa mengamati status hook.
 */
@AndroidEntryPoint // Anotasi Hilt untuk injeksi dependensi
class RoxAidlService : Service() {

    companion object{
        private const val TAG = "RoxAidlService" // <<< Mengubah nama variabel
        private const val MINIMUM_MEMORY_THRESHOLD = 50 * 1024 * 1024 // 50MB
        private const val HEALTH_CHECK_TIMEOUT = 5000L // 5 detik timeout
    }
    // === Inject Dependencies ===
    // Inject ILocationHelper Singleton untuk mendapatkan status faking dan lokasi palsu SAAT INI
    @Inject lateinit var locationHelper: ILocationHelper // <<< Inject ILocationHelper

    // Inject Repository untuk menerima status/error dari hook
    @Inject lateinit var hookStatusRepository: HookStatusRepository // <<< Inject HookStatusRepository

    // Inject Repository untuk menyediakan token
    @Inject lateinit var tokenRepository: TokenRepository // <<< Inject TokenRepository

    // IXposedHookManager: Untuk MELAPORKAN status binding AIDL dan status faking KE MANAGER/UI.
    @Inject lateinit var xposedHookManager: IXposedHookManager // <<< Inject Manager Xposed Hook

    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // PrefManager: Hanya jika hook butuh akses setting umum via AIDL. Jika tidak, bisa dihapus.
    // @Inject lateinit var prefManager: PrefManager // Opsional
    // =====================================================
    // Ini adalah implementasi dari antarmuka AIDL yang didefinisikan di file .aidl
    // Kelas anonim ini mengimplementasikan IRoxAidlService.Stub
    private val binder = object : IRoxAidlService.Stub() {

        @Throws(RemoteException::class) // Tandai metode ini bisa melempar RemoteException
        override fun setFakingEnabled(enabled: Boolean) { // <<< TAMBAHKAN METODE INI
            Timber.d("$TAG: setFakingEnabled($enabled) called by AIDL client.")
            Relog.i(TAG, "AIDL call: setFakingEnabled($enabled) received.")

            serviceScope.launch { // <<< LUNCURKAN COROUTINE DI SINI
                if (enabled) {
                    // === Jika perintah AKTIFKAN diterima ===
                    Timber.i("$TAG: Received command to ENABLE faking.")
                    Relog.i(TAG, "Received command to ENABLE faking.")

                    // TODO: Dapatkan lokasi target yang sebenarnya dari tempat yang sesuai
                    //       Untuk sementara, gunakan lokasi real terakhir sebagai contoh (pastikan aman dari null)
                    val targetLocation: Location? = locationHelper.getLastKnownLocation() // <<< Contoh: ambil lokasi real terakhir
                    // Atau lokasi default: val targetLocation = Location("default").apply { latitude = 0.0; longitude = 0.0 }
                    // Atau dari AppSettings DataStore jika kamu simpan lokasi target di sana

                    if (targetLocation != null) {
                        // Panggil metode startFaking di ILocationHelper dengan lokasi target
                        locationHelper.startFaking(targetLocation) // <<< PANGGIL startFaking()
                        Timber.i("$TAG: Calling locationHelper.startFaking() with target: ${targetLocation.latitude}, ${targetLocation.longitude}.")
                        Relog.i(TAG, "Calling locationHelper.startFaking() with target.")
                        // Laporkan status awal setelah memanggil start (opsional, status aktual dilaporkan di getFakeLocationData)
                        this@RoxAidlService.xposedHookManager.reportHookStatus(HookStatus.BoundAndReady) // Atau status lain yang sesuai
                        Timber.i("$TAG: Reporting HookStatus.BoundAndReady after calling startFaking.")
                        Relog.i(TAG, "Reporting BoundAndReady after calling startFaking.")
                    } else {
                        // Handle jika lokasi target tidak tersedia
                        Timber.w("$TAG: Cannot start faking: Target location is not available.")
                        Relog.i(TAG, "Cannot start faking: Target location is not available.")
                        // Laporkan status jika tidak bisa memulai faking
                        this@RoxAidlService.xposedHookManager.reportHookStatus(HookStatus.BoundAndReady) // Atau status lain yang sesuai
                        Timber.i("$TAG: Reporting HookStatus.BoundAndReady because target location not available.")
                        Relog.i(TAG, "Reporting BoundAndReady because target location not available.")
                    }

                } else {
                    // === Jika perintah NONAKTIFKAN diterima ===
                    Timber.i("$TAG: Received command to DISABLE faking.")
                    Relog.i(TAG, "Received command to DISABLE faking.")

                    // Panggil metode stopFaking di ILocationHelper
                    locationHelper.stopFaking() // <<< PANGGIL stopFaking()
                    Timber.i("$TAG: Calling locationHelper.stopFaking().")
                    Relog.i(TAG, "Calling locationHelper.stopFaking().")
                    // Laporkan status setelah memanggil stop (opsional, status aktual dilaporkan di getFakeLocationData)
                    this@RoxAidlService.xposedHookManager.reportHookStatus(HookStatus.BoundAndReady) // Atau status lain yang sesuai
                    Timber.i("$TAG: Reporting HookStatus.BoundAndReady after calling stopFaking.")
                    Relog.i(TAG, "Reporting BoundAndReady after calling stopFaking.")
                }
            }
            // =====================================
        }

        // Implementasi metode getLatestFakeLocation dari AIDL
        // Metode ini akan dipanggil dari modul Xposed melalui AIDL.
        // Modifier 'override' HARUS ADA jika stub AIDL benar.
        /*override fun getLatestFakeLocation(): FakeLocationData { // <<< 'override' HARUS ADA
            // Baca lat/lon terbaru dari PrefManager
            val latestLat = prefManager.latitude.value.toDouble() // Ambil nilai dari StateFlow dan konversi ke Double
            val latestLon = prefManager.longitude.value.toDouble() // Ambil nilai dari StateFlow dan konversi ke Double
            val latestAccuracy = prefManager.accuracyLevel.value.toFloatOrNull() ?: 0.0f // Ambil akurasi (String) dan konversi ke Float

            // Menghapus Log.d
            Relog.i(TAG, "getLatestFakeLocation called. Returning: Lat=$latestLat, Lon=$latestLon, Accuracy=$latestAccuracy")

            // Kembalikan objek FakeLocationData
            return FakeLocationData(latestLat, latestLon, latestAccuracy) // Mengembalikan objek dengan nama yang benar
        }*/
        /**
         * Implementasi metode getLatestFakeLocation dari AIDL.
         * Dipanggil dari modul Xposed melalui AIDL.
         * Service ini mengambil hook faking dari ILocationHelper Singleton.
         */
        @SuppressLint("TimberArgCount") // Menekan warning jika jumlah argumen Timber tidak sesuai format string
        @Throws(RemoteException::class) // Tandai metode ini bisa melempar RemoteException
        override fun getLatestFakeLocation(): FakeLocationData? {
            Timber.d("$TAG: getLatestFakeLocation() called by AIDL client.")
            Relog.i(TAG, "AIDL call: getLatestFakeLocation() received.")

            // === Ambil Setting Konfigurasi dari SettingsRepository ===
            // Ini adalah setting yang akan dikirim ke helper untuk memengaruhi hook fake.
            // Ambil nilai terbaru dari SettingsRepository.
            val settingsRepoImpl = settingsRepository as SettingsRepositoryImpl
            val isRandomPositionEnabled = settingsRepoImpl.isRandomPositionEnabledState.value // <<< BACA DARI STATEFLOW .value
            val accuracy = settingsRepoImpl.accuracyLevelState.value // <<< BACA DARI STATEFLOW .value
            val randomRange = settingsRepoImpl.randomRangeState.value // <<< BACA DARI STATEFLOW .value
            val updateIntervalMs = settingsRepoImpl.updateIntervalMsState.value // <<< BACA DARI STATEFLOW .value
            val desiredSpeed = settingsRepoImpl.desiredSpeedState.value
            // TODO: Ambil setting lain dari SettingsRepository sesuai parameter getFakeLocationData di ILocationHelper

            // === Panggil Metode getFakeLocationData di ILocationHelper ===
            // Panggil metode getFakeLocationData di helper.
            // LocationHelper akan membaca status faking (isFakingActive.value) dan lokasi target (currentFakeLocation.value)
            // dari state internalnya.
            // Berikan setting konfigurasi yang baru saja diambil dari SettingsRepository sebagai parameter.

            // Panggil getFakeLocationData dengan signature yang disederhanakan (hanya parameter setting)
            val fakeData: FakeLocationData? = locationHelper.getFakeLocationData(
                isRandomPositionEnabled = isRandomPositionEnabled,
                accuracy = accuracy,
                randomRange = randomRange,
                updateIntervalMs = updateIntervalMs,
                desiredSpeed = desiredSpeed, // <<< Berikan parameter desiredSpeed
                // TODO: Berikan parameter setting lain sesuai signature ILocationHelper
            )

            // === Laporkan status faking AKTUAL ke Hook Manager ===
            // Status faking AKTUAL (apakah helper sedang aktif atau tidak) ada di StateFlow helper.
            // Helper sudah mengeceknya di dalam getFakeLocationData dan mengembalikan null jika tidak aktif.
            // Cek status faking di helper secara langsung menggunakan StateFlow.
            val isFakingCurrentlyActive = locationHelper.isFakingActive.value // <<< Cek status faking di StateFlow helper

            if (isFakingCurrentlyActive) { // Jika faking aktif (sesuai StateFlow helper)
                // Laporkan status ActiveFaking ke Manager
                // Gunakan this@RoxAidlService.xposedHookManager
                this@RoxAidlService.xposedHookManager.reportHookStatus(HookStatus.ActiveFaking) // <<< PERBAIKI DI SINI
                Timber.v("$TAG: Reporting HookStatus.ActiveFaking via Manager.", "XposedLog")
                Relog.v(TAG, "Reporting ActiveFaking.")
            } else {
                // Jika faking tidak aktif (sesuai StateFlow helper).
                // Laporkan status BoundAndReady (jika Service masih bound tapi tidak faking)
                // Gunakan this@RoxAidlService.xposedHookManager
                this@RoxAidlService.xposedHookManager.reportHookStatus(HookStatus.BoundAndReady) // <<< PERBAIKI DI SINI
                Timber.v("$TAG: Reporting HookStatus.BoundAndReady via Manager.", "XposedLog")
                Relog.v(TAG, "Reporting BoundAndReady.")
            }
            // =========================================================

            // === Kembalikan objek FakeLocationData atau null ===
            if (fakeData != null) {
                // Log level Debug atau Info agar tidak terlalu banyak log saat faking berjalan
                Timber.d("$TAG: AIDL call: Returning fake location hook: Lat=${fakeData.latitude}, Lon=${fakeData.longitude}, Started=${fakeData.isStarted}")
                Relog.d(TAG, "Returning fake hook: ${fakeData.latitude}, ${fakeData.longitude}, ${fakeData.isStarted}")
                return fakeData // Mengembalikan objek FakeLocationData yang didapat dari helper (jika tidak null)
            } else {
                // Mengembalikan null jika faking tidak aktif atau hook tidak tersedia dari helper
                // (helper mengembalikan null dari getFakeLocationData jika isFakingActive.value false atau targetLocation null)
                Timber.d("$TAG: AIDL call: Faking not active or hook not available from helper. Returning null.")
                Relog.d(TAG, "Returning null (faking not active).")
                return null
            }
            // =======================================================================
        }

        // Implementasi metode setHookStatus dari AIDL
        // Modifier 'override' HARUS ADA jika stub AIDL benar.
        /**
         * Implementasi metode setHookStatus dari AIDL.
         * Dipanggil dari hook untuk melaporkan status hook.
         */
        @Throws(RemoteException::class)
        override fun setHookStatus(hooked: Boolean) {
            Timber.d("$TAG: setHookStatus($hooked) called by AIDL client.")
            // Gunakan this@RoxAidlService.xposedHookManager
            this@RoxAidlService.xposedHookManager.setHookConnected(hooked) // <<< PERBAIKI DI SINI jika metode ini ada di IXposedHookManager
            // TODO: Metode setHookConnected harus ada di IXposedHookManager
        }

        @Throws(RemoteException::class)
        override fun reportHookError(message: String) {
            Timber.e("$TAG: reportHookError() called by AIDL client: $message")
            // Gunakan this@RoxAidlService.xposedHookManager
            this@RoxAidlService.xposedHookManager.reportHookErrorFromHook(message) // <<< PERBAIKI DI SINI jika metode ini ada di IXposedHookManager
            // TODO: Metode reportHookErrorFromHook harus ada di IXposedHookManager
        }

        @Throws(RemoteException::class)
        override fun notifySystemCheck() {
            Timber.d("$TAG: notifySystemCheck() called by AIDL client.")
            // Gunakan this@RoxAidlService.xposedHookManager
            this@RoxAidlService.xposedHookManager.notifySystemCheckCompleted() // <<< PERBAIKI DI SINI jika metode ini ada di IXposedHookManager
            // TODO: Metode notifySystemCheckCompleted harus ada di IXposedHookManager
        }
        // Implementasi metode getLatestToken dari AIDL
        /**
         * Implementasi metode getLatestToken dari AIDL.
         * Dipanggil dari hook untuk mendapatkan token.
         */
        override fun getLatestToken(): String? { // <<< Kembalikan String? karena token bisa null
            Timber.d("$TAG: AIDL call: getLatestToken called.")
            Relog.i(TAG, "AIDL call: getLatestToken called.")

            // === Ambil token dari TokenRepository ===
            // Baca nilai SAAT INI dari StateFlow 'token' di TokenRepository menggunakan .value
            val latestToken = tokenRepository.token.value // <<< PERBAIKI DI SINI! Baca dari StateFlow .value

            Timber.i("$TAG: AIDL call: Returning token: ${latestToken?.take(5)}...") // Log beberapa karakter awal
            Relog.i(TAG, "Returning token: ${latestToken?.take(5)}...")
            return latestToken // Kembalikan token yang dibaca dari StateFlow (bisa null)
            // ======================================
        }

        /**
         * Implementasi health check untuk memverifikasi bahwa Service AIDL masih berjalan dan responsif.
         * Memeriksa:
         * 1. Status LocationHelper
         * 2. Status Repository
         * 3. Penggunaan memori
         * 4. Koneksi dengan komponen utama
         *
         * @return true jika semua komponen berfungsi normal
         * @throws RemoteException jika terjadi error komunikasi
         */
        @Throws(RemoteException::class)
        override fun ping(): Boolean {
            return try {
                Timber.d("$TAG: ping() called by AIDL client")
                Relog.i(TAG, "AIDL call: ping() received.")

                // Menggunakan withTimeout untuk membatasi waktu eksekusi
                val isHealthy = runBlocking {
                    try {
                        withTimeout(HEALTH_CHECK_TIMEOUT) {
                            // Jalankan semua pengecekan secara asynchronous
                            val locationHelperDeferred = async { checkLocationHelper() }
                            val repositoriesDeferred = async { checkRepositories() }
                            val memoryDeferred = async { checkMemoryUsage() }
                            val managerDeferred = async { checkXposedManager() }

                            // Tunggu semua hasil pengecekan
                            val isLocationHelperOK = locationHelperDeferred.await()
                            val isRepositoryOK = repositoriesDeferred.await()
                            val isMemoryOK = memoryDeferred.await()
                            val isManagerOK = managerDeferred.await()

                            // Kombinasikan hasil
                            isLocationHelperOK && isRepositoryOK && isMemoryOK && isManagerOK
                        }
                    } catch (e: TimeoutCancellationException) {
                        val message = "Health check timed out after ${HEALTH_CHECK_TIMEOUT}ms"
                        Timber.w(e, "$TAG: $message") // Log exception dengan message
                        Relog.i(TAG, "$message: ${e.message}")
                        false
                    }
                }

                if (isHealthy) {
                    Timber.d("$TAG: Health check passed")
                    Relog.i(TAG, "Health check passed")
                } else {
                    Timber.w("$TAG: Health check failed")
                    Relog.i(TAG, "Health check failed")
                }

                isHealthy
            } catch (e: Exception) {
                val message = "Error during health check"
                Timber.e(e, "$TAG: $message") // Log exception dengan message
                Relog.e(TAG, "$message: ${e.message}")
                false
            }
        }

        private fun checkLocationHelper(): Boolean {
            return try {
                // Verifikasi bahwa LocationHelper terinisialisasi
                if (!::locationHelper.isInitialized) {
                    Timber.w("$TAG: LocationHelper not initialized")
                    return false
                }

                // Periksa apakah bisa mengakses state
                locationHelper.isFakingActive.value // Akses StateFlow untuk memverifikasi
                true
            } catch (e: Exception) {
                Timber.e(e, "$TAG: LocationHelper check failed")
                false
            }
        }

        private fun checkRepositories(): Boolean {
            return try {
                // Verifikasi bahwa semua repository terinisialisasi
                if (!::hookStatusRepository.isInitialized ||
                    !::tokenRepository.isInitialized ||
                    !::settingsRepository.isInitialized) {
                    Timber.w("$TAG: One or more repositories not initialized")
                    return false
                }

                // Periksa akses ke repository
                val tokenOK = tokenRepository.token.value != null
                val settingsOK = (settingsRepository as? SettingsRepositoryImpl)?.accuracyLevelState?.value != null

                tokenOK && settingsOK
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Repository check failed")
                false
            }
        }

        private fun checkMemoryUsage(): Boolean {
            val runtime = Runtime.getRuntime()
            val freeMemory = runtime.freeMemory()
            val isMemoryOK = freeMemory > MINIMUM_MEMORY_THRESHOLD

            if (!isMemoryOK) {
                Timber.w("$TAG: Low memory: $freeMemory bytes free")
                Relog.i(TAG, "Low memory warning: $freeMemory bytes free")
            }

            return isMemoryOK
        }

        private fun checkXposedManager(): Boolean {
            return try {
                // Verifikasi bahwa XposedHookManager terinisialisasi
                if (!::xposedHookManager.isInitialized) {
                    Timber.w("$TAG: XposedHookManager not initialized")
                    return false
                }

                // Periksa akses ke status dan pastikan nilainya valid
                xposedHookManager.hookStatus.value.let { status ->
                    // Verifikasi bahwa status adalah salah satu dari nilai yang valid
                    when (status) {
                        HookStatus.NotActive,
                        HookStatus.Initializing,
                        HookStatus.BoundAndReady,
                        HookStatus.ActiveFaking -> true

                        else -> {
                            Timber.w("$TAG: Invalid hook status detected: $status")
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: XposedManager check failed")
                false
            }
        }
        // TODO: Implementasikan metode-metode AIDL lainnya yang ada di file .aidl kamu
    }

    // =====================================================================
    // Lifecycle Service
    // =====================================================================

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("$TAG: onBind called.")
        Relog.i(TAG, "onBind called.")

        // === Laporkan status BoundAndReady ke Hook Manager ===
        // Saat onBind dipanggil, ini adalah indikasi kuat bahwa Xposed Module berhasil
        // menemukan dan mencoba mengikat ke Service ini.
        // Laporkan status BoundAndReady.
        xposedHookManager.reportHookStatus(HookStatus.BoundAndReady) // <<< Laporkan status
        Timber.i("$TAG: Reporting HookStatus.BoundAndReady via Manager.")
        Relog.i(TAG, "Reporting BoundAndReady.")
        // =========================================================

        return binder // Mengembalikan implementasi Binder AIDL
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("$TAG: onCreate called.")
        Relog.i(TAG, "onCreate called.")
        // TODO: Lakukan inisialisasi yang dibutuhkan service AIDL ini jika ada.
        //       Contoh: Register receiver atau observer jika Service ini punya peran lain selain AIDL.
        //       Status Initializing sudah di-set oleh Hook Manager saat mencoba start Service.
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG: onDestroy called.")
        Relog.i(TAG, "onDestroy called.")

        // === Laporkan status NotActive ke Hook Manager (Fallback) ===
        // Jika onUnbind tidak dipanggil (misal, proses crash), onDestroy tetap dipanggil.
        // Pastikan status NotActive dilaporkan. Cek status saat ini sebelum update.
        if (xposedHookManager.hookStatus.value != HookStatus.NotActive) {
            xposedHookManager.reportHookStatus(HookStatus.NotActive) // <<< Laporkan status
            Timber.i("$TAG: Reporting HookStatus.NotActive via Manager from onDestroy.")
            Relog.i(TAG, "Reporting NotActive from onDestroy.")
        }
        serviceScope.cancel()
        // =========================================================

        // TODO: Lakukan cleanup yang dibutuhkan service AIDL ini jika ada.
        //       Contoh: Unregister receiver atau observer.
    }

    /**
     * Dipanggil saat semua klien (Xposed Module) sudah unbind dari Service ini.
     * Service ini mungkin akan dihancurkan setelah ini jika tidak ada klien lain
     * atau tidak dimulai dengan startService (kecuali jika startService dipanggil).
     * JUGA MELAPORKAN STATUS TIDAK AKTIF KE HOOK MANAGER.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("$TAG: onUnbind called.")
        Relog.i(TAG, "onUnbind called.")

        // === Laporkan status NotActive ke Hook Manager ===
        // Saat semua klien unbind, Service ini tidak lagi terhubung ke hook.
        // Laporkan status NotActive.
        xposedHookManager.reportHookStatus(HookStatus.NotActive) // <<< Laporkan status
        Timber.i("$TAG: Reporting HookStatus.NotActive via Manager.")
        Relog.i(TAG, "Reporting NotActive.")
        // =========================================================

        // Return true jika Service ingin menerima onRebind() nanti
        return super.onUnbind(intent)
    }
    // Metode lifecycle service lainnya seperti onStartCommand jika service juga bisa dimulai
    // startService() selain di-bind.
    // override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    //     // Menghapus Log.d
    //     // Relog.i(TAG, "onStartCommand called") // Gunakan FileLogger jika perlu log di sini
    //     // TODO: Handle perintah dari intent jika service dimulai dengan startService
    //     return START_STICKY // Atau nilai lain yang sesuai
    // }

    // Service AIDL biasanya TIDAK perlu onStartCommand jika hanya digunakan untuk binding.
    // onStartCommand hanya dipanggil jika Service dimulai dengan context.startService().
    // Kita memanggil startService di XposedHookManagerImpl.enableFakingMechanism(true)
    // untuk memastikan Service hidup dan siap dibind, jadi onStartCommand MUNGKIN dipanggil.
    // Jika onStartCommand dipanggil, pastikan tidak ada logika yang bentrok dengan onBind.
    // Biasanya onStartCommand di Service AIDL dibiarkan kosong atau hanya log.
    /*
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("$TAG: onStartCommand called.")
        Relog.i(TAG, "onStartCommand called.")
        // Return START_NOT_STICKY jika Service tidak perlu di-restart oleh sistem setelah dimatikan
        return START_NOT_STICKY
    }
    */
}
