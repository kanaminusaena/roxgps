package com.roxgps.helper // Sesuaikan dengan package utility atau fitur search kamu

// =====================================================================
// Import Library untuk SearchHelper
// =====================================================================

import android.content.Context // Untuk Context
import android.location.Address // Untuk objek Address dari Geocoder
import android.location.Geocoder // Untuk Geocoder
import android.os.Build // Perlu untuk cek versi Geocoder API 33+
import com.roxgps.R // Import R untuk string resources
import kotlinx.coroutines.Dispatchers // Untuk pindah Dispatcher (IO)
import kotlinx.coroutines.cancel // Untuk cancel coroutine di Flow
import kotlinx.coroutines.delay // Jika delay masih diperlukan (opsional)
import kotlinx.coroutines.flow.callbackFlow // Untuk membuat Flow dari operasi callback/blocking
import kotlinx.coroutines.withContext // Untuk berpindah thread (Dispatcher)
import java.io.IOException // Untuk menangani error I/O dari Geocoder
import java.util.Locale // Perlu untuk Geocoder (menentukan bahasa/region)
import java.util.regex.Matcher // Untuk Regex pattern matching
import java.util.regex.Pattern // Untuk Regex pattern
import kotlinx.coroutines.channels.awaitClose // Untuk resource cleanup di callbackFlow


// =====================================================================
// Sealed class SearchProgress (Status Proses Search)
// =====================================================================

