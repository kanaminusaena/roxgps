// File: com/roxgps/helper/GoogleLocationHelper.kt // <-- Ganti nama file dan class
package com.roxgps.helper // Sesuaikan dengan package utility kamu

// =====================================================================
// Import Library untuk GoogleLocationHelper
// =====================================================================

import android.Manifest // Untuk permission Manifest
import android.annotation.SuppressLint // Untuk suppress lint MissingPermission
import android.content.Context // Untuk Context
import android.content.Intent // Untuk Intent (misal buka settings)
import android.content.pm.PackageManager // Untuk PackageManager
import android.location.Location // Untuk objek Location
import android.location.LocationManager // Untuk LocationManager (cek layanan aktif)
import android.os.Looper // Untuk Looper (thread)
import android.provider.Settings // Untuk Settings (buka settings lokasi/aplikasi)
import androidx.activity.ComponentActivity // Menggunakan ComponentActivity - bagus untuk Activity Result APIs
import androidx.activity.result.contract.ActivityResultContracts // Untuk cara modern minta permission
import androidx.core.app.ActivityCompat // Untuk cek permission cara lama (masih dipakai di checkPermissions)
import com.google.android.gms.location.* // Untuk FusedLocationProviderClient, LocationRequest, LocationCallback, Priority (SPESIFIK GOOGLE)
import android.net.Uri // Untuk Uri (buka settings aplikasi)
import javax.inject.Inject // <-- Import Inject
import dagger.hilt.android.scopes.ActivityScoped // Untuk menandai scope ActivityScoped
import dagger.hilt.android.qualifiers.ActivityContext // Untuk ActivityContext (Jika constructor butuh Context level Activity)


// Import Interface ILocationHelper
import com.roxgps.helper.ILocationHelper // <-- Import Interface ILocationHelper

// Import LocationListener (Interface callback)
import com.roxgps.helper.LocationListener // <-- Import LocationListener

import timber.log.Timber // Logging


// =====================================================================
// Class GoogleLocationHelper (Implementasi SPESIFIK GOOGLE)
// = implements ILocationHelper
// =====================================================================

