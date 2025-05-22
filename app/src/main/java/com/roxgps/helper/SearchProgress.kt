package com.roxgps.helper

// Atau package lain yang sesuai tempat file ini berada

// =====================================================================
// Sealed class SearchProgress (Status Proses Search)
// =====================================================================

/**
 * Sealed class untuk merepresentasikan status proses pencarian (forward geocoding).
 * Digunakan oleh SearchRepository untuk melaporkan status ke ViewModel.
 * Mendefinisikan semua kemungkinan state dari proses search.
 */
sealed class SearchProgress {
    // Status idle: Tidak ada proses search yang sedang berjalan. Ini adalah state default.
    object Idle : SearchProgress()

    // Status progress: Proses search sedang berjalan.
    object Progress : SearchProgress()

    // Status complete: Proses search forward geocoding selesai dan menemukan hasil tunggal.
    // Membawa koordinat hasil (latitude dan longitude).
    data class Complete(val lat: Double, val lon: Double) : SearchProgress()
    // Catatan: Di SearchRepository, kamu memproses hasil tunggal dan memasukkan koordinatnya ke sini.

    // Status partial result: Proses search forward geocoding selesai dan menemukan lebih dari satu hasil.
    // Membawa daftar item hasil search (SearchResultItem). ViewModel/UI bisa menampilkan daftar ini ke user.
    data class PartialResult(val results: List<SearchResultItem>) : SearchProgress()

    // Status no result found: Proses search forward geocoding selesai tetapi tidak menemukan hasil sama sekali.
    object NoResultFound : SearchProgress()

    // Status fail: Proses search gagal karena error (misal, tidak ada koneksi internet, Geocoder error).
    // Membawa pesan error jika ada.
    data class Fail(val message: String?) : SearchProgress()

    object Loading : SearchProgress()
    //hook class Complete(val resultLocation: Location) : SearchProgress() // <<< Nama propertinya 'resultLocation', BUKAN 'location'
    data class Error(val message: String?) : SearchProgress()

    // TODO: Tambahkan state lain jika diperlukan (misal, Cancelled).

    // =====================================================================
    // Data class SearchResultItem
    // =====================================================================

    /**
     * Data class untuk merepresentasikan satu item dalam daftar hasil search
     * ketika ada banyak hasil yang ditemukan (PartialResult).
     * Berisi informasi yang cukup untuk ditampilkan di UI.
     */
    data class SearchResultItem(
        val address: String, // Alamat yang diformat untuk ditampilkan (misal, "Jalan Sudirman No. 123, Jakarta")
        val lat: Double,     // Garis Lintang dari hasil search
        val lon: Double      // Garis Bujur dari hasil search
        // TODO: Tambahkan properti lain jika diperlukan (misal, provider, accuracy jika ada)
    )

    // Catatan: Data class SearchResultItem didefinisikan di file yang sama
    // dengan sealed class SearchProgress agar bisa diakses oleh SearchProgress.PartialResult.
}

// Catatan: Data class SearchResultItem bisa juga didefinisikan di luar sealed class SearchProgress,
// tetapi tetap di file yang sama (SearchProgress.kt) atau file terpisah yang diimport,
// selama bisa diakses oleh SearchProgress dan SearchRepository.
// Mendefinisikannya di file yang sama adalah cara yang umum.