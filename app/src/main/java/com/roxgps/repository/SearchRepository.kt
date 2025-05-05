package com.roxgps.repository

// =====================================================================
// Import Library SearchRepository
// =====================================================================

import android.content.Context // Untuk Context Aplikasi (dibutuhkan Geocoder)
import android.location.Geocoder // Untuk Geocoder Android
import android.location.Address // Untuk hasil dari Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext // Qualifier untuk Context Aplikasi
import com.roxgps.helper.SearchProgress // Import Sealed Class SearchProgress
import com.roxgps.helper.SearchProgress.SearchResultItem // Import Data Class untuk hasil parsial
import kotlinx.coroutines.Dispatchers // Untuk Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow // Untuk StateFlow
import kotlinx.coroutines.flow.StateFlow // Untuk StateFlow
import kotlinx.coroutines.flow.asStateFlow // Untuk expose StateFlow
import kotlinx.coroutines.withContext // Untuk withContext
import timber.log.Timber // Untuk logging
import java.io.IOException // Untuk menangani error I/O (jaringan/Geocoder)
import java.util.Locale // Untuk Locale Geocoder
import javax.inject.Inject // Untuk Dependency Injection
import javax.inject.Singleton // Untuk menandai Repository sebagai Singleton

// =====================================================================
// Repository untuk Mengelola Proses Pencarian Alamat (Geocoding & Reverse Geocoding)
// Tanggung jawab: Berinteraksi dengan Geocoder/API, mengelola state SearchProgress.
// ViewModel akan menggunakan Repository ini.
// =====================================================================
@Singleton // Tandai Repository sebagai Singleton karena mengelola Geocoder dan state bersama
class SearchRepository @Inject constructor(
    @ApplicationContext private val context: Context // Inject Application Context (dibutuhkan Geocoder)
    // TODO: Jika pakai API eksternal, inject API Service/Client di sini
    // private val searchApiService: SearchApiService
) {

    // =====================================================================
    // State yang Diekspos ke ViewModel/UI
    // Menginformasikan status proses search saat ini.
    // =====================================================================
    private val _searchState = MutableStateFlow<SearchProgress>(SearchProgress.Idle)
    val searchState: StateFlow<SearchProgress> = _searchState.asStateFlow()

    // =====================================================================
    // Dependencies Internal (Geocoder atau API Client)
    // Diinisialisasi di sini atau di init block.
    // =====================================================================
    // Menggunakan Geocoder Android sebagai contoh.
    // Geocoder bisa blocking, jadi panggil method-nya di Dispatchers.IO
    private val geocoder = Geocoder(context, Locale.getDefault()) // Inisialisasi Geocoder

    // =====================================================================
    // Metode yang Dipanggil dari ViewModel
    // Menerima input, menjalankan logic search/geocoding, mengupdate searchState.
    // =====================================================================

    /**
     * Performs a text search (geocoding) for a given address query.
     * Updates the [searchState] StateFlow with the result (Progress, Complete,
     * PartialResult, NoResultFound, Fail).
     *
     * @param query The address string to search for.
     */
    // Method ini suspend karena operasi Geocoder bisa blocking, jalankan di Coroutine.
    suspend fun searchAddress(query: String) {
        if (query.isBlank()) {
            // Jika query kosong, reset state ke Idle
            _searchState.emit(SearchProgress.Idle)
            Timber.d("Search query is blank, resetting state.")
            return
        }

        // Emit state Progress sebelum memulai pencarian
        _searchState.emit(SearchProgress.Progress)
        Timber.d("Starting search for query: $query")

        try {
            // Lakukan pencarian menggunakan Geocoder di Dispatchers.IO
            // getFromLocationName bisa melempar IOException (masalah jaringan/service)
            val results: List<Address> = withContext(Dispatchers.IO) {
                // Geocoder.getFromLocationName bisa null di API level tertentu atau saat error
                @Suppress("DEPRECATION") // getFromLocationName deprecated, pakai Geocoder.getFromLocationName(String, int, Geocoder.GeocodeListener) di API 33+
                geocoder.getFromLocationName(query, 5) // Ambil maksimal 5 hasil sebagai contoh
            } ?: emptyList() // Pastikan hasilnya bukan null


            Timber.d("Search found ${results.size} results.")

            // Proses hasil pencarian
            when {
                results.isEmpty() -> {
                    // Tidak ada hasil ditemukan
                    _searchState.emit(SearchProgress.NoResultFound)
                    Timber.d("Search completed, no results found.")
                }
                results.size == 1 -> {
                    // Ditemukan 1 hasil yang dianggap definitif
                    val firstResult = results[0]
                    if (firstResult.hasLatitude() && firstResult.hasLongitude()) {
                        _searchState.emit(SearchProgress.Complete(firstResult.latitude, firstResult.longitude))
                        Timber.d("Search completed, single definitive result: ${firstResult.latitude}, ${firstResult.longitude}")
                    } else {
                         // Hasil tunggal tapi tidak punya koordinat
                        _searchState.emit(SearchProgress.NoResultFound) // Atau Fail? Tergantung kebutuhan.
                         Timber.d("Search completed, single result found but no coordinates.")
                    }
                }
                else -> {
                    // Ditemukan lebih dari 1 hasil (hasil parsial/pilihan)
                    val partialItems = results.mapNotNull { address ->
                        // Konversi Address Geocoder ke SearchResultItem kita
                        if (address.hasLatitude() && address.hasLongitude()) {
                            SearchResultItem(
                                address = address.getAddressLine(0) ?: address.featureName ?: "Unknown Address", // Ambil alamat format atau nama fitur
                                lat = address.latitude,
                                lon = address.longitude
                            )
                        } else {
                           null // Abaikan hasil yang tidak punya koordinat
                        }
                    }
                    if (partialItems.isNotEmpty()) {
                        _searchState.emit(SearchProgress.PartialResult(partialItems))
                        Timber.d("Search completed, multiple results found: ${partialItems.size} valid items.")
                    } else {
                         // Semua hasil lebih dari 1 tapi tidak ada yang punya koordinat
                         _searchState.emit(SearchProgress.NoResultFound)
                         Timber.d("Search completed, multiple results found but none with coordinates.")
                    }
                }
            }

        } catch (e: IOException) {
            // Menangani error I/O (jaringan mati, layanan Geocoder tidak tersedia, dll.)
            Timber.e(e, "Geocoder search failed due to IO error.")
            _searchState.emit(SearchProgress.Fail("Geocoder service not available or network error."))
        } catch (e: Exception) {
             // Menangani error lain selama proses search
             Timber.e(e, "An unexpected error occurred during search.")
            _searchState.emit(SearchProgress.Fail("An unexpected error occurred: ${e.message}"))
        }
         // State Idle TIDAK di-emit di sini. State akan tetap pada hasil terakhir (Complete, Partial, NoResult, Fail)
         // sampai pencarian baru dimulai (emit Progress) atau secara eksternal direset ke Idle.
    }

    /**
     * Performs a reverse geocoding lookup for given latitude and longitude.
     * Updates the [searchState] StateFlow with the result (Progress, Complete,
     * PartialResult, NoResultFound, Fail). Note: Reverse geocoding usually
     * returns multiple possible addresses for a single coordinate.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     */
    // Method ini suspend karena operasi Geocoder bisa blocking, jalankan di Coroutine.
    suspend fun getAddressFromLatLng(lat: Double, lon: Double) {
        // Emit state Progress sebelum memulai pencarian
        _searchState.emit(SearchProgress.Progress)
        Timber.d("Starting reverse geocoding for: Lat=$lat, Lon=$lon")

        try {
            // Lakukan reverse geocoding menggunakan Geocoder di Dispatchers.IO
            // getFromLocation bisa melempar IOException
             val results: List<Address> = withContext(Dispatchers.IO) {
                // Geocoder.getFromLocation bisa null di API level tertentu atau saat error
                @Suppress("DEPRECATION") // getFromLocation deprecated, pakai Geocoder.getFromLocation(double, double, int, Geocoder.GeocodeListener) di API 33+
                 geocoder.getFromLocation(lat, lon, 1) // Ambil 1 hasil paling mungkin sebagai contoh
             } ?: emptyList() // Pastikan hasilnya bukan null

            Timber.d("Reverse geocoding found ${results.size} results.")

            // Proses hasil reverse geocoding (biasanya ambil hasil pertama)
             when {
                results.isEmpty() -> {
                    // Tidak ada alamat ditemukan untuk koordinat ini
                    _searchState.emit(SearchProgress.NoResultFound)
                    Timber.d("Reverse geocoding completed, no address found.")
                }
                else -> {
                     // Ditemukan setidaknya satu alamat
                     val firstResult = results[0]
                     val addressString = firstResult.getAddressLine(0) // Ambil alamat format pertama
                     // Reverse geocoding biasanya menghasilkan 1 hasil "terbaik"
                     // Kita bisa langsung anggap ini Complete, atau kalau mau semua hasil ditampilkan, pakai PartialResult
                     _searchState.emit(SearchProgress.Complete(lat, lon)) // Emit Complete dengan koordinat awal
                     // Optional: Simpan addressString di StateFlow lain jika UI butuh teks alamatnya terpisah
                     // private val _currentAddress = MutableStateFlow<String?>(null)
                     // val currentAddress: StateFlow<String?> = _currentAddress.asStateFlow()
                     // _currentAddress.emit(addressString) // Emit alamat ke StateFlow terpisah
                     Timber.d("Reverse geocoding completed, address: $addressString")
                }
            }

        } catch (e: IOException) {
            // Menangani error I/O
            Timber.e(e, "Geocoder reverse geocoding failed due to IO error.")
            _searchState.emit(SearchProgress.Fail("Geocoder service not available or network error."))
            // Optional: Emit alamat error ke StateFlow alamat
            // _currentAddress.emit("Failed to get address")
        } catch (e: Exception) {
            // Menangani error lain
            Timber.e(e, "An unexpected error occurred during reverse geocoding.")
            _searchState.emit(SearchProgress.Fail("An unexpected error occurred: ${e.message}"))
             // Optional: Emit alamat error
            // _currentAddress.emit("Failed to get address")
        }
         // State Idle TIDAK di-emit di sini.
    }

    /**
     * Resets the search state back to Idle.
     * Called when the UI should clear the search status display.
     */
    suspend fun resetSearchState() {
        Timber.d("Resetting search state to Idle")
        _searchState.emit(SearchProgress.Idle)
        // Optional: Reset StateFlow alamat juga jika ada
        // _currentAddress.emit(null)
    }

    // =====================================================================
    // Initialization (Optional)
    // =====================================================================
    init {
        Timber.d("SearchRepository created")
        // Tidak ada inisialisasi khusus yang blocking di sini.
    }

    // =====================================================================
    // Cleanup Repository (Optional, untuk Singleton)
    // =====================================================================
    // Jarang dibutuhkan secara eksplisit untuk Repository sederhana
    // @PreDestroy // Anotasi ini dari Javax, perlu dependensi
    // fun cleanup() {
    //    Timber.d("SearchRepository cleanup")
    //    // Lepaskan resource jika ada yang butuh dilepas saat aplikasi mati
    // }
}
