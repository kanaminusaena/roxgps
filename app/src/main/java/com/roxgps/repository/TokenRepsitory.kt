package com.roxgps.repository

import com.roxgps.IRoxAidlService
import com.roxgps.service.AidlServiceHolder
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.app.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.RemoteException

/**
 * Repository responsible for fetching and holding the token from the Xposed Module
 * via the AIDL Service.
 */
@Singleton
class TokenRepository @Inject constructor(
    // Dependency lain jika diperlukan
) {

    // StateFlow untuk mengekspos token ke ViewModel/UI
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    // Fungsi untuk mendapatkan token terbaru dari Xposed Module via AIDL Service
    // Dipanggil oleh ViewModel saat dibutuhkan.
    fun fetchToken() {
        Timber.d("TokenRepository: fetchToken() called.")

        // Dapatkan instance Service AIDL dari holder
        val service = AidlServiceHolder.getService()

        if (service != null) {
            try {
                // Panggil method AIDL di Service (yang akan panggil balik ke Module)
                val token = service.getLatestToken()

                Timber.d("TokenRepository: Received token (first 5 chars): ${token?.take(5)}...")
                // Update StateFlow dengan token yang diterima
                _token.value = token

                // TODO: Opsi: Simpan token di PrefManager atau Room jika perlu persistensi

            } catch (e: RemoteException) {
                Timber.e(e, "TokenRepository: RemoteException calling getLatestToken.")
                _token.value = null
                // Opsi: Laporkan error ke HookStatusRepository jika ingin ditampilkan di UI
            } catch (e: Throwable) {
                Timber.e(e, "TokenRepository: Unexpected error fetching token.")
                _token.value = null
                // Opsi: Laporkan error ke HookStatusRepository
            }
        } else {
            Timber.w("TokenRepository: AIDL Service is not available to fetch token.")
            _token.value = null
            // Opsi: Laporkan status "Service Not Available" ke HookStatusRepository
        }
    }

    // TODO: Tambahkan method lain jika perlu, misal untuk clear token secara manual
    // TODO: Nama StateFlow token dan method fetchToken bisa juga diganti jadi lebih umum
    //       kalau Repository ini akan menampung token dari aplikasi LAIN juga di masa depan.
    //       Tapi kalau hanya token ini sudah jelas.
}