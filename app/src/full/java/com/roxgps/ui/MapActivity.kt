package com.roxgps.ui // Pastikan package ini sesuai

// --- IMPORTS UNTUK GOOGLE MAPS DAN LAINNYA DI FLAVOR FULL ---
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button // Untuk referensi tombol di setupButtons
import android.widget.EditText // Untuk referensi EditText di setupButtons
import android.widget.TextView // Untuk referensi TextView di setupButtons
import android.widget.Toast // Jika Toast dipanggil langsung di sini
import androidx.core.content.ContextCompat // Untuk drawable/color
import com.google.android.gms.maps.CameraUpdateFactory // Google Maps Camera
import com.google.android.gms.maps.GoogleMap // GoogleMap
import com.google.android.gms.maps.MapView // Google Maps MapView
import com.google.android.gms.maps.MapsInitializer // Google Maps Initializer
import com.google.android.gms.maps.OnMapReadyCallback // Listener Google Maps
import com.google.android.gms.maps.model.LatLng // LatLng untuk Google Maps
import com.google.android.gms.maps.model.Marker // Marker Google Maps
import com.google.android.gms.maps.model.MarkerOptions // MarkerOptions Google Maps
import com.google.android.gms.maps.model.BitmapDescriptorFactory // Untuk icon marker custom
import com.roxgps.R // Resources
import com.roxgps.databinding.ActivityMapBinding // View Binding (sama dengan Base)
import com.roxgps.utils.PrefManager // PrefManager (jika dipakai di sini)
import com.roxgps.utils.ext.getAddress // Extension function getAddress
import com.roxgps.utils.NotificationsChannel // NotificationsChannel

// ... import lain yang dibutuhkan oleh kode spesifik Google Maps ...
import com.roxgps.utils.NetworkUtils // Utility network jika dipakai di sini
import androidx.lifecycle.lifecycleScope // Untuk coroutine di sini
import kotlinx.coroutines.launch // Untuk coroutine di sini
import kotlinx.coroutines.flow.collect // Untuk collect Flow
import android.graphics.BitmapFactory // Untuk Bitmap custom marker
import android.graphics.Bitmap // Untuk Bitmap custom marker


// MapActivity untuk flavor full (menggunakan Google Maps)
class MapActivity : BaseMapActivity(), OnMapReadyCallback { // Implement OnMapReadyCallback Google Maps

    // Properti Google Maps
    private lateinit var googleMap: GoogleMap
    private lateinit var mapView: MapView // Referensi MapView
    private var currentMarker: Marker? = null // Marker yang sedang aktif
    // mMap (lateinit) di BaseMapActivity tidak dipakai langsung di sini, diganti googleMap

    // Properti untuk Button (Diakses via binding, tapi mungkin perlu referensi lokal)
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var addFavoriteButton: Button
    private lateinit var searchButton: Button
    private lateinit var searchEditText: EditText
    private lateinit var searchProgress: View // Progress bar di search


    // Implementasi abstract dari BaseMapActivity

    // Mengecek apakah ada marker di map Google Maps
    override fun hasMarker(): Boolean {
        return currentMarker != null // Mengecek apakah currentMarker tidak null
    }

    // Inisialisasi Map Google Maps
    override fun initializeMap() {
        // Menginisialisasi Google Maps
        MapsInitializer.initialize(applicationContext)
        // Mengambil referensi MapView dari binding
        mapView = binding.mapContainer.map // Asumsi id MapView di layout sama

        // Membuat map secara asynchronous
        mapView.getMapAsync(this) // Memanggil onMapReady setelah map siap
    }

    // Implementasi OnMapReadyCallback Google Maps
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap // Menyimpan referensi GoogleMap

        // Mengaktifkan My Location layer (opsional)
        // if (checkLocationPermissions()) { // Check permission jika belum dilakukan di tempat lain
        //     googleMap.isMyLocationEnabled = true
        // }

