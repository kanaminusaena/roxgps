// Di RoxGPS App: app/src/main/java/com/roxgps/data/FakeLocationData.kt
package com.roxgps.data // Package data class Parcelable (harus sama dengan AIDL)

import android.location.Location // Import Location jika perlu di constructor/method
import android.os.Parcelable // Interface Parcelable
import kotlinx.parcelize.Parcelize // Anotasi @Parcelize

// =====================================================================
// Data Class untuk Lokasi Palsu (Parcelable untuk AIDL)
// Digunakan untuk mengirim semua data lokasi palsu via AIDL.
// =====================================================================

@Parcelize // Menggunakan Kotlin-Parcelize plugin untuk implementasi Parcelable otomatis
data class FakeLocationData(
    val latitude: Double = 0.0, // Garis Lintang
    val longitude: Double = 0.0, // Garis Bujur
    val accuracy: Float = 1.0f, // Akurasi dalam meter (nilai kecil = akurat)
    val speed: Float = 0.0f, // Kecepatan dalam m/s
    val bearing: Float = 0.0f, // Arah hadap dalam derajat
    val altitude: Double = 0.0, // Ketinggian di atas permukaan laut
    val time: Long = System.currentTimeMillis(), // Waktu saat lokasi "diambil" (epoch time)
    val elapsedRealtimeNanos: Long = System.nanoTime(), // Waktu elapsed real time (nanoseconds)
    val provider: String = "gps" // Nama provider lokasi (misal "gps", "network", "fused")
) : Parcelable {
    // Kotlin-Parcelize akan meng-generate implementasi Parcelable

    // Metode helper opsional untuk mengonversi FakeLocationData ke objek android.location.Location
    // Ini berguna di sisi Module Xposed.
    fun toAndroidLocation(): Location {
        val location = Location(provider)
        location.latitude = latitude
        location.longitude = longitude
        location.accuracy = accuracy
        location.speed = speed
        location.bearing = bearing
        location.altitude = altitude
        location.time = time
        // Perhatikan: elapsedRealtimeNanos butuh set method yang berbeda di API tertentu
        // gunakan LocationCompat atau refleksikan jika perlu dukungan API lama
        // Pada API 17+, setElapsedRealtimeNanos(elapsedRealtimeNanos) tersedia
        // Pada API 24+, setElapsedRealtimeUnchecked(elapsedRealtimeNanos) tersedia
        // Untuk kompatibilitas, bisa set via reflection atau gunakan LocationCompat.setElapsedRealtimeNanos
         try {
             location.elapsedRealtimeNanos = elapsedRealtimeNanos // Cocok untuk API modern
         } catch (e: NoSuchMethodError) {
             // Handle jika method ini tidak ada (API lama)
             Timber.w("Method setElapsedRealtimeNanos not available, skipping elapsedRealtimeNanos for fake location.")
             // Reflection alternative (lebih kompleks):
             // try {
             //    val method = Location::class.java.getMethod("setElapsedRealtimeNanos", Long::class.javaPrimitiveType)
             //    method.invoke(location, elapsedRealtimeNanos)
             // } catch (re: ReflectiveOperationException) { Timber.e(re, "Reflection failed for setElapsedRealtimeNanos") }
         }

        return location
    }
}
