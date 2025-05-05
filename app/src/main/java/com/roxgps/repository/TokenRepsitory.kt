package com.roxgps.repository // Sesuaikan package

import com.roxgps.service.AidlServiceHolder // Import AidlServiceHolder
import timber.log.Timber // Import Timber
import javax.inject.Inject // Untuk Dependency Injection Hilt
import javax.inject.Singleton // Untuk Scope Singleton

// Menggunakan StateFlow untuk mengekspos token secara Observable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.RemoteException // Untuk exception AIDL

// =====================================================================
// Repository untuk Mengelola Token dari Xposed Module (Setelah Ganti Nama)
// Repository ini berkomunikasi dengan AIDL Service untuk mendapatkan token.
// =====================================================================

/**
 * Repository responsible for fetching and holding the Gojek token from the Xposed Module
 * via the AIDL Service.
 */
@Singleton // Scope Singleton karena ini Repository
class TokenRepository @Inject constructor( // <-- NAMA KELAS DIGANTI JADI TokenRepository
    // Dependency lain jika diperlukan
) {

    // StateFlow untuk mengekspos token Gojek ke ViewModel/UI
    private val _cocolToken = MutableStateFlow<String?>(null)
    val cocolToken: StateFlow<String?> = _cocolToken.asStateFlow() // Read-only StateFlow

    // Fungsi untuk mendapatkan token terbaru dari Xposed Module via AIDL Service
    // Dipanggil oleh ViewModel saat dibutuhkan.
    fun fetchToken() { // Nama method ini tetap bisa cocolToken karena mengambil token Gojek
        Timber.d("TokenRepository: fetchToken() called.") // Log juga diganti TAG

        // Dapatkan instance Service AIDL dari holder
        val service = AidlServiceHolder.getService()

        if (service != null) {
            try {
                // Panggil method AIDL di Service (yang akan panggil balik ke Module)
                val token = service.getLatestcocolToken() // <-- Memanggil method AIDL

                Timber.d("TokenRepository: Received token (first 5 chars): ${token?.take(5)}...") // Log diganti TAG
                // Update StateFlow dengan token yang diterima
                _cocolToken.value = token // Update nilai StateFlow

                // TODO: Opsi: Simpan token di PrefManager atau Room jika perlu persistensi

            } catch (e: RemoteException) {
                // Error saat komunikasi AIDL
                Timber.e(e, "TokenRepository: RemoteException calling getLatestcocolToken.") // Log diganti TAG
                _cocolToken.value = null // Set token jadi null jika error
                // Opsi: Laporkan error ke HookStatusRepository jika ingin ditampilkan di UI
            } catch (e: Throwable) {
                // Error lain saat fetch token
                Timber.e(e, "TokenRepository: Unexpected error fetching token.") // Log diganti TAG
                 _cocolToken.value = null // Set token jadi null jika error
                 // Opsi: Laporkan error ke HookStatusRepository
            }
        } else {
            // Service AIDL belum berjalan atau tidak tersedia
            Timber.w("TokenRepository: AIDL Service is not available to fetch token.") // Log diganti TAG
            _cocolToken.value = null // Set token jadi null jika service tidak ada
            // Opsi: Laporkan status "Service Not Available" ke HookStatusRepository
        }
    }

    // TODO: Tambahkan method lain jika perlu, misal untuk clear token secara manual
    // TODO: Nama StateFlow cocolToken dan method fetchToken bisa juga diganti jadi lebih umum
    //       kalau Repository ini akan menampung token dari aplikasi LAIN juga di masa depan.
    //       Tapi kalau hanya Gojek, nama ini sudah jelas.
}
