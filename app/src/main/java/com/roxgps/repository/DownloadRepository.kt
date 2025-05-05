package com.roxgps.repository

// =====================================================================
// Import Library DownloadRepository
// =====================================================================

import android.app.DownloadManager // Untuk berinteraksi dengan DownloadManager sistem
import android.content.BroadcastReceiver // Untuk menerima notifikasi download selesai
import android.content.Context // Konteks Aplikasi
import android.content.Intent // Untuk Intent Receiver
import android.content.IntentFilter // Untuk filter Intent Receiver
import android.database.ContentObserver // Untuk mengamati progress download
import android.database.Cursor // Untuk membaca data dari DownloadManager
import android.net.Uri // Untuk Uri file download
import android.os.Handler // Untuk ContentObserver Handler
import android.os.Looper // Untuk Looper ContentObserver
import androidx.core.content.FileProvider // Untuk mendapatkan Uri yang aman dari File
import dagger.hilt.android.qualifiers.ApplicationContext // Qualifier untuk Context Aplikasi
import com.roxgps.BuildConfig // Untuk package name FileProvider
import com.roxgps.R // Untuk resource string
import com.roxgps.update.UpdateChecker // Helper/Service untuk cek update (DI-INJECT di sini)
import kotlinx.coroutines.channels.awaitClose // Untuk Flow builder
import kotlinx.coroutines.flow.Flow // Untuk Flow API
import kotlinx.coroutines.flow.MutableStateFlow // Untuk StateFlow
import kotlinx.coroutines.flow.StateFlow // Untuk StateFlow
import kotlinx.coroutines.flow.asStateFlow // Untuk expose StateFlow
import kotlinx.coroutines.flow.callbackFlow // Untuk membuat Flow dari callback/listener
import kotlinx.coroutines.launch // Untuk Coroutine Scope
import kotlinx.coroutines.Job // Untuk Coroutine Job (optional, jika perlu manage job internal)
import timber.log.Timber // Untuk logging
import java.io.File // Untuk File
import javax.inject.Inject // Untuk Dependency Injection
import javax.inject.Singleton // Untuk menandai Repository sebagai Singleton
import kotlin.math.roundToInt // Untuk rounding progress