// Sealed class untuk merepresentasikan status proses search. BAGUS!
// Sealed class memastikan semua state proses terdefinisi dengan jelas.
sealed class SearchProgress {
    // State saat proses search sedang berjalan.
    object Progress : SearchProgress()
    // State saat proses search selesai sukses dan menemukan koordinat.
    // Menyimpan hasil koordinat (latitude dan longitude) bertipe Double.
    data class Complete(val lat: Double, val lon: Double) : SearchProgress()
    // State saat proses search gagal menemukan alamat atau ada error.
    // Menyimpan pesan error (String?) jika ada.
    data class Fail(val error: String?) : SearchProgress()
}


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
    suspend fun getSearchAddress(address: String) = callbackFlow {
        // Mengirim status Progress ke Flow saat search dimulai. BAGUS!
        trySend(SearchProgress.Progress)

        // Menjalankan operasi blocking (Geocoder atau parsing) di Dispatchers.IO. WAJIB!
        withContext(Dispatchers.IO) {
            // Mengecek apakah input address berupa format koordinat "lat,lon"
            // Menggunakan Regex Pattern matching. BAGUS!
            val matcher: Matcher =
                Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)

            if (matcher.matches()) {
                // Jika input sesuai format koordinat "lat,lon"
                // delay(3000) // Delay asli 3 detik - pastikan apakah ini masih diperlukan atau bisa dihapus

                try {
                    // Parsing latitude dan longitude dari input Regex yang cocok
                    val parts = matcher.group().split(",") // Memisahkan string berdasarkan koma
                    val lat = parts[0].trim().toDouble() // Ambil bagian pertama (latitude), hilangkan spasi, ubah ke Double. BAGUS!
                    val lon = parts[1].trim().toDouble() // Ambil bagian kedua (longitude), hililangkan spasi, ubah ke Double. BAGUS!
                    // Mengirim hasil sukses (koordinat) ke Flow
                    trySend(SearchProgress.Complete(lat, lon)) // Mengirim state Complete dengan data. BAGUS!
                } catch (e: NumberFormatException) {
                    // Jika parsing Double gagal, laporkan error ke Flow
                    // Menggunakan string resources dari context. BAGUS!
                    trySend(SearchProgress.Fail(context.getString(R.string.address_not_found)))
                } catch (e: Exception) {
                    // Tangani error lain saat parsing
                    trySend(SearchProgress.Fail("Terjadi kesalahan saat memproses koordinat: ${e.message}"))
                }

            } else {
                // Jika input BUKAN format koordinat, gunakan Geocoder untuk mencari alamat
                // Geocoder constructor bisa membutuhkan Locale. Menggunakan Locale.getDefault(). OK.
                val geocoder = Geocoder(context, Locale.getDefault())
                val addressList: List<Address>?

                try {
                    // Metode getFromLocationName bisa blocking, pastikan dijalankan di Dispatchers.IO (sudah di withContext).
                    // Mencari hingga 3 hasil teratas.
                    addressList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Untuk API 33+, gunakan metode baru Geocoder yang asinkron.
                        // getFromLocationName(address, maxResults) mengembalikan List<Address>.
                        // Ini adalah metode blocking di API 33+ saat dipanggil dari background thread.
                         geocoder.getFromLocationName(address, 3) // OK
                    } else {
                        // Untuk API < 33, gunakan metode lama blocking yang deprecated.
                        @Suppress("DEPRECATION")
                         geocoder.getFromLocationName(address, 3) // OK
                    }


                    // Memproses hasil dari Geocoder
                    addressList?.let {
                        if (it.size >= 1) { // Jika ada 1 atau lebih hasil
                            // Kita asumsikan hasil pertama paling relevan.
                            // Opsi lain: jika size > 1, bisa kembalikan daftar pilihan ke user (lebih kompleks).
                            // Saat ini, hanya mengambil hasil pertama dan menyelesaikannya.
                            val firstAddress = it[0]
                            trySend(SearchProgress.Complete(firstAddress.latitude, firstAddress.longitude)) // Mengirim state Complete dengan koordinat hasil pertama. BAGUS!
                        } else { // addressList is empty (size == 0)
                            // Jika tidak ada hasil sama sekali.
                             trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Mengirim state Fail. BAGUS!
                        }
                    } ?: run { // addressList is null (jarang terjadi tapi possible)
                        // Jika hasil dari Geocoder null.
                         trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Mengirim state Fail. BAGUS!
                    }
                } catch (io: IOException) {
                    // Menangani error I/O, misal tidak ada koneksi internet atau Geocoder service tidak tersedia.
                    // Menggunakan string resources dari context. BAGUS!
                    trySend(SearchProgress.Fail(context.getString(R.string.no_internet))) // Mengirim state Fail. BAGUS!
                } catch (e: Exception) {
                    // Menangani error lain yang mungkin terjadi selama proses geocoding.
                    trySend(SearchProgress.Fail("Terjadi kesalahan saat mencari alamat: ${e.message}")) // Mengirim state Fail. BAGUS!
                }
            }
        }
        // awaitClose akan dipanggil saat Flow di-collect dibatalkan dari luar (misal Activity/Coroutine Scope onDestroy/Cancelled).
        // Ini penting untuk cleanup sumber daya atau membatalkan operasi yang sedang berjalan.
        awaitClose {
             // Logic cleanup jika diperlukan.
             // Dalam kasus Geocoder blocking, pembatalan withContext(Dispatchers.IO) sudah cukup.
             // Jika menggunakan API yang punya listener/callback sendiri, perlu unregister/cancel di sini.
             this.cancel() // Membatalkan scope coroutine callbackFlow. BAGUS!
        }
    }

    /**
     * Melakukan proses reverse geocoding (mengubah koordinat jadi alamat).
     * Fungsi ini adalah suspend function. Menggunakan Geocoder.
     *
     * @param lat Latitude (Double).
     * @param lon Longitude (Double).
     * @return String alamat atau pesan error jika gagal.
     */
    suspend fun getAddressFromLatLng(lat: Double, lon: Double): String {
         var result = context.getString(R.string.address_not_found) // Default result

         // Menjalankan operasi blocking (Geocoder) di Dispatchers.IO
         withContext(Dispatchers.IO) {
             val geocoder = Geocoder(context, Locale.getDefault()) // Menggunakan context dan Locale. OK.
             val addressList: List<Address>?

             try {
                  // Metode getFromLocation bisa blocking, pastikan dijalankan di Dispatchers.IO
                 addressList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      // Untuk API 33+, gunakan metode baru Geocoder yang asinkron.
                      geocoder.getFromLocation(lat, lon, 1) // Hanya butuh 1 hasil
                 } else {
                      // Untuk API < 33, gunakan metode lama blocking yang deprecated.
                     @Suppress("DEPRECATION")
                      geocoder.getFromLocation(lat, lon, 1) // Hanya butuh 1 hasil
                 }

                 addressList?.let {
                      if (it.isNotEmpty()) {
                           result = it[0].getAddressLine(0) ?: context.getString(R.string.address_not_found) // Ambil alamat lengkap baris pertama. BAGUS!
                      }
                 }
             } catch (io: IOException) {
                 // Menangani error I/O, misal tidak ada koneksi internet atau Geocoder service tidak tersedia
                 result = context.getString(R.string.no_internet) // Menggunakan string resources. BAGUS!
             } catch (e: Exception) {
                 // Menangani error lain
                 result = "Error: ${e.message}"
             }
         }
         return result // Mengembalikan hasil alamat
     }


    // Fungsi utility isNetworkConnected() sudah dipindah ke NetworkUtils.kt
    // Jadi tidak ada di sini. Panggil NetworkUtils.isNetworkConnected(context) atau cek di ViewModel.
    // fun isNetworkConnected(): Boolean { ... }

}
