package com.roxgps.utils.ext // Package tetap sama

import android.content.Context
import android.location.Geocoder
import org.maplibre.android.geometry.LatLng // <-- Mengimpor LatLng MapLibre
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.*

// Extension function getAddress untuk LatLng MapLibre (Flavor FOSS)
// Ditempatkan di source set 'foss'
suspend fun LatLng.getAddress(context: Context) = callbackFlow { // Extension di atas LatLng MapLibre
    withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            // LatLng MapLibre punya properti latitude dan longitude (tipe Double).
            val addresses = geocoder.getFromLocation(latitude, longitude, 1) // Menggunakan properti latitude/longitude dari LatLng MapLibre

            val addressString = addresses?.firstOrNull()?.let { address ->
                val fullAddress = address.getAddressLine(0)
                // Logika format alamat
                 val addressParts = fullAddress.split(",").map { it.trim() }

                if (addressParts.size > 1) {
                    val mainAddress = addressParts[0]
                    val remainingAddress = fullAddress.substringAfter(", ", "")
                    "$mainAddress\n$remainingAddress"
                } else {
                    fullAddress
                }
            } ?: "Alamat tidak ditemukan" // Gunakan string literal atau dari resources jika ada

            trySend(addressString) // Kirim alamat ke flow
        } catch (e: Exception) {
            trySend("Gagal mendapatkan alamat: ${e.message}") // Kirim pesan error
            e.printStackTrace()
        }
    }

    awaitClose { /* Tidak ada resource spesifik yang perlu dibersihkan di sini dari callbackFlow*/ }
    // Jika ada listener atau resource lain yang didaftarkan di callbackFlow, bersihkan di sini
}
