// File: com/roxgps/helper/SearchProgress.kt
package com.roxgps.helper

// Import yang dibutuhkan
// import android.location.Address // Jika mau pakai Address Android
// import org.maplibre.android.geometry.LatLng // Jika mau pakai LatLng MapLibre
// import com.google.android.gms.maps.model.LatLng // Jika mau pakai LatLng Google

/**
 * Sealed class representing the possible states of a search operation.
 * Used by ViewModel to expose search status to the UI.
 */
sealed class SearchProgress {
    /**
     * Initial state when no search is in progress.
     */
    object Idle : SearchProgress()

    /**
     * State indicating a search is currently in progress.
     */
    object Progress : SearchProgress()

    /**
     * State indicating a search has completed successfully with a definitive result.
     *
     * @param lat The latitude of the search result.
     * @param lon The longitude of the search result.
     */
    data class Complete(val lat: Double, val lon: Double) : SearchProgress()

    /**
     * State indicating a search has failed due to an error during the process.
     *
     * @param error An optional error message describing the failure.
     */
    data class Fail(val error: String? = null) : SearchProgress()

    // =====================================================================
    // State BARU: Ditambahkan sesuai permintaan
    // =====================================================================

    /**
     * State indicating a search has completed but found no results matching the query.
     */
    object NoResultFound : SearchProgress() // <-- State BARU: Tidak ada hasil ditemukan

    /**
     * State indicating a search has returned multiple potential results (partial).
     * The UI might need to display these options to the user for selection.
     *
     * @param results A list of potential search results.
     */
    data class PartialResult(val results: List<SearchResultItem>) : SearchProgress() // <-- State BARU: Hasil parsial/banyak opsi


    // =====================================================================
    // Data Class Pembantu (Digunakan oleh PartialResult)
    // Didefinisikan di dalam file yang sama karena hanya dipakai di sini.
    // =====================================================================
    /**
     * Data class representing a single item in a list of potential search results.
     * Used within the [PartialResult] state.
     */
    data class SearchResultItem(
        val address: String, // Alamat hasil parsial
        val lat: Double, // Latitude hasil parsial
        val lon: Double // Longitude hasil parsial
        // Tambahkan properti lain jika perlu (misal: placeId, tipe tempat)
    )
}