        // Setup click listener pada map Google Maps
        googleMap.setOnMapClickListener { latLng ->
            // Hapus marker lama jika ada
            currentMarker?.remove()
            currentMarker = null // Reset marker

            // Tambahkan marker baru di lokasi klik
            // Menggunakan MarkerOptions Google Maps
             currentMarker = googleMap.addMarker(MarkerOptions().position(latLng).draggable(true)) // Tambahkan .draggable(true)

            // Update state lat/lon di BaseActivity
            lat = latLng.latitude
            lon = latLng.longitude

            // Optional: Pindah kamera ke lokasi marker baru
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f)) // Sesuaikan zoom level

            // Optional: Dapatkan alamat dari koordinat menggunakan extension function
            lifecycleScope.launch {
                 val address = latLng.getAddress(this@MapActivity) // Memanggil extension function
                 currentMarker?.title = address // Menampilkan alamat di title marker
            }
        }

         // Implementasi lain yang dibutuhkan setelah map siap
         // ...

        // Optional: Pindah kamera ke lokasi awal (misal dari last known location atau default)
        // moveMapToNewLocation(false) // Panggil moveMapToNewLocation di onMapReady
    }

    // Setup Tombol-tombol UI (spesifik di MapActivity)
    @SuppressLint("ClickableViewAccessibility")
    override fun setupButtons() {
        // Mengambil referensi tombol dari binding
        startButton = binding.startButton
        stopButton = binding.stopButton
        addFavoriteButton = binding.addFavoriteButton
        searchButton = binding.search.searchButton
        searchEditText = binding.search.searchBox
        searchProgress = binding.search.searchProgress


        // Listener tombol Start
        startButton.setOnClickListener {
            // Check permission lokasi sebelum request lokasi
            if (checkLocationPermissions()) { // Memanggil fungsi protected di BaseActivity
                // Pastikan map sudah siap (mMap sudah diinisialisasi)
                // Di Google Maps, kita pakai googleMap
                 if (::googleMap.isInitialized) { // <-- Perbaikan: Cek inisialisasi googleMap
                     // Panggil helper atau fungsi di Base untuk request lokasi
                     requestLocation() // Memanggil fungsi protected di BaseActivity

                     // Sembunyikan/tampilkan tombol
                     startButton.visibility = View.GONE
                     stopButton.visibility = View.VISIBLE
                     addFavoriteButton.visibility = View.VISIBLE // Tampilkan tombol favorit

                 } else {
                     showToast("Map belum siap.") // Membutuhkan showToast extension
                 }
            } else {
                showToast("Izin lokasi tidak diberikan.") // Membutuhkan showToast extension
                // Opsi: Tampilkan dialog penjelasan permission lagi
            }
        }

        // Listener tombol Stop
        stopButton.setOnClickListener {
            // Panggil helper atau fungsi di Base untuk menghentikan update lokasi
            stopLocationUpdates() // Memanggil fungsi protected di BaseActivity

            // Panggil helper atau fungsi di Base untuk membatalkan notifikasi
            cancelNotification() // Memanggil fungsi protected di BaseActivity

            // Hapus marker yang sedang aktif
            currentMarker?.remove()
            currentMarker = null

            // Sembunyikan/tampilkan tombol
            startButton.visibility = View.VISIBLE
            stopButton.visibility = View.GONE
            addFavoriteButton.visibility = View.GONE // Sembunyikan tombol favorit
        }

        // Listener tombol Add Favorite
        addFavoriteButton.setOnClickListener {
             // Panggil fungsi addFavoriteAction di BaseActivity
             addFavoriteAction() // Memanggil fungsi protected di BaseActivity
             // Logic add favorite dilanjutkan di BaseActivity melalui dialog
        }

         // Catatan: Listener search utama sudah ada di setupNavView di BaseMapActivity
    }

    // Pindah Map ke koordinat baru Google Maps
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (::googleMap.isInitialized) { // Cek inisialisasi googleMap
            val newLatLng = LatLng(lat, lon) // Menggunakan LatLng Google Maps

            // Pindahkan kamera
            val cameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder() // Menggunakan CameraPosition Google Maps
                .target(newLatLng) // Target koordinat
                .zoom(15.0f) // Level zoom (gunakan float untuk Google Maps)
                .tilt(0.0f) // Kemiringan
                .bearing(0.0f) // Arah
                .build()

            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

            // Opsional: Tambahkan atau pindahkan marker di lokasi baru (jika belum di handle di MapClickListener atau Search Result)
             // if(moveNewLocation) { // Hanya jika pindah ke lokasi BENAR-BENAR baru (misal dari search)
             //     currentMarker?.remove()
             //     currentMarker = null // Hapus marker lama
             //     currentMarker = googleMap.addMarker(MarkerOptions().position(newLatLng).draggable(true)) // Tambah marker baru
             // }
        }
    }

    // Implementasi abstract untuk logika inti Stop Proses
    override fun stopProcess() {
         // Logic yang dilakukan saat proses berhenti, misal dari notifikasi atau tombol Stop
         stopLocationUpdates() // Memanggil fungsi protected di BaseActivity
         cancelNotification() // Memanggil fungsi protected di BaseActivity
         currentMarker?.remove() // Hapus marker
         currentMarker = null
         // Update UI tombol
         startButton.visibility = View.VISIBLE
         stopButton.visibility = View.GONE
         addFavoriteButton.visibility = View.GONE
         // Opsional: Reset status lain jika ada
    }

    // Lifecycle methods untuk MapView Google Maps
    // Penting untuk dipanggil dari Activity lifecycle
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
         // Panggil onResume BaseMapActivity
         // super.onResume() // Sudah dipanggil secara otomatis
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
         // Panggil onDestroy BaseMapActivity
        // super.onDestroy() // Sudah dipanggil secara otomatis
    }

     // getActivityInstance() sudah dihapus di Base, tidak perlu override di sini

     // Fungsi helper untuk menampilkan Toast jika belum ada extension
    // protected fun showToast(message: String) { // Sudah ada di BaseMapActivity
    //     Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    // }
}
