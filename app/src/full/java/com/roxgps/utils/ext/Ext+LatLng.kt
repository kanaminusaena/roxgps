package com.roxgps.utils.ext // Package tetap sama

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng // <-- Mengimpor LatLng Google Maps
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.*

// Extension function getAddress untuk LatLng Google Maps (Flavor FULL)
// Ditempatkan di source set 'full'
suspend fun LatLng.getAddress(context: Context) = callbackFlow { // Extension di atas LatLng Google Maps
    withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            // LatLng Google Maps punya properti latitude dan longitude (tipe Double).
            val addresses = geocoder.getFromLocation(latitude, longitude, 1) // Menggunakan properti latitude/longitude dari LatLng Google Maps

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