// Helper class untuk mengelola semua logika terkait Lokasi dan Permission Lokasi MENGGUNAKAN API GOOGLE.
// Di-inject sebagai implementasi dari ILocationHelper di Module flavor 'full'.
@ActivityScoped // Scope level Activity, karena butuh ComponentActivity/ActivityContext
class GoogleLocationHelper @Inject constructor( // <-- TAMBAH ANOTASI @Inject constructor
    // Menggunakan ComponentActivity agar bisa pakai Activity Result APIs. Di-inject oleh Hilt.
    // Hilt bisa provide ComponentActivity karena sudah ada provider di ActivityModule.
    private val activity: ComponentActivity
    // LocationListener TIDAK di-inject di constructor lagi.
    // Listener akan dipass sebagai parameter method requestLocationUpdates.
    // private val listener: LocationListener // <-- DIHAPUS DARI CONSTRUCTOR
) : ILocationHelper { // <-- IMPLEMENTASIKAN INTERFACE ILocationHelper


    // FusedLocationProviderClient SPESIFIK GOOGLE. Diinisialisasi di init blok.
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // ID Permission tidak diperlukan lagi secara publik di helper ini karena menggunakan ActivityResultLauncher.
    // private val PERMISSION_ID = 42 // <- Tidak perlu di sini lagi.

    // Simpan referensi listener yang aktif saat ini, karena callback LocationListener.
    // Method requestLocationUpdates akan set ini.
    private var currentLocationListener: LocationListener? = null


    // Activity Result Launcher untuk menangani hasil permintaan izin lokasi secara modern.
    private val requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Mengecek hasil dari izin ACCESS_FINE_LOCATION dan ACCESS_COARSE_LOCATION
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifestly.permission.ACCESS_COARSE_LOCATION] ?: false

            // Panggil callback listener yang aktif
            if (fineLocationGranted && coarseLocationGranted) {
                currentLocationListener?.onPermissionGranted() // Beri tahu listener (Activity) bahwa izin berhasil
                // Opsional: Langsung coba ambil lokasi setelah izin diberikan
                 // requestLastKnownLocation() // Bisa panggil ini dari sini jika diinginkan
            } else {
                currentLocationListener?.onPermissionDenied() // Beri tahu listener (Activity) bahwa izin ditolak
                // Di sini bisa menampilkan pesan ke pengguna kenapa izin lokasi penting jika diinginkan
                // Contoh: Tampilkan Snackbar atau Dialog dari Activity melalui listener
            }
             // Optional: Clear listener setelah callback permission dipanggil jika ini adalah one-time permission request
             // currentLocationListener = null
        }

    // Blok init akan dijalankan saat objek GoogleLocationHelper dibuat oleh Hilt
    init {
        // Menginisialisasi FusedLocationProviderClient. SPESIFIK GOOGLE.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity) // Menggunakan activity dari constructor
        Timber.d("GoogleLocationHelper created")
    }

    // Callback dari FusedLocationProviderClient saat update lokasi diterima. SPESIFIK GOOGLE.
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // Mengambil lokasi terakhir dari hasil update
            val lastLocation: Location? = locationResult.lastLocation

            if (lastLocation != null) {
                 // Periksa apakah lokasi valid (bukan 0.0, 0.0) yang kadang bisa terjadi pada mock location atau error lain
                if (lastLocation.latitude != 0.0 || lastLocation.longitude != 0.0) {
                     // Jika lokasi valid, kirim hasilnya ke listener (Activity)
                     currentLocationListener?.onLocationResult(lastLocation) // <-- Panggil listener yang tersimpan
                } else {
                     // Jika lokasi terlihat tidak valid, laporkan error ke listener (Activity)
                     currentLocationListener?.onLocationError("Lokasi diterima tetapi datanya tidak valid (0.0, 0.0).")
                }
            } else {
                // Jika tidak ada lokasi yang tersedia dalam update
                currentLocationListener?.onLocationError("Tidak ada lokasi yang tersedia dalam update.")
            }
            // Jika requestLocationUpdate() dipanggil dengan setMaxUpdates(1), update akan berhenti otomatis.
            // Jika tidak, perlu dipanggil stopLocationUpdates() secara manual dari luar.
        }

        // Metode lain dari LocationCallback bisa dioverride jika diperlukan (misal: onLocationAvailability)
         override fun onLocationAvailability(p0: LocationAvailability) {
              super.onLocationAvailability(p0)
              // Bisa digunakan untuk mendeteksi jika layanan lokasi mati/hidup
              if (!p0.isLocationAvailable) {
                  // Layanan lokasi mungkin mati atau tidak tersedia saat ini
                  currentLocationListener?.onLocationError("Layanan lokasi saat ini tidak tersedia.")
              }
         }
    }

    // =====================================================================
    // Implementasi Metode dari Interface ILocationHelper
    // =====================================================================

    // Implementasi dari ILocationHelper: Memulai proses untuk mendapatkan lokasi terakhir atau meminta update baru
    // Dipanggil dari Activity saat membutuhkan lokasi.
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    override fun requestLocationUpdates(listener: LocationListener) { // <-- Menerima listener sebagai parameter
        this.currentLocationListener = listener // Simpan referensi listener yang aktif
        Timber.d("requestLocationUpdates called, listener set.")

        // Langkah 1: Periksa apakah izin lokasi sudah diberikan
        if (!checkLocationPermissions()) {
            // Jika izin belum diberikan, minta izin ke pengguna
            requestLocationPermissions() // Memanggil method di helper ini yang pakai ActivityResultLauncher
            return // Hentikan proses lebih lanjut sampai izin diberikan
        }

        // Langkah 2: Periksa apakah layanan lokasi (GPS/Network) aktif
        if (!isLocationServiceEnabled()) {
            // Jika layanan lokasi tidak aktif, laporkan error ke listener
            currentLocationListener?.onLocationError("Layanan lokasi (GPS atau Jaringan) dinonaktifkan.")
            // Di sini bisa juga memberikan opsi ke pengguna untuk membuka pengaturan lokasi
            // listener.openLocationSettings(); // Jika ada method di listener
            return // Hentikan proses
        }

        // Langkah 3: Coba ambil lokasi terakhir yang diketahui. SPESIFIK GOOGLE.
        fusedLocationClient.lastLocation.addOnSuccessListener(activity) { location: Location? ->
             if (location != null) {
                // Jika lokasi terakhir ditemukan, kirim hasilnya ke listener (Activity)
                currentLocationListener?.onLocationResult(location)
            } else {
                // Jika lokasi terakhir tidak ditemukan atau null, minta update lokasi baru (sekali)
                requestSingleLocationUpdateInternal() // Memanggil method internal di helper ini
            }
        }.addOnFailureListener { exception ->
            // Menangani kesalahan jika gagal mendapatkan lokasi terakhir. SPESIFIK GOOGLE.
            currentLocationListener?.onLocationError("Gagal mendapatkan lokasi terakhir (Google FusedLocationClient): ${exception.message}")
        }
    }

    // Implementasi dari ILocationHelper: Menghentikan update lokasi.
    // Penting dipanggil saat tidak lagi membutuhkan update (misal di onPause/onDestroy Activity).
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    override fun stopLocationUpdates() {
        Timber.d("stopLocationUpdates called.")
        // Memastikan fusedLocationClient sudah diinisialisasi sebelum mencoba menghapus update
        if (::fusedLocationClient.isInitialized) {
             // Menghapus update lokasi menggunakan LocationCallback Google. SPESIFIK GOOGLE.
             // Penting: Hapus update yang terkait dengan locationCallback kita
             fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // Optional: Clear listener setelah update dihentikan
        // currentLocationListener = null
    }

    // Implementasi dari ILocationHelper: Memeriksa apakah layanan lokasi (GPS/Network provider) aktif di perangkat. UMUM (Android API).
    override fun isLocationServiceEnabled(): Boolean {
        val locationManager: LocationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager // Menggunakan activity dari constructor
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // Implementasi dari ILocationHelper: Memeriksa apakah izin lokasi (ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION) sudah diberikan. UMUM (Android API).
    override fun checkLocationPermissions(): Boolean {
        // Menggunakan ActivityCompat (dari AndroidX) dan activity dari constructor. UMUM.
        return ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Implementasi dari ILocationHelper: Meminta izin lokasi ke pengguna menggunakan Activity Result APIs. UMUM (AndroidX).
    override fun requestLocationPermissions() {
        Timber.d("requestLocationPermissions called.")
        // Meluncurkan permintaan izin untuk ACCESS_COARSE_LOCATION dan ACCESS_FINE_LOCATION
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

    // Implementasi dari ILocationHelper: Membuka pengaturan lokasi di perangkat. UMUM (Android API).
    override fun openLocationSettings() {
        Timber.d("openLocationSettings called.")
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        activity.startActivity(intent) // Menggunakan activity dari constructor
    }

    // Implementasi dari ILocationHelper: Membuka pengaturan izin aplikasi spesifik di perangkat. UMUM (Android API).
    override fun openAppPermissionSettings() {
        Timber.d("openAppPermissionSettings called.")
         val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
         val uri = Uri.fromParts("package", activity.packageName, null) // Menggunakan activity dari constructor
         intent.setData(uri)
         activity.startActivity(intent) // Menggunakan activity dari constructor
    }


    // =====================================================================
    // Metode Internal Helper (Tidak ada di Interface)
    // =====================================================================

    // Meminta satu kali update lokasi baru dari FusedLocationProviderClient. SPESIFIK GOOGLE.
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    private fun requestSingleLocationUpdateInternal() {
        Timber.d("requestSingleLocationUpdateInternal called.")
        // Membuat permintaan lokasi. Menggunakan Priority, Builder dari Google API. SPESIFIK GOOGLE.
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // Prioritas tinggi, interval 1 detik (meskipun numUpdates = 1)
            .setWaitForAccurateLocation(true) // Tunggu lokasi akurat
            .setMaxUpdates(1) // Hanya perlu 1 update
            .build() // Build dari Google API

        // Memulai request update lokasi dari FusedLocationProviderClient. SPESIFIK GOOGLE.
        fusedLocationClient.requestLocationUpdates(
            locationRequest, // LocationRequest Google
            locationCallback, // LocationCallback Google
            Looper.myLooper() ?: Looper.getMainLooper() // Gunakan Looper saat ini atau MainLooper
        )
    }

     // Catatan: Jika butuh update lokasi terus menerus (misal untuk GPS palsu),
     // LocationRequest.Builder perlu diubah (setMaxUpdates dihapus atau di set besar)
     // dan stopLocationUpdates() perlu dipanggil saat tidak lagi dibutuhkan.
     // requestLocationUpdates(listener: LocationListener) bisa punya parameter
     // boolean isOneTime atau LocationRequest configuration untuk mengontrol ini.


    // =====================================================================
    // Cleanup (Opsional, untuk ActivityScoped)
    // =====================================================================
    // @PreDestroy // Anotasi ini dari Javax, perlu dependensi
    // fun cleanup() {
    //    Timber.d("GoogleLocationHelper cleanup")
    //    // Hentikan update lokasi secara paksa jika Activity dihancurkan
    //    stopLocationUpdates()
    // }
}
