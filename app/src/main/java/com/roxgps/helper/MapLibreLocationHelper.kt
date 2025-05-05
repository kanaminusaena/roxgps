package com.roxgps.helper // Sesuaikan dengan package utility kamu

// =====================================================================
// Import Library untuk MapLibreLocationHelper (menggunakan Android LocationManager)
// =====================================================================

import android.Manifest // Untuk permission Manifest
import android.annotation.SuppressLint // Untuk suppress lint MissingPermission
import android.content.Context // Untuk Context
import android.content.Intent // Untuk Intent (misal buka settings)
import android.content.pm.PackageManager // Untuk PackageManager
import android.location.Location // Untuk objek Location
import android.location.LocationListener // Location Listener dari LocationManager
import android.location.LocationManager // LocationManager dari Android
import android.os.Bundle // Untuk Bundle di LocationListener onStatusChanged (Deprecated)
import android.os.Looper // Untuk Looper (thread)
import android.provider.Settings // Untuk Settings (buka settings lokasi/aplikasi)
import androidx.activity.ComponentActivity // Menggunakan ComponentActivity untuk Activity Result APIs
import androidx.activity.result.contract.ActivityResultContracts // Untuk cara modern minta permission
import androidx.core.app.ActivityCompat // Untuk cek permission cara lama (masih dipakai di checkLocationPermissions)
import android.net.Uri // Untuk Uri (buka settings aplikasi)
import javax.inject.Inject // <-- Import Inject
import dagger.hilt.android.scopes.ActivityScoped // Untuk menandai scope ActivityScoped
import dagger.hilt.android.qualifiers.ActivityContext // Untuk ActivityContext (Jika constructor butuh Context level Activity)


// Import interface yang diimplementasikan dan listener untuk callback
import com.roxgps.helper.ILocationHelper // Asumsi package sama
import com.roxgps.helper.LocationListener // Asumsi package sama

import timber.log.Timber // Logging


// =====================================================================
// Class MapLibreLocationHelper (Implementasi ILocationHelper untuk Flavor MapLibre)
// = implements ILocationHelper
// =====================================================================

