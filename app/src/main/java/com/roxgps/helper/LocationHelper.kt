package com.roxgps.helper // Sesuaikan dengan package utility kamu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

// Interface buat komunikasi balik ke Activity yang menggunakan LocationHelper
// Activity yang menggunakan helper ini harus mengimplementasikan interface ini
interface LocationListener {
    // Dipanggil saat lokasi berhasil didapatkan
    fun onLocationResult(location: Location)
    // Dipanggil saat terjadi error terkait lokasi (misal: layanan lokasi mati, gagal dapat lokasi)
    fun onLocationError(message: String)
    // Dipanggil saat izin lokasi berhasil diberikan setelah diminta
    fun onPermissionGranted()
    // Dipanggil saat izin lokasi ditolak oleh pengguna
    fun onPermissionDenied()
}

// Helper class untuk mengelola semua logika terkait Lokasi dan Permission Lokasi
class LocationHelper(
    private val activity: ComponentActivity, // Perlu ComponentActivity untuk Activity Result APIs
    private val listener: LocationListener // Listener untuk mengirim data dan status ke Activity
) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // ID Permission tidak diperlukan lagi secara publik karena menggunakan ActivityResultLauncher

    // Activity Result Launcher untuk menangani hasil permintaan izin lokasi secara modern
    private val requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Mengecek hasil dari izin ACCESS_FINE_LOCATION dan ACCESS_COARSE_LOCATION
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted && coarseLocationGranted) {
                // Jika kedua izin diberikan
                listener.onPermissionGranted() // Beri tahu listener bahwa izin berhasil
                // Opsional: Langsung coba ambil lokasi setelah izin diberikan
                 // requestLastKnownLocation()
            } else {
                // Jika ada salah satu atau kedua izin ditolak
                listener.onPermissionDenied() // Beri tahu listener bahwa izin ditolak
                // Di sini bisa menampilkan pesan ke pengguna kenapa izin lokasi penting jika diinginkan
            }
        }

    // Blok init akan dijalankan saat objek LocationHelper dibuat
    init {
        // Menginisialisasi FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
    }

    // Callback dari FusedLocationProviderClient saat update lokasi diterima
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // Mengambil lokasi terakhir dari hasil update
            val lastLocation: Location? = locationResult.lastLocation

            if (lastLocation != null) {
                 // Periksa apakah lokasi valid (bukan 0.0, 0.0) yang kadang bisa terjadi pada mock location atau error lain
                if (lastLocation.latitude != 0.0 || lastLocation.longitude != 0.0) {
                     // Jika lokasi valid, kirim hasilnya ke listener
                     listener.onLocationResult(lastLocation)
                } else {
                     // Jika lokasi terlihat tidak valid, laporkan error
                     listener.onLocationError("Lokasi diterima tetapi datanya tidak valid (0.0, 0.0).")
                }
            } else {
                // Jika tidak ada lokasi yang tersedia dalam update
                listener.onLocationError("Tidak ada lokasi yang tersedia dalam update.")
            }
        }

        // Metode lain dari LocationCallback bisa dioverride jika diperlukan (misal: onLocationAvailability)
    }

    // Memulai proses untuk mendapatkan lokasi terakhir yang diketahui atau meminta update baru
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    fun requestLastKnownLocation() {
        // Langkah 1: Periksa apakah izin lokasi sudah diberikan
        if (!checkLocationPermissions()) {
            // Jika izin belum diberikan, minta izin ke pengguna
            requestLocationPermissions()
            return // Hentikan proses lebih lanjut sampai izin diberikan
        }

        // Langkah 2: Periksa apakah layanan lokasi (GPS/Network) aktif
        if (!isLocationServiceEnabled()) {
            // Jika layanan lokasi tidak aktif, laporkan error ke listener
            listener.onLocationError("Layanan lokasi (GPS atau Jaringan) dinonaktifkan.")
            // Di sini bisa juga memberikan opsi ke pengguna untuk membuka pengaturan lokasi
            return // Hentikan proses
        }

        // Langkah 3: Coba ambil lokasi terakhir yang diketahui
        fusedLocationClient.lastLocation.addOnSuccessListener(activity) { location: Location? ->
             if (location != null) {
                // Jika lokasi terakhir ditemukan, kirim hasilnya ke listener
                listener.onLocationResult(location)
            } else {
                // Jika lokasi terakhir tidak ditemukan, minta update lokasi baru
                requestSingleLocationUpdate()
            }
        }.addOnFailureListener { exception ->
            // Menangani kesalahan jika gagal mendapatkan lokasi terakhir
            listener.onLocationError("Gagal mendapatkan lokasi terakhir: ${exception.message}")
        }
    }

    // Meminta satu kali update lokasi baru dari FusedLocationProviderClient
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    private fun requestSingleLocationUpdate() {
        // Membuat permintaan lokasi
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // Prioritas tinggi, interval 1 detik (meskipun numUpdates = 1)
            .setWaitForAccurateLocation(true) // Tunggu lokasi akurat
            .setMaxUpdates(1) // Hanya perlu 1 update
            .build()

        // Memulai request update lokasi. Penting: Pastikan dipanggil dari Main Thread (biasanya sudah aman jika dari Activity)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper() // Menggunakan Looper dari thread saat ini (Main Thread)
        )
    }

    // Memeriksa apakah layanan lokasi (GPS atau Network provider) aktif di perangkat
    fun isLocationServiceEnabled(): Boolean {
        val locationManager: LocationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // Memeriksa apakah izin lokasi (ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION) sudah diberikan
    fun checkLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Meminta izin lokasi ke pengguna menggunakan Activity Result APIs
    fun requestLocationPermissions() {
        // Meluncurkan permintaan izin untuk ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

     // Menghentikan update lokasi. Penting dipanggil saat tidak lagi membutuhkan update (misal di onPause/onDestroy Activity)
    fun stopLocationUpdates() {
        // Memastikan fusedLocationClient sudah diinisialisasi sebelum mencoba menghapus update
        if (::fusedLocationClient.isInitialized) {
             fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Fungsi lain yang mungkin berguna:
    // fun openLocationSettings() {
    //     val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    //     activity.startActivity(intent)
    // }
    // fun openAppPermissionSettings() {
    //      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    //      val uri = Uri.fromParts("package", activity.packageName, null)
    //      intent.setData(uri)
    //      activity.startActivity(intent)
    // }
}
