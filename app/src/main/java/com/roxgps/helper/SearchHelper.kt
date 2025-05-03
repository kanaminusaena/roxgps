package com.roxgps.helper // Sesuaikan dengan package utility atau fitur search kamu

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build // Perlu untuk cek versi Geocoder
import com.roxgps.R // Import R untuk string resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay // Jika delay masih diperlukan
import kotlinx.coroutines.flow.callbackFlow // Untuk membuat Flow dari operasi callback/blocking
import kotlinx.coroutines.withContext // Untuk berpindah thread (Dispatcher)
import java.io.IOException
import java.util.Locale // Perlu untuk Geocoder
import java.util.regex.Matcher
import java.util.regex.Pattern

// Sealed class untuk merepresentasikan status proses search
// Dipindahkan dari BaseMapActivity
sealed class SearchProgress {
    object Progress : SearchProgress() // Menandakan proses sedang berjalan
    data class Complete(val lat: Double , val lon : Double) : SearchProgress() // Menandakan proses selesai sukses dengan hasil koordinat
    data class Fail(val error: String?) : SearchProgress() // Menandakan proses gagal dengan pesan error
}

// Helper class untuk mengelola logika search alamat/koordinat
class SearchHelper(private val context: Context) { // Butuh Context untuk akses Geocoder dan Resources

    // Fungsi untuk melakukan proses search (mengubah teks jadi koordinat)
    // Fungsi ini adalah suspend function dan mengembalikan Flow<SearchProgress>
    suspend fun getSearchAddress(address: String) = callbackFlow {
        // Mengirim status Progress ke Flow, menandakan search dimulai
        trySend(SearchProgress.Progress)

        // Menjalankan operasi blocking (Geocoder) di Dispatchers.IO
        withContext(Dispatchers.IO){
            // Mengecek apakah input address berupa format koordinat lat,lon
            val matcher: Matcher =
                Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)

            if (matcher.matches()){
                // Jika input sesuai format koordinat
                // delay(3000) // Delay asli 3 detik, bisa dipertimbangkan apakah benar-benar perlu

                try {
                    // Parsing latitude dan longitude dari input
                    val parts = matcher.group().split(",")
                    val lat = parts[0].trim().toDouble() // Ambil bagian pertama (latitude), hilangkan spasi, ubah ke Double
                    val lon = parts[1].trim().toDouble() // Ambil bagian kedua (longitude), hilangkan spasi, ubah ke Double
                    // Mengirim hasil sukses (koordinat) ke Flow
                    trySend(SearchProgress.Complete(lat, lon))
                } catch (e: NumberFormatException) {
                    // Jika parsing Double gagal, laporkan error
                     trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Gunakan string resources
                } catch (e: Exception) {
                     // Tangani error lain saat parsing
                    trySend(SearchProgress.Fail("Terjadi kesalahan saat memproses koordinat: ${e.message}"))
                }

            } else {
                // Jika input BUKAN format koordinat, gunakan Geocoder untuk mencari alamat
                // Geocoder constructor bisa membutuhkan Locale
                val geocoder = Geocoder(context, Locale.getDefault())
                val addressList: List<Address>?

                try {
                    // Metode getFromLocationName bisa blocking, pastikan dijalankan di Dispatchers.IO
                    // Mencari hingga 3 hasil teratas
                    addressList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                         // Untuk API 33+, gunakan metode baru asinkron yang dikonversi jadi blocking
                         geocoder.getFromLocationName(address,3)
                    } else {
                         // Untuk API < 33, gunakan metode lama blocking
                        @Suppress("DEPRECATION")
                         geocoder.getFromLocationName(address,3)
                    }


                    // Memproses hasil dari Geocoder
                    addressList?.let {
                        if (it.size == 1){
                            // Jika hanya ada 1 hasil, anggap itu yang paling relevan
                           trySend(SearchProgress.Complete(addressList[0].latitude, addressList[0].longitude))
                        } else if (it.size > 1) {
                            // Jika ada lebih dari 1 hasil, laporkan sebagai "tidak ditemukan hasil tunggal" atau bisa tambahkan logic pilih hasil
                            trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Gunakan string resources
                        }
                        else { // addressList is empty (size == 0)
                            trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Gunakan string resources
                        }
                    } ?: run { // addressList is null (jarang terjadi tapi possible)
                         trySend(SearchProgress.Fail(context.getString(R.string.address_not_found))) // Gunakan string resources
                    }
                } catch (io : IOException){
                    // Menangani error I/O, misal tidak ada koneksi internet atau Geocoder service tidak tersedia
                    trySend(SearchProgress.Fail(context.getString(R.string.no_internet))) // Gunakan string resources
                } catch (e: Exception) {
                     // Menangani error lain yang mungkin terjadi selama proses geocoding
                     trySend(SearchProgress.Fail("Terjadi kesalahan saat mencari alamat: ${e.message}"))
                }
            }
        }
        // awaitClose akan dipanggil saat Flow di-collect dibatalkan
        awaitClose { this.cancel() } // Membatalkan scope coroutine jika Flow dibatalkan dari luar
    }

    // Fungsi utility isNetworkConnected() sudah dipindah ke NetworkUtils.kt
    // Jadi tidak ada di sini
    // fun isNetworkConnected(): Boolean { ... }
}