// Class ini mengimplementasikan interface ILocationHelper menggunakan
// LocationManager dari Android standar. Cocok untuk flavor MapLibre
// yang tidak punya Google Play Services / FusedLocationProviderClient.
@ActivityScoped // Scope level Activity, karena butuh ComponentActivity/ActivityContext
class MapLibreLocationHelper @Inject constructor( // <-- TAMBAH ANOTASI @Inject constructor
    // Membutuhkan ComponentActivity untuk mendaftarkan Activity Result Launcher. Di-inject oleh Hilt.
    // Hilt bisa provide ComponentActivity karena sudah ada provider di ActivityModule.
    private val activity: ComponentActivity
    // LocationListener tidak diterima di constructor ILocationHelper.
    // Method requestLocationUpdates di ILocationHelper yang menerima listener.
) : ILocationHelper { // Mengimplementasikan interface ILocationHelper

    // LocationManager dari Android standar. Diinisialisasi di init block.
    private val locationManager: LocationManager by lazy { // Gunakan lazy delegate
        activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }


    // LocationListener dari Android standar untuk menerima update lokasi
    // Properti ini perlu disimpan agar bisa di-remove nantinya.
    private var currentLocationListener: LocationListener? = null

    // Activity Result Launcher untuk menangani hasil permintaan izin lokasi secara modern (sama seperti di GoogleHelper)
    // Hasilnya akan memanggil onPermissionGranted/Denied di LocationListener yang diberikan saat request.
    private val requestPermissionLauncher =
        // Mendaftarkan launcher dengan Activity dari constructor.
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Mendapatkan LocationListener yang aktif saat request permission terakhir dipanggil.
            val lastUsedListener = this.activeLocationListener // Mengakses properti listener terakhir

            // Mengecek hasil dari izin ACCESS_FINE_LOCATION dan ACCESS_COARSE_LOCATION
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted && coarseLocationGranted) {
                // Jika kedua izin diberikan, beri tahu listener yang terakhir digunakan
                lastUsedListener?.onPermissionGranted() // Memanggil callback melalui listener
                // Opsional: Langsung coba ambil lokasi setelah izin diberikan
                 // Jika perlu, panggil method requestLocationUpdates(lastUsedListener) lagi dari sini.
            } else {
                // Jika ada salah satu atau kedua izin ditolak, beri tahu listener
                lastUsedListener?.onPermissionDenied() // Memanggil callback melalui listener
            }
             // Optional: Clear listener setelah callback permission dipanggil jika ini adalah one-time permission request
             // this.activeLocationListener = null
        }

     // Properti untuk menyimpan LocationListener yang terakhir kali digunakan di requestLocationUpdates
     // Diperlukan oleh requestPermissionLauncher setelah permission diberikan/ditolak.
    private var activeLocationListener: LocationListener? = null


    // Blok init akan dijalankan saat objek MapLibreLocationHelper dibuat oleh Hilt
    init {
        Timber.d("MapLibreLocationHelper created")
        // LocationManager diinisialisasi via lazy delegate di atas
    }

    // =====================================================================
    // Implementasi Method dari ILocationHelper
    // =====================================================================

    // Implementasi dari ILocationHelper: Memulai proses request lokasi atau update
    // Menggunakan LocationManager. Hasil dan error dikirim ke listener yang diberikan.
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    override fun requestLocationUpdates(listener: LocationListener) { // <-- Menerima listener sebagai parameter
        // Simpan listener yang aktif untuk digunakan oleh permission launcher callback dan update callback
        this.activeLocationListener = listener
        this.currentLocationListener = listener // Gunakan properti currentLocationListener untuk LocationManager

        Timber.d("requestLocationUpdates called (MapLibre), listener set.")


        // Langkah 1: Periksa apakah izin lokasi sudah diberikan
        if (!checkLocationPermissions()) {
            // Jika izin belum diberikan, minta izin ke pengguna
            requestLocationPermissions() // Memanggil implementasi method di helper ini
            return // Hentikan proses lebih lanjut sampai izin diberikan
        }

        // Langkah 2: Periksa apakah layanan lokasi (GPS/Network) aktif
        if (!isLocationServiceEnabled()) {
            // Jika layanan lokasi tidak aktif, laporkan error ke listener
            listener.onLocationError("Layanan lokasi (GPS atau Jaringan) dinonaktifkan.") // Melaporkan error via listener
            // Di sini bisa juga memberikan opsi ke pengguna untuk membuka pengaturan lokasi
            openLocationSettings() // Memanggil method di helper ini
            return // Hentikan proses
        }

        // Langkah 3: Coba ambil lokasi terakhir yang diketahui dari LocationManager (Android API)
        // Cek provider mana yang aktif
        val providers = locationManager.getProviders(true)
        var lastKnownLocation: Location? = null

        // Coba dapatkan lokasi terakhir dari provider yang aktif
        for (provider in providers) {
            // Membutuhkan permission. checkPermissions() sudah dipanggil.
            lastKnownLocation = try {
                // Pastikan provider yang diminta benar-benar enabled
                if (locationManager.isProviderEnabled(provider)) {
                     locationManager.getLastKnownLocation(provider)
                } else {
                     null
                }
            } catch (e: SecurityException) {
                // Seharusnya tidak terjadi jika checkPermissions() bener, tapi baik untuk safety
                Timber.e(e, "SecurityException while getting last known location for provider $provider")
                listener.onLocationError("Security Exception: ${e.message}")
                null
            } catch (e: IllegalArgumentException) {
                // Provider tidak valid
                 Timber.e(e, "IllegalArgumentException while getting last known location for provider $provider")
                 listener.onLocationError("Invalid provider: $provider")
                 null
            }
            if (lastKnownLocation != null) {
                 Timber.d("Found last known location from provider $provider: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                 break // Ambil lokasi dari provider pertama yang kasih hasil dan valid
            }
        }


        if (lastKnownLocation != null) {
            // Jika lokasi terakhir ditemukan, kirim hasilnya ke listener
            listener.onLocationResult(lastKnownLocation) // Mengirim hasil via listener
        } else {
            // Jika lokasi terakhir tidak ditemukan, minta update lokasi baru (sekali) dari LocationManager
            Timber.d("Last known location not found, requesting single update.")
            requestSingleLocationUpdateInternal(listener) // Memanggil method internal di helper ini
        }
         // Opsi: Tambahkan listener untuk update lokasi kontinu jika diperlukan
         // requestContinuousLocationUpdates(listener)
    }

    // Implementasi dari ILocationHelper: Menghentikan semua update lokasi yang sedang berjalan
    // Menggunakan LocationManager.
    override fun stopLocationUpdates() {
        Timber.d("stopLocationUpdates called (MapLibre).")
        // Memastikan listener tidak null sebelum mencoba menghapus update
        currentLocationListener?.let {
            // Menghapus update lokasi menggunakan LocationManager (Android API)
            locationManager.removeUpdates(it)
             Timber.d("Location updates removed.")
        }
        // Reset listener lokal setelah di-remove
        currentLocationListener = null
        activeLocationListener = null // Reset listener yang terakhir digunakan juga
    }

    // Implementasi dari ILocationHelper: Memeriksa apakah layanan lokasi aktif
    // Menggunakan LocationManager (Android API). UMUM.
    override fun isLocationServiceEnabled(): Boolean {
        // Menggunakan properti locationManager yang diinisialisasi via lazy.
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // Implementasi dari ILocationHelper: Memeriksa apakah izin lokasi sudah diberikan
    // Menggunakan ActivityCompat (AndroidX). UMUM.
    override fun checkLocationPermissions(): Boolean {
        // Menggunakan activity dari constructor.
        return ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Implementasi dari ILocationHelper: Meminta izin lokasi ke pengguna
    // Menggunakan Activity Result Launcher. UMUM (AndroidX).
    override fun requestLocationPermissions() {
        Timber.d("requestLocationPermissions called (MapLibre).")
        // Meluncurkan permintaan izin menggunakan Activity Result Launcher yang sudah didaftarkan.
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
        // Hasilnya akan ditangani oleh callback requestPermissionLauncher di atas,
        // yang kemudian akan memanggil listener.onPermissionGranted/Denied melalui activeLocationListener.
    }

    // Implementasi dari ILocationHelper: Membuka pengaturan lokasi
    // Menggunakan Intent (Android API). UMUM.
    override fun openLocationSettings() {
        Timber.d("openLocationSettings called (MapLibre).")
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        activity.startActivity(intent) // Memulai activity dari Context/Activity yang diberikan
    }

    // Implementasi dari ILocationHelper: Membuka pengaturan izin aplikasi
    // Menggunakan Intent (Android API). UMUM.
    override fun openAppPermissionSettings() {
        Timber.d("openAppPermissionSettings called (MapLibre).")
         val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
         val uri = Uri.fromParts("package", activity.packageName, null)
         intent.setData(uri)
         activity.startActivity(intent) // Memulai activity dari Context/Activity yang diberikan
    }


    // =====================================================================
    // Method Helper Internal (Dipanggil dari Implementasi ILocationHelper)
    // =====================================================================

    // Meminta satu kali update lokasi baru dari LocationManager (Android API)
    // Dipanggil jika getLastKnownLocation tidak memberikan hasil.
    // Hasil akan dikirim ke listener yang diberikan saat request.
    @SuppressLint("MissingPermission") // Menekan warning karena permission dicek di checkLocationPermissions()
    private fun requestSingleLocationUpdateInternal(listener: LocationListener) { // <-- Menerima listener untuk callback hasil
        Timber.d("requestSingleLocationUpdateInternal called (MapLibre).")

        // Hapus listener lama jika ada permintaan update sebelumnya (untuk update sekali saja)
        // currentLocationListener?.let { locationManager.removeUpdates(it) } // Ini sudah dilakukan di stopLocationUpdates, tapi safety check lagi

        // Membuat permintaan lokasi LocationManager (Android API)
        val locationRequest = android.location.LocationRequest.Builder(
             // Di Android API < Q, interval dan fastestInterval di request ini sering diabaikan untuk numUpdates=1
             // Untuk akurasi tinggi, cukup set priority.
             // Android API > Q+ pakai Duration
             // Priority HIGH_ACCURACY setara dengan Criteria.ACCURACY_FINE
             android.location.LocationRequest.PRIORITY_HIGH_ACCURACY,
             0L // Interval minimal (milidetik)
        )
        .setDurationMillis(10000L) // Batasi waktu request (misal 10 detik)
        .setMaxUpdates(1) // Hanya perlu 1 update
        .build()


        // Buat listener baru untuk update sekali ini
        // Listener ini akan mengirim hasil ke listener yang diberikan ke requestLocationUpdates (yaitu Activity)
        // Gunakan Looper dari Main Thread untuk callback
        locationManager.requestLocationUpdates(
            locationRequest,
            object : LocationListener { // Listener anonymous untuk update sekali
                override fun onLocationChanged(location: Location) {
                    Timber.d("Single update received: ${location.latitude}, ${location.longitude}")
                    // Lokasi didapat, kirim ke listener yang diberikan ke requestLocationUpdates
                    listener.onLocationResult(location) // Mengirim hasil via listener yang DITERIMA DI METHOD INI

                    // Setelah dapat 1 update, unregister listener ini secara explicit.
                    // Meskipun numUpdates=1, explicit remove lebih pasti.
                     locationManager.removeUpdates(this) // Hapus listener anonymous ini
                     // Tidak perlu update properti class currentLocationListener/activeLocationListener di sini
                     // karena properti tersebut diset di requestLocationUpdates dan digunakan oleh permission launcher
                     // dan untuk menghapus update keseluruhan (stopLocationUpdates).

                }
                // Implementasi method LocationListener lain yang WAJIB di-override (Android API)
                override fun onProviderEnabled(provider: String) {
                    Timber.d("Location Provider enabled: $provider")
                    // listener.onLocationServiceEnabledChanged(true) // Kalau ada method ini di listener
                }
                override fun onProviderDisabled(provider: String) {
                    Timber.w("Location Provider disabled: $provider")
                    // Layanan lokasi mati, laporkan error ke listener yang DITERIMA DI METHOD INI
                     listener.onLocationError("Provider lokasi ($provider) dinonaktifkan.")
                    // listener.onLocationServiceEnabledChanged(false) // Kalau ada method ini di listener
                }
                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    Timber.d("Provider status changed: $provider, status $status")
                 }
            },
            Looper.getMainLooper() // Gunakan MainLooper untuk callback listener
        )
    }

     // Opsi: Method untuk request update lokasi kontinu jika diperlukan
     // fun requestContinuousLocationUpdates(listener: LocationListener) { ... }
     // Logic ini juga akan menggunakan LocationManager.requestLocationUpdates dengan numUpdates tidak terbatas.
     // Stopnya nanti panggil stopLocationUpdates().


     // Opsi: Method untuk mendapatkan lokasi terakhir yang diketahui TANPA request update baru
     // Implementasi dari ILocationHelper
     /*
     override fun getLastKnownLocation(): Location? {
         Timber.d("getLastKnownLocation called (MapLibre).")
         if (!checkLocationPermissions() || !isLocationServiceEnabled()) {
             Timber.w("Cannot get last known location: permissions denied or service disabled.")
             return null // Tidak bisa dapat lokasi jika permission atau layanan mati
         }
         val providers = locationManager.getProviders(true)
         var lastLocation: Location? = null
         for (provider in providers) {
              lastLocation = try {
                 // Pastikan provider yang diminta benar-benar enabled
                 if (locationManager.isProviderEnabled(provider)) {
                    // Butuh permission
                    @SuppressLint("MissingPermission") // Suppress karena sudah dicek di atas
                    locationManager.getLastKnownLocation(provider)
                 } else {
                    null
                 }
              } catch (e: SecurityException) {
                 Timber.e(e, "SecurityException while getting last known location for provider $provider")
                 null // Seharusnya tidak terjadi jika checkPermissions() bener
              } catch (e: IllegalArgumentException) {
                 Timber.e(e, "IllegalArgumentException while getting last known location for provider $provider")
                 null
              }
             if (lastLocation != null) {
                 Timber.d("Found last known location from provider $provider: ${lastLocation.latitude}, ${lastLocation.longitude}")
                 break
             }
         }
         Timber.d("Returning last known location: $lastLocation")
         return lastLocation
     }
     */


    // =====================================================================
    // Cleanup (Opsional, untuk ActivityScoped)
    // =====================================================================
    // @PreDestroy // Anotasi ini dari Javax, perlu dependensi
    // fun cleanup() {
    //    Timber.d("MapLibreLocationHelper cleanup")
    //    // Hentikan update lokasi secara paksa jika Activity dihancurkan
    //    stopLocationUpdates()
    // }
}
