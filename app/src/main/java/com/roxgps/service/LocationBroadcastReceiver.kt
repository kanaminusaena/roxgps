package com.roxgps.service // Atau package lain tempat kamu meletakkan Receiver ini

// =====================================================================
// Import Library untuk LocationBroadcastReceiver
// =====================================================================

// === Import untuk Hilt (jika menggunakan @AndroidEntryPoint pada BroadcastReceiver) ===
// TODO: Import Repository atau komponen lain yang akan memproses lokasi background
// import com.roxgps.repository.LocationRepository // <<< Contoh Repository untuk lokasi background
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.LocationResult
import com.roxgps.repository.LocationRepository
import com.roxgps.utils.Relog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// =====================================================================


// =====================================================================
// LocationBroadcastReceiver (Top-Level Receiver untuk Background Updates)
// =====================================================================

/**
 * Broadcast Receiver untuk menerima update lokasi real di background
 * dari FusedLocationProviderClient atau LocationManager (melalui PendingIntent).
 * Didaftarkan di AndroidManifest.xml.
 *
 * Menggunakan @AndroidEntryPoint untuk mengizinkan dependency injection (Hilt).
 * Pastikan versi Hilt Anda mendukung @AndroidEntryPoint pada BroadcastReceiver.
 */
@AndroidEntryPoint // <<< Anotasi Hilt
class LocationBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LocationBroadcastReceiver"
        // Action string yang sama dengan yang digunakan di Helper saat membuat PendingIntent
        const val ACTION_PROCESS_LOCATION = "com.roxgps.action.PROCESS_LOCATION" // <<< Contoh action
    }

    // TODO: Inject Repository atau komponen lain yang akan memproses lokasi background
    // Contoh: Inject LocationRepository untuk menyimpan atau memproses hook lokasi background
    // @Inject lateinit var locationRepository: LocationRepository // <<< Contoh Inject Repository
    @Inject lateinit var locationRepository: LocationRepository

    // onReceive akan dipanggil saat Intent dari PendingIntent diterima oleh sistem
    // Metode ini berjalan di thread utama proses aplikasi, tetapi harus selesai dengan cepat.
    override fun onReceive(context: Context?, intent: Intent?) {
        Relog.d(TAG, "onReceive called for background location.") // Log saat menerima Intent
        Timber.d("$TAG: onReceive called for action: ${intent?.action}")

        // Pastikan context dan intent tidak null, dan cek action jika perlu
        if (context == null || intent == null) {
            Timber.e("$TAG: Context or Intent is null in onReceive.")
            Relog.e(TAG, "Context or Intent is null.")
            return
        }

        // === PENTING: Gunakan 'goAsync()' karena kita akan meluncurkan Coroutine ===
        // Ini memberi tahu sistem bahwa onReceive() belum selesai dan butuh waktu.
        // Pastikan operasi di Coroutine selesai dalam ~10 detik.
        val pendingResult: PendingResult = goAsync()
        // === LOGIKA MEMBACA DATA LOKASI DARI INTENT ===

        // Cek apakah Intent membawa hasil dari FusedLocationProviderClient (LocationResult)
        if (LocationResult.hasResult(intent)) {
            // Jika dari FusedLocationClient
            val locationResult = LocationResult.extractResult(intent) // Ambil LocationResult
            val locations = locationResult?.locations // Ambil daftar lokasi dari LocationResult

            if (!locations.isNullOrEmpty()) {
                val lastLocation = locations.last() // Ambil lokasi terbaru dari daftar
                Timber.i("$TAG: Received background real location from FusedLocationClient: ${lastLocation.latitude}, ${lastLocation.longitude}", "LocationLog")
                Relog.i(TAG, "Received BG location (Fused): ${lastLocation.latitude}, ${lastLocation.longitude}")

                // TODO: Lakukan sesuatu dengan 'lastLocation' (objek Location) di sini.
                //       Ini adalah lokasi real background.
                //       - Kirim ke Repository untuk disimpan atau diproses.
                //       - Jika Receiver ini di-inject Repository (@Inject lateinit var locationRepository),
                //         kamu bisa panggil locationRepository.saveBackgroundLocation(lastLocation)
                //         Atau locationRepository.processBackgroundLocation(lastLocation)
                //         Metode di Repository ini (save/process) harus suspend fun dan berjalan di Coroutine Scope-nya Repository.
                //       - Jika tidak pakai Inject atau Hilt di Receiver, kamu perlu cara lain
                //         untuk memberikan lokasi ini ke komponen yang bisa memprosesnya di background (misal, start WorkManager, kirim Local Broadcast ke Service yang aktif).

                // Contoh (jika LocationRepository di-inject):
                // locationRepository.saveBackgroundLocation(lastLocation) // Panggil suspend fun dari Coroutine Scope yang tepat
                // === PANGGIL SUSPEND FUN DI REPOSITORY DARI COROUTINE ===
                // Luncurkan Coroutine Scope baru (di Dispatchers.IO untuk operasi I/O seperti Room)
                CoroutineScope(Dispatchers.IO).launch { // <<< DITAMBAHKAN
                    try {
                        // Panggil suspend fun di Repository menggunakan instance yang di-inject
                        locationRepository.saveBackgroundLocation(lastLocation) // <<< DITAMBAHKAN/DIUNCOMMENT
                        Timber.v("$TAG: Successfully passed location to Repository for saving (Fused).") // Log sukses
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Error saving location via Repository (Fused).") // Log error
                    } finally {
                        // PENTING: Tandai onReceive selesai setelah Coroutine selesai
                        pendingResult.finish() // <<< DITAMBAHKAN/DIPINDAHKAN KE DALAM COROUTINE
                    }
                }

            } else {
                Timber.w("$TAG: Received background real location result from FusedLocationClient, but location list is empty or null.")
                Relog.w(TAG, "Received BG result (Fused), but list empty.")
                pendingResult.finish()
            }

        } else {
            // Jika Intent BUKAN dari FusedLocationClient, coba baca dari extra Intent standar (untuk LocationManager)
            // LocationManager kadang menempatkan objek Location di extra dengan key "location".
            @Suppress("DEPRECATION") // getParcelableExtra deprecated
            val location: Location? = intent.getParcelableExtra("location") // Key standar LocationManager

            if (location != null) {
                Timber.i("$TAG: Received background real location (possibly from LocationManager): ${location.latitude}, ${location.longitude}", "LocationLog")
                Relog.i(TAG, "Received BG location (LM?): ${location.latitude}, ${location.longitude}")

                // TODO: Lakukan sesuatu dengan 'location' (objek Location) di sini.
                //       Ini adalah lokasi real background dari LocationManager.
                //       Sama seperti di atas, kirim ke Repository atau komponen pemroses background lainnya.
                // Contoh (jika LocationRepository di-inject):
                // locationRepository.saveBackgroundLocation(location) // Panggil suspend fun dari Coroutine Scope yang tepat

                // === PANGGIL SUSPEND FUN DI REPOSITORY DARI COROUTINE ===
                // Luncurkan Coroutine Scope baru (di Dispatchers.IO untuk operasi I/O seperti Room)
                CoroutineScope(Dispatchers.IO).launch { // <<< DITAMBAHKAN
                    try {
                        // Panggil suspend fun di Repository menggunakan instance yang di-inject
                        locationRepository.saveBackgroundLocation(location) // <<< DITAMBAHKAN/DIUNCOMMENT
                        Timber.v("$TAG: Successfully passed location to Repository for saving (LM?).") // Log sukses
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Error saving location via Repository (LM?).") // Log error
                    } finally {
                        // PENTING: Tandai onReceive selesai setelah Coroutine selesai
                        pendingResult.finish() // <<< DITAMBAHKAN/DIPINDAHKAN KE DALAM COROUTINE
                    }
                }
                // ------------------------------------------------------

            } else {
                // Jika tidak ada LocationResult (Fused) dan tidak ada extra "location" (LM)
                // Ini bisa terjadi jika PendingIntent dipicu oleh alasan lain atau hook lokasi tidak ada.
                Timber.w("$TAG: Received background intent but no LocationResult or 'location' extra found.")
                Relog.i(TAG, "Received BG intent, no location hook found.")
                // Jika tidak ada lokasi, tetap panggil finish()
                pendingResult.finish() // <<< DITAMBAHKAN
            }
        }
        // ---------------------------------------------

        // === LOGIKA PENANGANAN ASYNC (Jika menggunakan goAsync()) ===
        // Jika kamu menggunakan goAsync(), panggil pendingResult.finish() di akhir
        // setelah semua operasi asynchronous selesai atau did delegasikan.
        // Biasanya, panggil goAsync() hanya jika kamu perlu operasi singkat async (~10 detik).
        // Jika operasi lebih panjang, delegasikan ke WorkManager atau JobIntentService/Foreground Service.

        // Contoh:
        // pendingResult.finish()
        // ----------------------------------------------------------

        // Catatan PENTING: onReceive harus selesai dengan CEPAT.
        // Jangan lakukan operasi I/O blocking (seperti akses database besar, network request)
        // atau komputasi berat secara langsung di onReceive tanpa mendelegasikannya
        // ke Coroutine Scope yang tepat (misal, dari Repository yang di-inject)
        // atau WorkManager/JobIntentService.

        // Jika menggunakan Inject Repository dengan Hilt (@AndroidEntryPoint),
        // Repository bisa punya CoroutineScope sendiri (misal @ApplicationScope)
        // dan metode save/process di Repository bisa suspend fun yang
        // kamu panggil dari onReceive (hati-hati panggil suspend fun dari onReceive).
        // Cara aman panggil suspend dari onReceive adalah:
        /*
        val repository = // get repository using EntryPoint accessor or Inject
        val pendingResult = goAsync() // Jika butuh waktu untuk Coroutine di Repo selesai

        CoroutineScope(Dispatchers.IO).launch {
             // Panggil suspend fun di Repository
             repository.saveBackgroundLocation(lastLocation) // Contoh
             pendingResult.finish() // Tandai onReceive selesai setelah Coroutine
        }
        */
        // Atau jika @AndroidEntryPoint + @Inject lateinit:
        /*
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
             locationRepository.saveBackgroundLocation(lastLocation) // Gunakan injected repo
             pendingResult.finish()
        }
        */
    }

    // TODO: Tambahkan metode helper internal jika dibutuhkan di Receiver ini.

    // TODO: Implementasikan metode atau properti yang dibutuhkan oleh Repository
    //       jika Receiver ini perlu berinteraksi dengan Repository (misal, inject).
}