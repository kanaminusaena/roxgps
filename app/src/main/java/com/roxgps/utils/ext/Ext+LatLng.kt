package com.roxgps.utils.ext

import android.content.Context
import android.location.Geocoder
import com.roxgps.ui.CustomLatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.*

suspend fun CustomLatLng.getAddress(context: Context) = callbackFlow {
    withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            val addressString = addresses?.firstOrNull()?.let { address ->
                val fullAddress = address.getAddressLine(0)
                val addressParts = fullAddress.split(",").map { it.trim() }

                if (addressParts.size > 1) {
                    val mainAddress = addressParts[0]
                    val remainingAddress = fullAddress.substringAfter(", ", "")
                    "$mainAddress\n$remainingAddress"
                } else {
                    fullAddress
                }
            } ?: "Alamat tidak ditemukan"

            trySend(addressString) // Kirim alamat ke flow
        } catch (e: Exception) {
            trySend("Gagal mendapatkan alamat: ${e.message}") // Kirim pesan error
            e.printStackTrace()
        }
    }

    awaitClose { this.cancel() } // Bersihkan resource
}

/*
suspend fun CustomLatLng.getAddress(context: Context) = callbackFlow {
    withContext(Dispatchers.IO) {
        val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
        val sb = StringBuilder()
        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0].getAddressLine(0)
            val strs = address.split(",").toTypedArray()
            if (strs.size > 1) {
                sb.append(strs[0])
                val index = address.indexOf(",") + 2
                if (index > 1 && address.length > index) {
                    sb.append("\n").append(address.substring(index))
                }
            } else {
                sb.append(address)
            }
        }
        trySend(sb.toString())
    }
    awaitClose { this.cancel() }
}
*/