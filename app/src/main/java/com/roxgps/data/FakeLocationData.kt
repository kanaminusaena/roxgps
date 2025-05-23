package com.roxgps.data

import android.location.Location
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
    val provider: String? = null, // Nama provider lokasi (misal "gps", "network", "fused")
    val isStarted: Boolean = false, // <<< PROPERTI PENTING UNTUK STATUS FAKING
    val isRandomPositionEnabled: Boolean = false, // Apakah random position aktif?
    // Kita sudah punya 'accuracy' di properti lokasi, gunakan itu sebagai accuracy setting.
    // Jika accuracy di sini hanya untuk setting, namanya mungkin perlu diganti (misal targetAccuracy),
    // tapi mari kita pakai properti accuracy yang sudah ada untuk kesederhanaan.

    // TODO: Tambahkan setting lain dari Xshare yang kamu butuhkan di hook.
    val randomRange: Int = 50, // Range untuk random position (meter) (Menggantikan dari Xshare)
    val updateIntervalMs: Long = 80, // Interval update di hook (ms) (Menggantikan dari Xshare)
    // val ignorePackageList: List<String>? = null // Opsi: kirim ignore list jika perlu di hook
    val baseLocation: Location? = null,    // Tambahkan default value null
    val desiredSpeed: Float = 0.0f         // Tambahkan default value 0.0f

) : Parcelable {
    // Kotlin-Parcelize akan meng-generate implementasi Parcelable

    // Metode helper opsional untuk mengonversi FakeLocationData ke objek android.location.Location
    // Ini berguna di sisi Module Xposed.
    // Metode helper untuk mengonversi FakeLocationData ke objek android.location.Location
    // Ini berguna di sisi Module Xposed setelah menerima objek FakeLocationData via AIDL.
    fun toAndroidLocation(): Location {
        val location = Location(provider) // Set provider di constructor
        location.latitude = latitude
        location.longitude = longitude
        location.accuracy = accuracy
        location.speed = speed
        location.bearing = bearing
        location.altitude = altitude
        location.time = time // Set epoch time

        // === Gunakan LocationCompat untuk set elapsedRealtimeNanos secara kompatibel ===
        // Ini lebih aman daripada reflection atau try-catch NoSuchMethodError
        //LocationCompat.setElapsedRealtimeNanos(location, elapsedRealtimeNanos) // <<< Menggunakan LocationCompat
        location.elapsedRealtimeNanos = elapsedRealtimeNanos
        // ============================================================================

        // Properties lain seperti bearingAccuracyDegrees, verticalAccuracyMeters,
        // speedAccuracyMetersPerSecond juga bisa disalin jika ada di hook class dan API level memungkinkan.
        // Misalnya jika FakeLocationData punya val verticalAccuracy: Float = 0f
        // LocationCompat.setVerticalAccuracyMeters(location, verticalAccuracy)

        // Catatan: Properti 'isStarted' dari FakeLocationData TIDAK disalin ke objek android.location.Location.
        // Properti isStarted digunakan oleh logika Xposed Hook untuk memutuskan APAPUN
        // objek Location hasil konversi ini akan disuntikkan atau tidak.


        return location
    }
}