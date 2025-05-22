package com.roxgps.helper // Sesuaikan dengan package utility atau fitur search kamu

// =====================================================================
// Import Library untuk SearchHelper
// =====================================================================

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.roxgps.R
import com.roxgps.utils.Relog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern


// =====================================================================
// Sealed class SearchProgress (Status Proses Search)
// =====================================================================

// Sealed class untuk merepresentasikan status proses search. BAGUS!
// Sealed class memastikan semua state proses terdefinisi dengan jelas.
/*sealed class SearchProgress {
    // State saat proses search sedang berjalan.
    object Progress : SearchProgress()
    // State saat proses search selesai sukses dan menemukan koordinat.
    // Menyimpan hasil koordinat (latitude dan longitude) bertipe Double.
    hook class Complete(val lat: Double, val lon: Double) : SearchProgress()
    // State saat proses search gagal menemukan alamat atau ada error.
    // Menyimpan pesan error (String?) jika ada.
    hook class Fail(val error: String?) : SearchProgress()
}*/


// =====================================================================
// Class SearchHelper
// =====================================================================

/**
 * Helper class untuk mengelola logika search alamat/koordinat menggunakan Geocoder.
 * Mengembalikan hasil sebagai Flow yang mengindikasikan status proses (loading, sukses, gagal).
 *
 * @param context Context yang digunakan untuk akses Geocoder dan Resources (string).
 */
class SearchHelper(private val context: Context) { // Membutuhkan Context. BAGUS!

    // =====================================================================
    // Method Publik untuk Melakukan Search
    // =====================================================================

