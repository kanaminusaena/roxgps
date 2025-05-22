package com.roxgps.room

import android.location.Location
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date


// =====================================================================
// LocationEntity (Entity Room Database untuk Lokasi Background)
// =====================================================================

/**
 * Room Entity merepresentasikan hook lokasi real yang disimpan di database lokal.
 * Setiap instance Entity ini adalah sebuah baris di tabel database 'locations'.
 */
@Entity(tableName = "locations") // Nama tabel di database
@TypeConverters(DateConverters::class) // Gunakan TypeConverter untuk Date
data class LocationEntity(
    // Primary Key: ID unik untuk setiap baris lokasi. Auto-generate oleh Room.
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Kolom-kolom untuk menyimpan hook lokasi
    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float = 0.0f, // Akurasi lokasi (opsional, beri default)

    @ColumnInfo(name = "speed")
    val speed: Float = 0.0f, // Kecepatan (opsional, beri default)

    @ColumnInfo(name = "bearing")
    val bearing: Float = 0.0f, // Arah hadap (opsional, beri default)

    @ColumnInfo(name = "altitude")
    val altitude: Double = 0.0, // Ketinggian (opsional, beri default)

    @ColumnInfo(name = "provider")
    val provider: String? = null, // Provider lokasi (GPS, network, dll.) (opsional, bisa null)

    @ColumnInfo(name = "timestamp")
    val timestamp: Date // Timestamp lokasi (kapan lokasi ini diterima)

    // TODO: Tambahkan kolom lain jika ada hook lokasi tambahan yang ingin disimpan.
) {
    // === Helper Method untuk Konversi dari android.location.Location ===
    // Metode statis atau dalam companion object untuk memudahkan konversi
    companion object {
        fun fromLocation(location: Location): LocationEntity {
            return LocationEntity(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                bearing = location.bearing,
                altitude = location.altitude,
                provider = location.provider,
                // Ambil waktu dari objek Location.
                // Waktu di objek Location adalah System.currentTimeMillis() saat lokasi diterima.
                timestamp = Date(location.time)
            )
        }
    }
}
