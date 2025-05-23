package com.roxgps.utils // Sesuaikan dengan package utility umum di project kamu

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Objek utility untuk fungsi-fungsi terkait jaringan/koneksi
// Menggunakan 'object' agar fungsinya bisa dipanggil langsung tanpa membuat instance kelas
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    private const val EARTH_RADIUS_METERS = 6371000.0
    /**
     * Memeriksa apakah layanan lokasi diaktifkan di perangkat
     * @param context Context untuk mengakses system services
     * @return true jika location service aktif
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Relog.e("Error checking location service status ${e.message}", TAG)
            false
        }
    }

    /**
     * Menghitung posisi berikutnya berdasarkan kecepatan dan interval
     * @param currentLocation Lokasi saat ini
     * @param speedMps Kecepatan dalam meter per detik
     * @param intervalMs Interval update dalam milidetik
     * @return Location object dengan posisi yang baru
     */
    fun calculateNextPosition(
        currentLocation: Location,
        speedMps: Float,
        intervalMs: Long
    ): Location {

        val distanceMeters = speedMps * (intervalMs / 1000.0)
        val bearing = currentLocation.bearing

        val lat1 = Math.toRadians(currentLocation.latitude)
        val lon1 = Math.toRadians(currentLocation.longitude)
        val bearingRad = Math.toRadians(bearing.toDouble())

        val angularDistance = distanceMeters / EARTH_RADIUS_METERS

        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
                    cos(lat1) * sin(angularDistance) * cos(bearingRad)
        )

        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )

        return Location(currentLocation).apply {
            latitude = Math.toDegrees(lat2)
            longitude = Math.toDegrees(lon2)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = System.nanoTime()
        }
    }

    /**
     * Menerapkan offset acak pada lokasi
     * @param location Lokasi asal
     * @param maxRangeMeters Jarak maksimum offset dalam meter
     * @return Location object dengan offset acak
     */
    fun applyRandomOffset(
        location: Location,
        maxRangeMeters: Int
    ): Location {
        val randomDistance = Random.nextDouble(0.0, maxRangeMeters.toDouble())
        val randomBearing = Random.nextDouble(0.0, 360.0)

        val lat1 = Math.toRadians(location.latitude)
        val lon1 = Math.toRadians(location.longitude)
        val bearingRad = Math.toRadians(randomBearing)

        val angularDistance = randomDistance / EARTH_RADIUS_METERS

        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
                    cos(lat1) * sin(angularDistance) * cos(bearingRad)
        )

        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )

        return Location(location).apply {
            latitude = Math.toDegrees(lat2)
            longitude = Math.toDegrees(lon2)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = System.nanoTime()
        }
    }


        /**
         * Memeriksa apakah perangkat saat ini terhubung ke jaringan internet.
         *
         * @param context Context dari aplikasi atau Activity.
         * @return true jika terhubung ke Wi-Fi, Seluler, atau Ethernet, false jika tidak.
         */
        fun isNetworkConnected(context: Context): Boolean {
            // Mendapatkan ConnectivityManager dari sistem service
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Untuk Android Marshmallow (API 23) dan yang lebih baru
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Mendapatkan jaringan aktif saat ini
                val network = connectivityManager.activeNetwork
                    ?: return false // Jika tidak ada jaringan aktif, kembalikan false
                // Mendapatkan kemampuan (capabilities) dari jaringan aktif
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                    ?: return false // Jika tidak ada capabilities, kembalikan false

                // Memeriksa apakah jaringan aktif memiliki salah satu transport (Wi-Fi, Seluler, Ethernet)
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                // Untuk versi Android di bawah Marshmallow (API < 23) - Menggunakan metode deprecated
                @Suppress("DEPRECATION") // Menekan warning untuk metode deprecated
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                    ?: return false // Jika tidak ada info jaringan aktif, kembalikan false
                @Suppress("DEPRECATION") // Menekan warning
                return activeNetworkInfo.isConnected // Mengembalikan status koneksi
            }
        }


        // Bisa tambahkan fungsi utility network lain di sini jika diperlukan
        // fun getNetworkType(context: Context): String { ... }
        // fun getWifiSSID(context: Context): String? { ... }
    }
