package com.roxgps.service

// =====================================================================
// Import Library untuk BackgroundTaskService
// =====================================================================

// import com.roxgps.ui.viewmodel.MainViewModel // HAPUS: ViewModel tidak boleh diinjeksi ke Service
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import com.roxgps.helper.ILocationHelper
import com.roxgps.helper.NotificationHelper
import com.roxgps.repository.LocationRepository
import com.roxgps.repository.SearchRepository
import com.roxgps.utils.NotificationsChannel
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Foreground Service yang menjalankan tugas background (misal, faking lokasi).
 * Menerima perintah Start/Stop dari Activity dan Notifikasi.
 * Berkomunikasi dengan ILocationHelper, SearchRepository, dan PrefManager (menggunakan Proto DataStore).
 */
@AndroidEntryPoint // Anotasi Hilt untuk injection dependencies
class BackgroundTaskService : Service() {

    companion object {
        private const val TAG = "BackgroundTaskService"
        // Konstanta untuk Extra Intent (untuk mengirim hook ke Service saat Start)
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
    }

    // === Inject Dependencies ===
    @Inject lateinit var notificationHelper: NotificationHelper // Untuk mengelola Notifikasi
    @Inject lateinit var locationHelper: ILocationHelper // Untuk mengelola logika lokasi (faking/real)
    @Inject lateinit var searchRepository: SearchRepository // Untuk Reverse Geocoding alamat notifikasi
    @Inject lateinit var locationRepository: LocationRepository // Untuk mengelola lokasi umum (real atau fake)
    @Inject lateinit var prefManager: PrefManager // Untuk akses state PrefManager langsung
    // =========================

    // Status internal Service apakah sedang menjalankan tugas utama (misal, faking aktif)
    private var isRunningTask = false

    // === Coroutine Scope untuk menjalankan tugas asynchronous di Service ===
    // Gunakan scope ini untuk Coroutine yang terkait siklus hidup Service.
    private val serviceJob = SupervisorJob() // Job yang akan dibatalkan saat Service dihancurkan
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Scope I/O (untuk tugas blocking seperti Geocoder)
    // =====================================================================

    override fun onCreate() {
        super.onCreate()
        Relog.i(TAG, "Service onCreate")

        // === PENTING: DAFTARKAN RECEIVER NOTIFIKASI DI SINI ===
        // Receiver ini akan menerima Intent dari tombol Notifikasi.
        // Lambda yang diberikan akan dijalankan saat Intent diterima.
        notificationHelper.registerReceiver { // Lambda ini dipanggil saat tombol Stop di notif diklik
            Relog.i(TAG, "Notification Stop button clicked, triggering stop process...")
            stopProcess() // Trigger logika stop terpusat
        }
        // ===================================================

        // Jika Service dihidupkan ulang oleh sistem setelah di-kill,
        // kita perlu memulihkan statusnya jika PrefManager mengindikasikan bahwa seharusnya berjalan.
        serviceScope.launch {
            // Mengambil nilai isStarted dari DataStore (Flow) secara langsung
            val wasStarted = prefManager.isStarted.first() // Mengambil nilai pertama dan membatalkan Flow

            if (wasStarted && !isRunningTask) { // Hanya restore jika PrefManager bilang started dan Service belum active
                Relog.i(TAG, "PrefManager indicates service was started. Restoring foreground state.")

                // Ambil lokasi terakhir dari PrefManager (DataStore)
                val lastLat = prefManager.latitude.first().toDouble()
                val lastLon = prefManager.longitude.first().toDouble()

                // Buat notifikasi awal saat Service di-restart
                val initialNotification = notificationHelper.showStartNotification("Lokasi Palsu Aktif (Memulai Ulang...)")
                startForeground(NotificationsChannel.NOTIFICATION_ID, initialNotification)

                // Lakukan reverse geocoding untuk update notifikasi
                updateNotificationWithLocationAddress(lastLat, lastLon)

                // Mulai faking lagi berdasarkan lokasi terakhir
                val restoredLocation = Location("restored_service").apply {
                    latitude = lastLat
                    longitude = lastLon
                }
                locationHelper.stopRealLocationUpdates()
                locationHelper.startFaking(restoredLocation)
                isRunningTask = true // Perbarui status internal Service
            } else if (!wasStarted && isRunningTask) {
                // Jika PrefManager bilang tidak started, tapi Service masih running, hentikan.
                Relog.i(TAG, "PrefManager indicates service should NOT be started. Stopping self.")
                stopProcess() // Hentikan proses Service
            }
        }
    }