// =====================================================================
// Repository untuk Mengelola Proses Cek Update dan Download Update Aplikasi
// Tanggung jawab: Berinteraksi dengan UpdateChecker, DownloadManager, Receiver, Observer.
// ViewModel akan menggunakan Repository ini.
// =====================================================================
@Singleton // Tandai Repository sebagai Singleton karena mengelola resource sistem (DownloadManager, Receiver)
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context, // Inject Application Context
    private val downloadManager: DownloadManager, // Inject DownloadManager sistem
    private val updateChecker: UpdateChecker // Inject UpdateChecker
    // Bisa juga inject SearchHelper di sini kalau Search logic terkait update (misal download peta)
    // private val searchHelper: SearchHelper
) {

    // =====================================================================
    // State yang Diekspos ke ViewModel/UI
    // =====================================================================

    // StateFlow untuk informasi update yang tersedia (dari UpdateChecker)
    private val _updateInfo = MutableStateFlow<UpdateChecker.Update?>(null)
    val updateInfo: StateFlow<UpdateChecker.Update?> = _updateInfo.asStateFlow()

    // StateFlow untuk status dan progress proses download
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // =====================================================================
    // Properti Internal untuk Mengelola Download
    // =====================================================================
    private var downloadRequestId: Long? = null // ID request dari DownloadManager
    private var downloadedFile: File? = null // Referensi file yang didownload

    // Coroutine Scope internal untuk Repository (optional, jika ada background task yg tdk terkait ViewModel lifecycle)
    // private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // =====================================================================
    // Implementasi Receiver & Observer Download
    // Logika ini dipindahkan dari ViewModel.
    // =====================================================================

    // BroadcastReceiver untuk menangani event download selesai/gagal
    private val downloadStateReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            // Cek apakah ID download ini sesuai dengan request kita yang sedang berjalan
            if (id == downloadRequestId) {
                // Karena Receiver dijalankan di Main thread (default),
                // gunakan Coroutine Scope untuk update StateFlow
                // Gunakan CoroutineScope yang tepat (bisa GlobalScope, atau scope internal Repository jika ada)
                // ViewModelScope TIDAK bisa diakses di sini, karena ini di Receiver
                // Jika pakai ApplicationScope: applicationScope.launch { ... }
                // Jika pakai scope internal Repository: repositoryScope.launch { ... }
                // Atau untuk kesederhanaan di Singleton Repository, bisa pakai GlobalScope (hati-hati dengan cancellation)
                 GlobalScope.launch { // HATI-HATI: GlobalScope tidak otomatis ter-cancel
                    var success = false
                    val query = DownloadManager.Query().apply {
                        setFilterById(downloadRequestId ?: return@launch) // Gunakan ID request internal Repository
                    }
                    // DownloadManager query bisa jadi operasi blocking, jalankan di Dispatchers.IO
                    val cursor = withContext(Dispatchers.IO) {
                         downloadManager.query(query)
                    }

                    cursor?.use { // Gunakan use untuk auto close cursor
                        if (it.moveToFirst()) {
                            val columnIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = it.getInt(columnIndex)
                            when(status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    success = true
                                    Timber.d("Download success: ID $downloadRequestId")
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    Timber.e("Download failed: ID $downloadRequestId")
                                    // Optional: Dapatkan alasan gagal dari cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reasonColumn = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = if (reasonColumn != -1) it.getInt(reasonColumn) else -1
                                    _downloadState.emit(DownloadState.Failed(reason)) // Emit state Failed
                                }
                                DownloadManager.STATUS_CANCELLED -> {
                                    Timber.d("Download cancelled: ID $downloadRequestId")
                                     _downloadState.emit(DownloadState.Cancelled) // Emit state Cancelled
                                }
                                // Status lain (PENDING, RUNNING) ditangani oleh ContentObserver
                            }
                        }
                    } ?: Timber.e("Cursor is null or empty for download ID $downloadRequestId")


                    if (success && downloadedFile != null) {
                        // Download sukses, dapatkan Uri file
                        runCatching {
                            val outputUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", downloadedFile!!)
                            Timber.d("Download complete, file URI: $outputUri")
                            _downloadState.emit(DownloadState.Done(outputUri)) // Emit state Done
                        }.onFailure { error ->
                            Timber.e(error, "Failed to get FileProvider URI after download")
                             _downloadState.emit(DownloadState.Failed(null)) // Emit state Failed jika gagal dapat Uri
                        }
                    } else if (!success && (_downloadState.value !is DownloadState.Failed && _downloadState.value !is DownloadState.Cancelled)) {
                         // Jika tidak sukses dan belum di-emit Failed/Cancelled oleh status,
                         // asumsikan Failed (misal cursor kosong karena ID tidak valid)
                         Timber.e("Download failed or status unknown for ID $downloadRequestId")
                         _downloadState.emit(DownloadState.Failed(null))
                    }

                    // Setelah download selesai (sukses/gagal/cancel), unregister receiver & observer
                    unregisterDownloadCallbacks()
                    downloadRequestId = null // Reset request ID
                    downloadedFile = null // Reset file reference
                }
            }
        }
    }

    // ContentObserver untuk mengamati progress download
    private val downloadObserver = object: ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            // Callback ini dijalankan di Main thread (karena handler pakai Looper.getMainLooper())
            // Gunakan Coroutine Scope untuk update StateFlow dan query DownloadManager
             GlobalScope.launch { // HATI-HATI: GlobalScope tidak otomatis ter-cancel
                val query = DownloadManager.Query().apply {
                    setFilterById(downloadRequestId ?: return@launch) // Gunakan ID request internal Repository
                }
                // DownloadManager query bisa jadi operasi blocking, jalankan di Dispatchers.IO
                val c: Cursor? = withContext(Dispatchers.IO) {
                     downloadManager.query(query)
                }

                c?.use { // Gunakan use untuk auto close cursor
                    if (it.moveToFirst()) {
                        val sizeIndex: Int =
                            it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloadedIndex: Int =
                            it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADted_SO_FAR)
                        val statusIndex: Int =
                            it.getColumnIndex(DownloadManager.COLUMN_STATUS) // Cek status juga
                        val status = it.getInt(statusIndex)

                        // Hanya update progress jika statusnya RUNNING atau PENDING
                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                            val size = it.getLong(sizeIndex) // Ukuran bisa besar, pakai Long
                            val downloaded = it.getLong(downloadedIndex) // Pakai Long
                            var progress = 0

                            if (size > 0) { // Hindari pembagian dengan nol
                                progress = (downloaded * 100.0 / size).roundToInt()
                            }
                             // Emit state Downloading dengan progress
                             _downloadState.emit(DownloadState.Downloading(progress))
                             Timber.d("Download progress: $progress%")
                        }
                         // Jika status sudah SUCCESSFUL, FAILED, atau CANCELLED, Receiver yang akan handle
                    }
                }
            }
        }
    }

    // =====================================================================
    // Metode yang Dipanggil dari ViewModel
    // = Delegasi tugas dari ViewModel ke Repository.
    // =====================================================================

    /**
     * Triggers the process of checking for the latest app update.
     * The result is emitted to the [updateInfo] StateFlow.
     */
    suspend fun checkForUpdates() {
        Timber.d("Checking for updates...")
        // Panggil UpdateChecker untuk mendapatkan info update terbaru
        // UpdateChecker.getLatestRelease() mengembalikan Flow, amati itu.
        updateChecker.getLatestRelease()
            .catch { error -> // Tangani error dari Flow UpdateChecker
                 Timber.e(error, "Error checking for updates")
                 _updateInfo.emit(null) // Emit null atau objek error khusus jika gagal cek update
            }
            .collect { update -> // Amati hasil dari UpdateChecker
                 Timber.d("Update check result: $update")
                 _updateInfo.emit(update) // Emit info update ke StateFlow
            }
        // Karena ini suspend fun, pemanggilan di ViewModel akan menunggu koleksi selesai.
        // Jika UpdateChecker Flow tidak pernah selesai, ini akan blocking.
        // Mungkin lebih baik koleksi UpdateChecker Flow di init block Repository?
    }

     /**
     * Starts the download process for a given update asset URL.
     * Manages DownloadManager request and registers observers/receivers.
     * State and progress are emitted to the [downloadState] StateFlow.
     *
     * @param url The URL of the update asset (APK).
     * @param fileName The desired file name for the downloaded asset.
     */
    suspend fun startDownload(url: String, fileName: String) {
         // Pastikan tidak ada download lain yang sedang berjalan
        if (_downloadState.value !is DownloadState.Idle) {
             Timber.d("Download already in progress or state is not Idle.")
            return // Jangan mulai download baru jika belum Idle
        }
         Timber.d("Starting download: $url to $fileName")

        // Buat folder download jika belum ada (di cache eksternal aplikasi)
        val downloadFolder = File(context.externalCacheDir, "updates").apply {
            mkdirs() // Buat direktori, termasuk yang di atasnya jika perlu
        }
        downloadedFile = File(downloadFolder, fileName)

        // Hapus file lama jika ada (opsional, untuk memastikan selalu download versi baru)
         if (downloadedFile!!.exists()) {
             Timber.d("Deleting existing file: ${downloadedFile!!.absolutePath}")
             downloadedFile!!.delete()
         }


        // Register Receiver & Observer SEBELUM memulai download
        registerDownloadCallbacks()

        // Buat dan enqueue request ke DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setDescription(context.getString(R.string.download_manager_description)) // Gunakan context dari constructor
            setTitle(context.getString(R.string.app_name)) // Gunakan context dari constructor
            // Set Uri tujuan (FileProvider akan diurus saat membuka installer)
            setDestinationUri(Uri.fromFile(downloadedFile!!))
            // Optional: setAllowedNetworkTypes, setNotificationVisibility, etc.
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) // Tampilkan notif saat selesai
        }

        // Enqueue request dan simpan ID-nya
        downloadRequestId = downloadManager.enqueue(request)
        Timber.d("Download enqueued with ID: $downloadRequestId")

        // Emit state Downloading awal (progress bisa 0)
        _downloadState.emit(DownloadState.Downloading(0))
    }

    /**
     * Cancels the currently running download, if any.
     * State is emitted to the [downloadState] StateFlow.
     */
    suspend fun cancelDownload() {
        Timber.d("Cancelling download with ID: $downloadRequestId")
        // Batalkan request di DownloadManager menggunakan ID
        // remove() mengembalikan jumlah baris yang dihapus (1 jika berhasil, 0 jika tidak ditemukan)
        val removedCount = downloadRequestId?.let { downloadManager.remove(it) } ?: 0

        if (removedCount > 0) {
            Timber.d("Download ID $downloadRequestId cancelled successfully via DownloadManager.")
            // DownloadManager remove() seharusnya memicu status CANCELLED atau FAILED,
            // yang akan ditangkap oleh Receiver.
            // Receiver akan meng-emit DownloadState.Cancelled atau Failed dan unregister callbacks.
            // Atau kita bisa langsung emit state Cancelled di sini jika yakin remove berhasil
             if (_downloadState.value is DownloadState.Downloading) { // Hanya jika sedang downloading
                 _downloadState.emit(DownloadState.Cancelled) // Emit state Cancelled
             }
             // Unregister callbacks secara manual jika remove() tidak memicu Receiver dengan cepat
             // unregisterDownloadCallbacks() // Ini bisa double unregister kalau Receiver juga dipicu
        } else {
            Timber.d("No active download found to cancel for ID: $downloadRequestId")
            // Jika tidak ada request ID atau remove tidak menghapus apapun, mungkin download sudah selesai/gagal
            // Pastikan state tidak stuck di Downloading jika tidak ada request ID
            if (_downloadState.value is DownloadState.Downloading && downloadRequestId == null) {
                 _downloadState.emit(DownloadState.Idle) // Reset ke Idle jika stuck
                 unregisterDownloadCallbacks() // Coba unregister kalau-kalau masih terdaftar
            }
        }
        downloadRequestId = null // Reset request ID setelah coba cancel
        downloadedFile = null // Reset file reference
    }

    /**
     * Clears the stored update information.
     */
    suspend fun clearUpdateInfo() {
        Timber.d("Clearing update info")
        _updateInfo.emit(null) // Emit null ke StateFlow updateInfo
    }

    /**
     * Resets the download state back to Idle.
     */
    suspend fun resetDownloadState() {
        Timber.d("Resetting download state to Idle")
        // Pastikan callback sudah di-unregister sebelum reset ke Idle
        unregisterDownloadCallbacks()
        downloadRequestId = null
        downloadedFile = null
        _downloadState.emit(DownloadState.Idle) // Emit state Idle
    }

    // =====================================================================
    // Manajemen Receiver & Observer (Internal)
    // Harus dipanggil berpasangan (register/unregister).
    // Gunakan ApplicationContext untuk pendaftaran.
    // =====================================================================

    private var isCallbacksRegistered = false // Flag untuk mencegah double register/unregister

    private fun registerDownloadCallbacks() {
        if (!isCallbacksRegistered) {
             Timber.d("Registering download callbacks")
            // Register Receiver untuk event download selesai
            context.registerReceiver(downloadStateReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), null, Handler(Looper.getMainLooper())) // Gunakan Handler MainLooper jika perlu Receiver di Main
            // Register ContentObserver untuk mengamati progress
            context.contentResolver.registerContentObserver(
                Uri.parse("content://downloads/my_downloads"), // Uri standar untuk DownloadManager
                true, // notifyForDescendants
                downloadObserver // Observer kita
            )
            isCallbacksRegistered = true
        } else {
             Timber.d("Download callbacks already registered")
        }
    }

    private fun unregisterDownloadCallbacks() {
        if (isCallbacksRegistered) {
             Timber.d("Unregistering download callbacks")
            runCatching { // Gunakan runCatching kalau-kalau belum terdaftar (meskipun pakai flag)
                // Unregister Receiver
                context.unregisterReceiver(downloadStateReceiver)
                // Unregister ContentObserver
                context.contentResolver.unregisterContentObserver(downloadObserver)
            }.onFailure { error ->
                Timber.e(error, "Error unregistering download callbacks")
            }
            isCallbacksRegistered = false
        } else {
            Timber.d("Download callbacks already unregistered")
        }
    }

    // =====================================================================
    // Sealed Class untuk State Download (Sebaiknya di file model terpisah)
    // =====================================================================
    // Lo bisa pindahkan definisi sealed class ini ke file terpisah (misal DownloadState.kt)
    // atau biarkan di sini kalau cuma dipakai internal Repository dan ViewModel.
    sealed class DownloadState {
        object Idle: DownloadState() // Tidak ada proses
        data class Downloading(val progress: Int): DownloadState() // Sedang mengunduh, dengan progress 0-100
        data class Done(val fileUri: Uri): DownloadState() // Selesai, dengan Uri file
        data class Failed(val reason: Int?): DownloadState() // Gagal, optional reason code
        object Cancelled: DownloadState() // Dibatalkan
    }

    // =====================================================================
    // Cleanup Repository (Dipanggil saat Singleton scope dihancurkan)
    // Jarang dibutuhkan secara eksplisit di Repository Singleton yang pakai ApplicationContext
    // tapi bisa kalau ada resource yang perlu dilepas saat app mati.
    // =====================================================================
     // @PreDestroy // Anotasi ini dari Javax, perlu dependensi
     // fun cleanup() {
     //    Timber.d("DownloadRepository cleanup")
     //    // Pastikan callbacks di-unregister kalau-kalau aplikasi tiba-tiba mati saat download
     //    unregisterDownloadCallbacks()
     //    // repositoryScope.cancel() // Batalkan scope internal jika dipakai
     // }

    // =====================================================================
    // Inisialisasi Repository (Optional)
    // Bisa digunakan untuk memicu cek update awal saat Repository dibuat.
    // =====================================================================
     init {
         Timber.d("DownloadRepository created")
         // Memicu cek update awal saat Repository ini dibuat pertama kali oleh Hilt
         // Hati-hati: init block ini dipanggil di thread apapun Hilt membuat instansi Singleton.
         // Untuk suspend function seperti checkForUpdates, perlu CoroutineScope.
         // Bisa pakai GlobalScope, atau inject @ApplicationScope CoroutineScope ke Repository.
          GlobalScope.launch { // Menggunakan GlobalScope untuk init check (hati-hati cancellation)
              Timber.d("Running initial update check in init block")
              checkForUpdates() // Memicu cek update
          }
     }
}
