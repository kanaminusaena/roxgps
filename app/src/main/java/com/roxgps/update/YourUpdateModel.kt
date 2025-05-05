// File: com/roxgps/update/YourUpdateModel.kt
package com.roxgps.update

// Import yang dibutuhkan (sesuaikan dengan properti yang lo pakai)
// import android.net.Uri // Jika butuh Uri di modelnya
// import java.util.Date // Jika butuh Date atau Timestamp

/**
 * Data class representing information about an available application update.
 * This model is used to pass update details between layers (Repository, ViewModel, UI).
 * Informasi ini biasanya didapat dari sumber update (misal: API server, GitHub release).
 */
data class YourUpdateModel(
    val versionName: String, // Nama versi baru (Contoh: "1.2.3")
    val changelog: String, // Daftar perubahan di versi baru (Contoh: "Fixed bugs, added feature X\nImproved performance")
    val assetUrl: String, // URL untuk mendownload file update (Contoh: "https://yourserver.com/downloads/app-release-v1.2.3.apk")
    val assetName: String, // Nama file update yang akan disimpan (Contoh: "app-release-v1.2.3.apk")
    val isMandatory: Boolean = false, // Status apakah update ini wajib (default false)
    // TODO: Tambahkan properti lain sesuai kebutuhan dari sumber update lo
    // val releaseDate: Long? = null, // Contoh: Timestamp tanggal rilis
    // val fileSize: Long? = null, // Contoh: Ukuran file dalam bytes
    // val downloadCount: Int? = null // Contoh: Jumlah unduhan
)