    /**
     * Dipanggil setiap kali startService() dipanggil dengan Intent baru.
     * Ini adalah titik masuk utama untuk perintah ke Service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Relog.i(TAG, "Service onStartCommand: Action = $action, startId = $startId, isRunningTask = $isRunningTask")

        // === Handle berbagai Aksi yang diterima Service ===
        when (action) {
            // --- Aksi untuk MEMULAI Service / Tugas (dari Activity) ---
            NotificationsChannel.ACTION_START_SERVICE -> {
                Relog.i(TAG, "Received START_SERVICE command.")
                if (!isRunningTask) { // Pastikan hanya mulai jika belum berjalan
                    Relog.i(TAG, "Task is not running. Proceeding to start.")
                    // Ambil hook lokasi dari Intent Extra
                    val startLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                    val startLon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

                    if (startLat != 0.0 || startLon != 0.0) { // Validasi hook lokasi dasar
                        Relog.i(TAG, "Valid location hook received ($startLat, $startLon).")

                        // === Perbarui state PrefManager (lokasi dan isStarted=true) ===
                        // PrefManager adalah sumber kebenaran, ViewModel hanya mengamatinya.
                        serviceScope.launch {
                            prefManager.setLocation(startLat.toFloat(), startLon.toFloat())
                            prefManager.setStarted(true) // Atur isStarted menjadi true
                            Relog.i(TAG, "PrefManager updated: Started=true, Location=$startLat, $startLon")
                        }
                        // =============================================================

                        // === MULAI LOGIKA FAKING LOKASI ===
                        val targetLocation = Location("fused").apply {
                            latitude = startLat
                            longitude = startLon
                            // Kamu juga bisa set akurasi default jika perlu, meskipun LocationHook akan menimpanya
                            // accuracy = 10f // Contoh akurasi default
                        }
                        locationHelper.stopRealLocationUpdates()
                        locationHelper.startFaking(targetLocation)
                        Relog.i(TAG, "ILocationHelper startFaking called.")
                        isRunningTask = true // Atur status internal Service
                        // ================================

                        // === Tampilkan notifikasi foreground AWAL ===
                        val initialNotification = notificationHelper.showStartNotification("Lokasi Palsu Aktif...")
                        startForeground(NotificationsChannel.NOTIFICATION_ID, initialNotification)
                        Relog.i(TAG, "Called startForeground with initial notification.")
                        // =========================================================

                        // === LAKUKAN REVERSE GEOCODING DI SERVICE UNTUK ALAMAT NOTIFIKASI ===
                        updateNotificationWithLocationAddress(startLat, startLon)
                        // ===================================================================

                    } else {
                        Relog.i(TAG, "Received START_SERVICE but location hook is invalid ($startLat, $startLon). Stopping service.")
                        stopSelf(startId)
                    }

                } else {
                    Relog.i(TAG, "Received START_SERVICE but task is already running. Ignoring this startId: $startId.")
                }
            }

            // --- Aksi untuk MENGHENTIKAN Service / Tugas (dari Activity atau Notifikasi Receiver) ---
            NotificationsChannel.ACTION_STOP_SERVICE -> {
                Relog.i(TAG, "Received STOP_SERVICE command. Triggering stop process...")
                stopProcess()
            }

            // --- Aksi saat Service di-restart oleh sistem (Intent null atau Action null) ---
            null, "" -> {
                Relog.i(TAG, "Service restarted by system (Intent is null or action is null). flags=$flags, startId=$startId.")
                // Logika pemulihan state sudah ada di onCreate via observasi PrefManager.isStarted
                // Jadi, di sini cukup biarkan onCreate yang menangani jika Service harusnya tetap berjalan.
                // Jika tidak ada logika pemulihan, atau PrefManager.isStarted false,
                // maka biarkan stopProcess() atau stopSelf() dipanggil sesuai kondisi.
                serviceScope.launch { // Harus di coroutine untuk akses DataStore
                    val isStartedFromPref = prefManager.isStarted.first()
                    if (!isRunningTask && !isStartedFromPref) { // Cek lagi apakah prefManager.isStarted sudah false
                        Relog.i(TAG, "Service restarted, but not running and PrefManager.isStarted is false. Stopping self.")
                        stopSelf(startId) // Hentikan diri sendiri jika Service tidak seharusnya berjalan
                    }
                }
            }

            // --- Aksi lain yang tidak dikenal ---
            else -> {
                Relog.i(TAG, "Received unknown action: $action, stopping.")
                stopSelf(startId)
            }
        }
        return START_STICKY
    }

    /**
     * Fungsi helper untuk memperbarui notifikasi foreground dengan alamat lokasi.
     * Dijalankan dalam coroutine.
     */
    private fun updateNotificationWithLocationAddress(lat: Double, lon: Double) {
        serviceScope.launch { // Gunakan serviceScope yang sudah dibuat
            Relog.i(TAG, "Launching coroutine for reverse geocoding for notification.")
            try {
                val address = searchRepository.getAddressStringFromLatLng(lat, lon)
                val addressText = address ?: "Lokasi Palsu Aktif"

                Relog.i(TAG, "Reverse geocoding result for notification: $addressText")

                val updatedNotification = notificationHelper.showStartNotification(addressText)
                startForeground(NotificationsChannel.NOTIFICATION_ID, updatedNotification)
                Relog.i(TAG, "Updated foreground notification with address.")

            } catch (e: Exception) {
                Relog.i(TAG, "Error during reverse geocoding for notification: ${e.message}")
                val errorNotification = notificationHelper.showStartNotification("Lokasi Palsu Aktif (Alamat Error)")
                startForeground(NotificationsChannel.NOTIFICATION_ID, errorNotification)
            }
        }
    }

