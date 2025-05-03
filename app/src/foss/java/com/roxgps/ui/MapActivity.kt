package com.roxgps.ui // Pastikan package ini sesuai

// --- IMPORTS UNTUK MAPLIBRE DAN LAINNYA DI FLAVOR FOSS ---
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
import org.maplibre.android.MapLibre // Import MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng // LatLng untuk MapLibre
import org.maplibre.android.maps.MapView // MapView untuk MapLibre
import org.maplibre.android.maps.MapLibreMap // MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback // Listener MapLibre
import org.maplibre.android.maps.Style // Style MapLibre
import org.maplibre.android.annotations.Marker // Marker MapLibre
import org.maplibre.android.annotations.MarkerOptions // MarkerOptions MapLibre
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.utils.BitmapUtils // Untuk icon marker custom
import com.roxgps.R // Resources
import com.roxgps.databinding.ActivityMapBinding // View Binding (sama dengan Base)
import com.roxgps.utils.PrefManager // PrefManager (jika dipakai di sini)
import com.roxgps.utils.ext.getAddress // Extension function getAddress
import com.roxgps.utils.NotificationsChannel // NotificationsChannel

// ... import lain yang dibutuhkan oleh kode spesifik MapLibre ...
import com.roxgps.utils.NetworkUtils // Utility network jika dipakai di sini
import androidx.lifecycle.lifecycleScope // Untuk coroutine di sini
import kotlinx.coroutines.launch // Untuk coroutine di sini
import kotlinx.coroutines.flow.collect // Untuk collect Flow
import android.graphics.BitmapFactory // Untuk Bitmap custom marker
import android.graphics.Bitmap // Untuk Bitmap custom marker


// MapActivity untuk flavor foss (menggunakan MapLibre)
class MapActivity : BaseMapActivity(), OnMapReadyCallback { // Implement OnMapReadyCallback MapLibre

    // Properti MapLibre
    private lateinit var mapLibreMap: MapLibreMap
    private lateinit var mapView: MapView // Referensi MapView
    private var currentMarker: Marker? = null // Marker yang sedang aktif
     // mMap (lateinit) tidak dipakai langsung di sini, diganti mapLibreMap

    // Properti untuk Button (Diakses via binding, tapi mungkin perlu referensi lokal)
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var addFavoriteButton: Button
    private lateinit var searchButton: Button
    private lateinit var searchEditText: EditText
    private lateinit var searchProgress: View // Progress bar di search

    // Implementasi abstract dari BaseMapActivity

    // Mengecek apakah ada marker di map MapLibre
    override fun hasMarker(): Boolean {
        return currentMarker != null // Mengecek apakah currentMarker tidak null
    }

    // Inisialisasi Map MapLibre
    override fun initializeMap() {
        // Menginisialisasi MapLibre
        MapLibre.getInstance(this)
        // Mengambil referensi MapView dari binding
        mapView = binding.mapContainer.map // Asumsi id MapView di layout sama

        // Memuat map secara asynchronous
        mapView.getMapAsync(this) // Memanggil onMapReady setelah map siap
    }

    // Implementasi OnMapReadyCallback MapLibre
    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.mapLibreMap = mapLibreMap // Menyimpan referensi MapLibreMap

        // Memuat style map (contoh: style OSM Bright)
        mapLibreMap.setStyle(Style.Builder().fromUri("asset://osm_bright.json")) { style ->
            // Map style siap, tambahkan logika setelah style dimuat

            // Contoh: Tambahkan sumber dan layer untuk ikon kustom jika dibutuhkan
            // style.addSource(GeoJsonSource("marker-source", FeatureCollection.fromFeatures(emptyList())))
            // style.addImage("marker-icon", BitmapFactory.decodeResource(resources, R.drawable.your_marker_icon))
            // style.addLayer(SymbolLayer("marker-layer", "marker-source").withProperties(
            //     iconImage("marker-icon"),
            //     iconAllowOverlap(true),
            //     iconIgnorePlacement(true)
            // ))

            // Setup click listener pada map MapLibre
            mapLibreMap.addOnMapClickListener { latLng ->
                // Hapus marker lama jika ada
                currentMarker?.remove()
                currentMarker = null // Reset marker

                // Tambahkan marker baru di lokasi klik
                // Menggunakan MarkerOptions MapLibre
                 currentMarker = mapLibreMap.addMarker(MarkerOptions().position(latLng))

                // Update state lat/lon di BaseActivity
                lat = latLng.latitude
                lon = latLng.longitude

                 // Optional: Pindah kamera ke lokasi marker baru
                 mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0)) // Sesuaikan zoom level