    /**
     * Melakukan proses search berdasarkan string input (alamat atau koordinat).
     * Mengembalikan Flow yang akan mengirimkan status proses search (Progress, Complete, Fail).
     * Operasi Geocoder dilakukan di background thread (Dispatchers.IO).
     *
     * @param address String input dari user (bisa alamat atau format "lat,lon").
     * @return Flow<SearchProgress> yang merepresentasikan status dan hasil search.
     */
    suspend fun getSearchAddress(context: Context, address: String) = callbackFlow {
        // Mengirim status Progress ke Flow saat search dimulai.
        trySend(SearchProgress.Progress)

        // Menjalankan operasi blocking (Geocoder atau parsing) di Dispatchers.IO.
        withContext(Dispatchers.IO) {
            // Mengecek apakah input address berupa format koordinat "lat,lon"
            val matcher: Matcher =
                Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)

            if (matcher.matches()) {
                // Jika input sesuai format koordinat "lat,lon"
                // Hapus delay(3000) kecuali Anda benar-benar membutuhkannya.
                // delay(3000)

                try {
                    // Parsing latitude dan longitude dari input Regex yang cocok
                    val parts = matcher.group().split(",")
                    val lat = parts[0].trim().toDouble()
                    val lon = parts[1].trim().toDouble()
                    // Mengirim hasil sukses (koordinat) ke Flow
                    trySend(SearchProgress.Complete(lat, lon))
                } catch (e: NumberFormatException) {
                    // Jika parsing Double gagal
                    trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Atau pesan lebih spesifik: "Format koordinat tidak valid."
                } catch (e: Exception) {
                    // Tangani error lain saat parsing
                    trySend(SearchProgress.Fail("Terjadi kesalahan tak terduga saat memproses koordinat: ${e.message}"))
                }

            } else {
                // Jika input BUKAN format koordinat, gunakan Geocoder untuk mencari alamat
                val geocoder = Geocoder(context, Locale.getDefault())
                val addressList: List<Address>?

                try {
                    // Panggil getFromLocationName yang blocking, tetapi aman karena di Dispatchers.IO
                    // Gunakan @Suppress("DEPRECATION") untuk menekan peringatan di API < 33
                    @Suppress("DEPRECATION")
                    addressList = geocoder.getFromLocationName(address, 3)

                    // Memproses hasil dari Geocoder
                    addressList?.let {
                        if (it.isNotEmpty()) {
                            // Mengambil hasil pertama (paling relevan)
                            val firstAddress = it[0]
                            trySend(SearchProgress.Complete(firstAddress.latitude, firstAddress.longitude))
                        } else {
                            // Jika tidak ada hasil sama sekali.
                            trySend(SearchProgress.Fail(context.getString(R.string.address_not_found)))
                        }
                    } ?: run {
                        // Jika hasil dari Geocoder null (jarang terjadi)
                        trySend(SearchProgress.Fail(context.getString(R.string.address_not_found)))
                    }
                } catch (io: IOException) {
                    // Menangani error I/O (misal tidak ada koneksi internet atau layanan Geocoder tidak tersedia)
                    trySend(SearchProgress.Fail(context.getString(R.string.no_internet_connection_or_service_unavailable))) // Pesan lebih jelas
                } catch (e: Exception) {
                    // Menangani error lain yang mungkin terjadi
                    trySend(SearchProgress.Fail("Terjadi kesalahan tak terduga saat mencari alamat: ${e.message}"))
                }
            }
        }
        // awaitClose akan dipanggil saat Flow di-collect dibatalkan
        awaitClose {
            // Logic cleanup jika diperlukan (misal membatalkan listener jika menggunakan API asinkron dengan listener)
            // Dalam kasus ini, pembatalan withContext(Dispatchers.IO) sudah cukup.
            // this.cancel() // Tidak perlu memanggil cancel() secara eksplisit, awaitClose akan melakukannya.
        }
    }

    /**
     * Helper class untuk mendapatkan alamat dari koordinat latitude dan longitude
     * Menggunakan Coroutines untuk operasi asynchronous
     */
    suspend fun getAddressFromLatLng(lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context, Locale.getDefault())
            val defaultAddress = context.getString(R.string.address_not_found)

            try {
                fetchAddressList(geocoder, lat, lon)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
                    ?: defaultAddress
            } catch (e: IOException) {
                Relog.e("Geocoder error: ${e.message}")
                context.getString(R.string.no_internet)
            } catch (e: Exception) {
                Relog.e("Unexpected error: ${e.message}")
                "Error: ${e.message}"
            }
        }
    }

    /**
     * Mengambil daftar alamat dari Geocoder dengan timeout
     * @param geocoder Instance Geocoder
     * @param lat Latitude
     * @param lon Longitude
     * @return List<Address>? atau null jika timeout atau error
     */
    private suspend fun fetchAddressList(geocoder: Geocoder, lat: Double, lon: Double): List<Address>? {
        return withTimeoutOrNull(5000) { // 5 detik timeout
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (addresses.isNotEmpty()) {
                                // Menggunakan resumeWith yang tidak deprecated
                                continuation.resumeWith(Result.success(addresses))
                            } else {
                                continuation.resumeWith(Result.success(null))
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            val error = errorMessage ?: "Geocoder error"
                            Relog.e("Geocoder error: $error")
                            continuation.resumeWith(Result.failure(IOException(error)))
                        }
                    })

                    continuation.invokeOnCancellation { cause ->
                        Relog.w("Geocoder Coroutine cancelled: ${cause?.message}")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                try {
                    geocoder.getFromLocation(lat, lon, 1)?.takeIf { it.isNotEmpty() }
                } catch (e: Exception) {
                    Relog.e("Legacy Geocoder error: ${e.message}")
                    null
                }
            }
        } ?: run {
            Relog.w("Geocoder timeout after 5 seconds")
            null
        }
    }
    // Fungsi utility isNetworkConnected() sudah dipindah ke NetworkUtils.kt
    // Jadi tidak ada di sini. Panggil NetworkUtils.isNetworkConnected(context) atau cek di ViewModel.
    // fun isNetworkConnected(): Boolean { ... }

}