    /**
     * Dipanggil saat Service akan dihancurkan (misal, setelah stopSelf()).
     * Lakukan pembersihan semua sumber daya di sini.
     */
    override fun onDestroy() {
        Relog.i(TAG, "Service onDestroy. Cleaning up...")

        notificationHelper.unregisterReceiver()
        Relog.i(TAG, "Notification receiver unregistered.")

        locationHelper.stopFaking()
        Relog.i(TAG, "Fake location updates stopped.")
        locationHelper.startRealLocationUpdates()
        Relog.i(TAG, "Real location updates restarted.")

        notificationHelper.cancelNotification()
        Relog.i(TAG, "Notification cancelled.")

        // === Update state PrefManager bahwa Service sudah berhenti ===
        serviceScope.launch { // Pastikan update ini juga di coroutine
            prefManager.setStarted(false) // Update isStarted state menjadi false
            Relog.i(TAG, "PrefManager updated: Started=false.")
        }
        // ==========================================================

        isRunningTask = false
        Relog.i(TAG, "Service cleanup complete. Calling super.onDestroy()")

        serviceJob.cancel() // Batalkan Job/Scope Coroutine saat Service dihancurkan

        super.onDestroy()
    }

    /**
     * Logika penghentian proses Service secara terpusat.
     * Dipanggil oleh Notifikasi Receiver atau oleh onStartCommand saat menerima perintah Stop.
     */
    private fun stopProcess() {
        Relog.i(TAG, "Executing centralized stop process logic")

        locationHelper.stopFaking()
        Relog.i(TAG, "Fake location updates stopped within stopProcess.")
        locationHelper.startRealLocationUpdates()
        Relog.i(TAG, "Real location updates restarted within stopProcess.")

        // === Update state PrefManager bahwa Service sudah berhenti ===
        serviceScope.launch { // Pastikan update ini di coroutine
            prefManager.setStarted(false) // Update isStarted state menjadi false
            Relog.i(TAG, "PrefManager updated: Started=false within stopProcess.")
        }
        // ==============================================================================================

        // === Menghentikan Service itu sendiri ===
        stopSelf()
        Relog.i(TAG, "Calling stopSelf()... onDestroy will follow.")
    }

    /**
     * Dipanggil saat komponen lain mencoba melakukan binding ke Service ini.
     * Dalam kasus ini, kita tidak menggunakan binding, jadi kembalikan null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null // Tidak mendukung binding
    }
}