                // Optional: Dapatkan alamat dari koordinat menggunakan extension function
                lifecycleScope.launch {
                    val address = latLng.getAddress(this@MapActivity) // Memanggil extension function
                    currentMarker?.title = address // Menampilkan alamat di title marker
                }

                true // Mengembalikan true untuk mengonsumsi event
            }

            // Optional: Pindah kamera ke lokasi awal (misal dari last known location atau default)
            // moveMapToNewLocation(false) // Panggil moveMapToNewLocation di onMapReady
        }
    }

    // Setup Tombol-tombol UI (spesifik di MapActivity)
    @SuppressLint("ClickableViewAccessibility") // Mungkin untuk onTouchListener jika dipakai
    override fun setupButtons() {
        // Mengambil referensi tombol dari binding
        startButton = binding.startButton
        stopButton = binding.stopButton
        addFavoriteButton = binding.addFavoriteButton
        searchButton = binding.search.searchButton // Asumsi id searchButton di layout search.xml
        searchEditText = binding.search.searchBox // Asumsi id searchBox di layout search.xml
        searchProgress = binding.search.searchProgress // Asumsi id searchProgress di layout search.xml


        // Listener tombol Start
        startButton.setOnClickListener {
            // Check permission lokasi sebelum request lokasi
            if (checkLocationPermissions()) { // Memanggil fungsi protected di BaseActivity
                // Pastikan map sudah siap (mMap sudah diinisialisasi)
                // Di MapLibre, kita pakai mapLibreMap
                 if (::mapLibreMap.isInitialized) { // <-- Perbaikan: Cek inisialisasi mapLibreMap
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

        // Listener tombol Search (Jika ada tombol terpisah untuk search)
        // binding.search.searchButton.setOnClickListener {
        //     // Trigger event search di EditText jika perlu, atau panggil searchHelper langsung
        //     val query = binding.search.searchBox.text.toString()
        //     if (query.isNotEmpty()) {
        //         // Panggil search logic di BaseActivity atau langsung di sini via searchHelper
        //         // searchEditText.onEditorAction(EditorInfo.IME_ACTION_SEARCH) // Simulasi Enter
        //     }
        // }
         // Catatan: Listener search utama sudah ada di setupNavView di BaseMapActivity
    }

    // Pindah Map ke koordinat baru MapLibre
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (::mapLibreMap.isInitialized) { // Cek inisialisasi mapLibreMap
            val newLatLng = LatLng(lat, lon) // Menggunakan LatLng MapLibre

            // Pindahkan kamera
            val cameraPosition = CameraPosition.Builder()
                .target(newLatLng) // Target koordinat
                .zoom(15.0) // Level zoom (sesuaikan)
                .tilt(0.0) // Kemiringan
                .bearing(0.0) // Arah
                .build()

            mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

             // Opsional: Tambahkan atau pindahkan marker di lokasi baru (jika belum di handle di MapClickListener atau Search Result)
             // if(moveNewLocation) { // Hanya jika pindah ke lokasi BENAR-BENAR baru (misal dari search)
             //     currentMarker?.remove()
             //     currentMarker = null // Hapus marker lama
             //     currentMarker = mapLibreMap.addMarker(MarkerOptions().position(newLatLng)) // Tambah marker baru
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

    // Lifecycle methods untuk MapView MapLibre
    // Penting untuk dipanggil dari Activity lifecycle
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
         // Panggil onResume BaseMapActivity
         // super.onResume() // Sudah dipanggil secara otomatis oleh superclass
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
