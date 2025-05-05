package com.roxgps.ui // Pastikan package ini sesuai


// =====================================================================
// Import Library MapActivity (MapLibre Flavor - Final Refactoring)
// =====================================================================

import android.Manifest // Untuk permission (jika diperlukan di implementasi listener)
import android.annotation.SuppressLint // Suppress lint
import android.content.Context // Context
import android.content.Intent // Intent (untuk startActivity)
import android.content.pm.PackageManager // Untuk PackageManager
import android.graphics.Color // Color (jika dipakai spesifik di MapActivity)
import android.location.Location // Location (dari callback LocationListener)
import android.os.Bundle // Untuk Bundle di lifecycle methods
import android.util.Log // Untuk Log
import android.view.View // Untuk View
import android.view.LayoutInflater // Import LayoutInflater (dibutuhkan untuk dialogHelper)
import android.view.inputmethod.EditorInfo // EditorInfo
import android.widget.EditText // EditText (di dialog)
import android.widget.TextView // TextView (di dialog)
import android.widget.Toast // Toast
// Import dari androidx atau Material yang dibutuhkan di MapActivity
// import androidx.activity.viewModels // ViewModel di-inject di Base, tidak perlu di sini
import androidx.appcompat.app.AlertDialog // AlertDialog
import androidx.appcompat.widget.AppCompatButton // Button (di dialog)
import androidx.core.app.ActivityCompat // Permission helper (jika diperlukan)
import androidx.core.app.NotificationCompat // Notifikasi
import androidx.core.view.* // ViewCompat, WindowInsetsCompat, GravityCompat
import androidx.lifecycle.Lifecycle // Lifecycle State
import androidx.lifecycle.lifecycleScope // Coroutine scope
import androidx.lifecycle.repeatOnLifecycle // RepeatOnLifecycle
// import androidx.lifecycle.ViewModel // Tidak perlu import tipe ViewModel
import androidx.recyclerview.widget.LinearLayoutManager // RecyclerView LayoutManager
import androidx.recyclerview.widget.RecyclerView // RecyclerView (di dialog)

// Import MapLibre spesifik
import org.maplibre.android.MapLibre // Untuk inisialisasi MapLibre SDK (Jika dilakukan di Activity)
import org.maplibre.android.camera.CameraPosition // Untuk konfigurasi kamera
import org.maplibre.android.camera.CameraUpdateFactory // Untuk membuat CameraUpdate
import org.maplibre.android.geometry.LatLng // LatLng untuk MapLibre (Penting: Beda class dengan com.google.android.gms.maps.model.LatLng)
import org.maplibre.android.maps.MapView // MapView untuk MapLibre (Dideklarasikan di Base)
import org.maplibre.android.maps.MapLibreMap // MapLibreMap (Objek map utama)
import org.maplibre.android.maps.OnMapReadyCallback // Listener MapLibre
import org.maplibre.android.maps.Style // Style MapLibre
import org.maplibre.android.annotations.Marker // Marker MapLibre (Ini Annotations API lama MapLibre < v10)
import org.maplibre.android.annotations.MarkerOptions // MarkerOptions MapLibre (Ini Annotations API lama MapLibre < v10)
// Jika pakai Annotations API v10+ (lebih disarankan):
// import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
// import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
// import com.mapbox.geojson.Point // Untuk Point GeoJson
// import com.mapbox.maps.plugin.annotation.annotations
// import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager


// Import Hilt untuk Field Injection Helper (Hanya helper spesifik flavor)
import dagger.hilt.android.AndroidEntryPoint // Anotasi Hilt (Sudah ada di Base)
// Import resource, binding, viewmodel, utils, dll.
import com.roxgps.R // Resources
import com.roxgps.databinding.ActivityMapBinding // View Binding (sama dengan Base)
import com.roxgps.ui.viewmodel.MainViewModel // ViewModel utama (sama dengan Base)
import com.roxgps.adapter.FavListAdapter // Adapter favorit
import com.roxgps.utils.NotificationsChannel // Utility Notifikasi Channel (Di-inject di Base)
import com.roxgps.utils.PrefManager // Preference Manager (Di-inject di Base)
import com.roxgps.utils.ext.* // Extension functions (getAddress, showToast, isNetworkConnected, dll)
import kotlinx.coroutines.* // Coroutine basics
import kotlinx.coroutines.flow.collectLatest // Untuk collect Flow (lebih aman dari collect)
// Import Material Components (jika dipakai di dialog)
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator // ProgressBar Material
import com.google.android.material.snackbar.Snackbar // Snackbar
// Import Semua Helper dan Listener (Di-inject di Base atau di MapActivity ini)
import com.roxgps.helper.ILocationHelper // Import interface helper lokasi (DI-INJECT di MapActivity)
import com.roxgps.helper.LocationListener // Import listener callback helper lokasi
import com.roxgps.helper.PermissionHelper // Import PermissionHelper (DI-INJECT di Base)
import com.roxgps.helper.PermissionResultListener // Import listener callback PermissionHelper
import com.roxgps.helper.NotificationHelper // Import NotificationHelper (DI-INJECT di Base)
import com.roxgps.helper.SearchHelper // Import SearchHelper (Bisa diinject di Base atau dipakai internal Repo)
import com.roxgps.helper.DialogHelper // Import DialogHelper (DI-INJECT di Base)
// Import Sealed class State dari Repository/Helper BARU
import com.roxgps.helper.SearchProgress // Import Sealed Class SearchProgress dari package helper
import com.roxgps.repository.DownloadRepository.DownloadState // Import Sealed Class DownloadState dari Repository Download
// Import data class YourUpdateModel
import com.roxgps.update.YourUpdateModel // Import data class YourUpdateModel


// Import Hilt untuk Field Injection (hanya untuk helper spesifik flavor)
import javax.inject.Inject
// Import yang mungkin tidak diperlukan di Activity kalau logic di Helper
import android.net.Uri // Untuk Uri (untuk Intent installer)

import timber.log.Timber // Logging


// =====================================================================
// Class MapActivity (FLAVOR MAPLIBRE - Final Refactoring)
// Mengimplementasikan MapLibre Map API dan mengamati ViewModel state/event
// =====================================================================

// Anotasi Hilt untuk Dependency Injection (Sudah ada di Base)
@AndroidEntryPoint
// MapActivity meng-extend BaseMapActivity, mengimplementasikan callback map MapLibre,
// DAN listener callback dari Helper (LocationListener, PermissionResultListener)
class MapActivity : BaseMapActivity(), OnMapReadyCallback, LocationListener, PermissionResultListener { // Implement OnMapReadyCallback MapLibre untuk flavor MapLibre

    // Tag untuk logging di MapActivity
    private val TAG = "MapActivityLibre" // Ubah TAG agar beda dengan flavor Google

    // =====================================================================
    // Properti Spesifik MapActivity (MapLibre Flavor)
    // =====================================================================
    // Objek MapLibreMap, dijamin tidak null setelah onMapReady MapLibre
    private lateinit var mapLibreMap: MapLibreMap // Untuk flavor MapLibre


    // Variable untuk menyimpan LatLng spesifik flavor lokasi yang sedang aktif/ditampilkan/dipilih user.
    private var mLatLng: org.maplibre.android.geometry.LatLng? = null // <-- Menggunakan LatLng MapLibre


    // Marker yang sedang aktif di map (Annotations API lama MapLibre < v10).
    private var currentMarker: org.maplibre.android.annotations.Marker? = null
    // Jika pakai Annotations API v10+ (lebih disarankan):
    // private var pointAnnotationManager: PointAnnotationManager? = null


    // Properti Dialog & Adapter (sama dengan Google)
    private var favListAdapter: FavListAdapter = FavListAdapter() // Adapter ini spesifik ke UI list favorit, manage di sini


    // =====================================================================
    // Properti Helper (Di-inject)
    // =====================================================================
    // Helper Lokasi - Tipe Interface ILocationHelper. Implementasi MapLibreLocationHelper di-inject DI SINI
    // sesuai dengan @Binds di ActivityModule flavor 'foss'.
    @Inject // <-- Injeksi Helper Lokasi Spesifik Flavor (ILocationHelper)
    lateinit var locationHelper: ILocationHelper // Hilt akan meng-inject MapLibreLocationHelper


    // Helper Permission, Notifikasi, Search, Dialog.
    // Mereka sudah di-inject di BaseMapActivity dan bersifat PROTECTED.
    // Akses properti mereka dari Base: this.permissionHelper, this.notificationsChannel, this.dialogHelper


    // =====================================================================
    // Implementasi Metode Lifecycle Activity
    // Panggil super, MapView/Fragment lifecycle, dan daftar/lepas helper/receiver.
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // super.onCreate() di Base akan menginisialisasi binding, viewModel, dll. DAN MapView.onCreate().
        super.onCreate(savedInstanceState)

        // Tidak perlu panggil observasi ViewModel di sini lagi!
        // Semua observasi StateFlow, LiveData, dan SharedFlow DILAKUKAN DI BaseMapActivity.observeViewModel()
        // Method observeViewModelState() dan observeViewModelEvents() di MapActivity DIHAPUS.

        // MapView lifecycle onCreate dipanggil di Base Activity onCreate jika Base menggunakan MapView
        // Pastikan binding.mapContainer di layout XML adalah MapView MapLibre dan diinisialisasi di Base.onCreate.
        // Inisialisasi SDK MapLibre JIKA dilakukan di Activity (kurang disarankan di sini, lebih baik di Application class)
        // MapLibre.getInstance(this) // Jika inisialisasi SDK di Activity (kurang disarankan)

        // Handle Intent (jika Activity diluncurkan dengan data/action tertentu) - Logic di Base
        // handleIntent(intent) // Sudah dipanggil di Base. Bisa di-override di sini kalau perlu logic tambahan.
    }

    // === Metode Lifecycle onStart, onResume, onPause, onStop, onSaveInstanceState, onLowMemory, onDestroy ===
    // Implementasi ini mengasumsikan BaseMapActivity menggunakan MapView MapLibre dan memanggil lifecycle-nya.
    override fun onStart() {
        super.onStart()
         Log.d(TAG, "onStart()")
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onStart() // Jika Base tidak memanggil

         // Mendaftar atau mengaktifkan listener yang butuh onStart
    }

    override fun onResume() {
        super.onResume()
         Log.d(TAG, "onResume()")
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onResume() // Jika Base tidak memanggil

        // Mendaftar BroadcastReceiver notifikasi jika NotificationHelper membutuhkannya di sini
        // notificationsChannel.registerReceiver(this) // <-- Mengakses notificationsChannel dari Base
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onPause() // Jika Base tidak memanggil

        // Melepas BroadcastReceiver notifikasi jika NotificationHelper membutuhkannya di sini
        // notificationsChannel.unregisterReceiver(this) // <-- Mengakses notificationsChannel dari Base
    }

    override fun onStop() {
        super.onStop()
         Log.d(TAG, "onStop()")
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onStop() // Jika Base tidak memanggil
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
         Log.d(TAG, "onSaveInstanceState()")
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onSaveInstanceState(outState) // Jika Base tidak memanggil
    }

    override fun onLowMemory() {
        super.onLowMemory()
         Log.d(TAG, "onLowMemory()")
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onLowMemory() // Jika Base tidak memanggil
    }

    override fun onDestroy() {
        super.onDestroy()
         Log.d(TAG, "onDestroy()")
        // Menghentikan update lokasi menggunakan Helper saat Activity dihancurkan
        locationHelper.stopLocationUpdates() // Panggil helper lokasi (ILocationHelper)

        // Jika Base menggunakan MapView MapLibre
        // Asumsi mapContainer di Base Layout adalah MapView MapLibre dan Base memanggil lifecycle-nya.
        // binding.mapContainer.map.onDestroy() // Jika Base tidak memanggil

        // Cleanup resources jika ada di MapActivity spesifik flavor (misal AnnotationManager MapLibre v10+)
        // pointAnnotationManager?.onDestroy() // Untuk MapLibre v10+
    }

    // =====================================================================
    // DIHAPUS: Metode Mengamati StateFlow dan LiveData dari ViewModel
    // LOGIKA INI SUDAH ADA DI BaseMapActivity.observeViewModel()
    // =====================================================================
    // private fun observeViewModelState() { /* DIHAPUS */ }
    // private fun observeViewModelEvents() { /* DIHAPUS */ }


    // =====================================================================
    // Implementasi Metode Abstract dari BaseMapActivity
    // Logik panggil Helper atau Map API spesifik MapLibre.
    // =====================================================================

    // Implementasi dari BaseMapActivity: Mengecek status marker MapLibre
    // Implementasi MapLibre (Annotations API lama).
    override fun hasMarker(): Boolean {
        // Untuk flavor MapLibre (Annotations API lama):
        return currentMarker != null
        // Jika pakai Annotations API v10+: return pointAnnotationManager?.annotations?.isNotEmpty() == true
    }

    // Implementasi dari BaseMapActivity: Inisialisasi Map MapLibre
    override fun initializeMap() {
        Log.d(TAG, "Initializing MapLibreMap async.")
        // Mengambil referensi MapView dari binding Base
        // MapView harus dideklarasikan di BaseMapActivity dan diinisialisasi di BaseMapActivity.onCreate
        // Akses MapView dari properti protected di Base MapActivity: this.mapView <-- Jika Base punya properti MapView

        // Asumsi binding.mapContainer di layout XML adalah MapView MapLibre dan Base sudah menginisialisasinya.
        // Memuat map secara asynchronous MapLibre. Setelah siap, onMapReady akan dipanggil.
        binding.mapContainer.map.getMapAsync(this) // 'this' refers to MapActivity implementing OnMapReadyCallback MapLibre
    }

    // Implementasi dari BaseMapActivity: Pindah kamera map ke lokasi baru menggunakan MapLibre API.
    override fun moveMapToNewLocation(location: Location, moveNewLocation: Boolean) { // Menerima Location objek standar Android
        Log.d(TAG, "Moving map to location: ${location.latitude}, ${location.longitude}")
        // Pastikan map MapLibre sudah siap
        if (::mapLibreMap.isInitialized) {
            // Membuat LatLng MapLibre dari Location objek parameter
            val targetLatLng = org.maplibre.android.geometry.LatLng(location.latitude, location.longitude) // <-- Menggunakan LatLng MapLibre

            // Set mLatLng (properti di MapActivity, LatLng MapLibre)
            mLatLng = targetLatLng

            // Gerakkan kamera menggunakan API MapLibre
            val cameraPosition = CameraPosition.Builder().target(targetLatLng).zoom(15.0).build() // Zoom double di MapLibre
            if (moveNewLocation) { mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition)) } else { mapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition)) }


             // Tambahkan atau pindahkan marker di lokasi baru (panggil method helper spesifik MapLibre di Activity ini)
             // Method updateMarker membutuhkan LatLng MapLibre.
             updateMarker(targetLatLng, currentMarker?.title ?: "Lokasi")

            // Dapatkan alamat dari lokasi baru secara asynchronous menggunakan ViewModel (yang memanggil SearchRepository)
            // Panggil ViewModel untuk memicu proses reverse geocoding.
            // Hasilnya (alamat) akan datang via searchResult StateFlow di observer ViewModel di Base.
            // ViewModel sudah punya akses ke lat/lon dari Location yang diteruskan ke setSearchedLocation()
            // Jadi, mungkin tidak perlu panggil triggerReverseGeocoding di sini lagi jika alur search
            // sudah terpusat di ViewModel.
            // Cek apakah ViewModel sudah memicu reverse geocoding setelah setSearchedLocation().
            // Kalau searchResult di Base sudah menangani ReverseGeocoding saat Complete, baris di bawah ini bisa dihapus.
            // viewModel.triggerReverseGeocoding(location.latitude, location.longitude) // <-- Cek di ViewModel apakah ini sudah ditangani

             // Sinkronisasi lat/lon di BaseMapActivity
            this@MapActivity.lat = location.latitude
            this@MapActivity.lon = location.longitude
        }
    }

    // Implementasi dari BaseMapActivity: Setup listeners button. Sama persis untuk kedua flavor.
    @SuppressLint("ClickableViewAccessibility")
    override fun setupButtons() {
         Log.d(TAG, "Setting up buttons.")
         // Mengakses referensi tombol dari binding Base (binding sama di kedua flavor)

        // Listener tombol Start (panggil ViewModel.update)
         binding.startButton.setOnClickListener {
             mLatLng?.let { latLng -> // Pastikan mLatLng (LatLng MapLibre) tidak null
                 // Buat objek Location dari mLatLng (konversi dari LatLng MapLibre ke Location standar)
                 val selectedLocation = Location("user_selected").apply {
                     latitude = latLng.latitude
                     longitude = latLng.longitude
                 }
                 // Update ViewModel dengan status mulai (true) dan lokasi yang dipilih (Location standar)
                 viewModel.update(true, selectedLocation.latitude, selectedLocation.longitude)
                 Log.d(TAG, "Start button clicked. ViewModel update(true, ...)")


                 // Update marker dengan title khusus (panggil method helper spesifik MapLibre di Activity ini)
                 updateMarker(latLng, "Harapan Palsu") // Gunakan mLatLng (LatLng spesifik flavor MapLibre)
                 // Gerakkan kamera menggunakan API MapLibre
                 mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0))


                 // --- Mengatur Visibilitas Button ---
                 binding.startButton.visibility = View.GONE
                 binding.stopButton.visibility = View.VISIBLE
                 binding.addfavorite.visibility = View.VISIBLE
                 // ---------------------------------

                 // Trigger reverse geocoding untuk alamat dan notifikasi (SAMA seperti Google)
                 // ViewModel yang memicu reverse geocoding, dan alamat akan ditangkap observer searchResult di Base.
                 viewModel.triggerReverseGeocoding(selectedLocation.latitude, selectedLocation.longitude)
                 // Setelah alamat didapat, BaseMapActivity.observeViewModel akan menampilkan notifikasi via showStartNotification().

             } ?: run { // Jika mLatLng null
                 // showToast(getString(R.string.invalid_location)) // Ditangani oleh showToastEvent dari ViewModel
                  lifecycleScope.launch { viewModel.showToastEvent.emit(getString(R.string.invalid_location)) } // Emit Toast via event
             }
         }

        // Listener tombol Stop (panggil performStopButtonClick di Base)
        binding.stopButton.setOnClickListener {
             Log.d(TAG, "Stop button clicked. Performing stop button click.")
             performStopButtonClick() // <-- Panggil method di Base yang akan memanggil onStopButtonClicked() di sini
        }

        // Listener tombol Add Favorite (panggil addFavoriteDialog)
        binding.addfavorite.setOnClickListener {
             Log.d(TAG, "Add Favorite button clicked. Showing dialog.")
             addFavoriteDialog() // <-- Panggil method abstract di MapActivity ini
         }

        // Listener tombol "Dapatkan Lokasi Asli/Mock" (Jika ada, panggil locationHelper)
        binding.getlocation.setOnClickListener {
             Log.d(TAG, "Get Location button clicked. Requesting updates.")
           locationHelper.requestLocationUpdates(this) // 'this' karena MapActivity mengimplementasikan LocationListener. locationHelper adalah ILocationHelper.
        }

        // Setup search bar listener di BaseMapActivity (panggil ViewModel.searchAddress)
        // Logic listener ini ada di BaseMapActivity.setupNavView()
        // MapActivity ini akan mengamati Flow searchResult dari ViewModel di observeViewModelState().
    }


    // Implementasi dari BaseMapActivity: Mendapatkan lokasi terakhir (panggil locationHelper). Sama persis untuk kedua flavor.
    @SuppressLint("MissingPermission") // Suppress karena permission dicek oleh Helper ILocationHelper
    override fun getLastLocation() {
         Log.d(TAG, "getLastLocation() called. Requesting Location updates via helper.")
         // locationHelper.getLastLocation(this) // Opsi: Panggil getLastLocation di helper
         locationHelper.requestLocationUpdates(this) // Panggil method di ILocationHelper (this = MapActivity implementing LocationListener)
    }

    // Implementasi dari BaseMapActivity: Menangani kesalahan terkait lokasi. Sama persis untuk kedua flavor.
    override fun handleLocationError() {
        Log.w(TAG, "Handling Location Error.")
        Snackbar.make(binding.root, "Location services are disabled.", Snackbar.LENGTH_LONG)
            .setAction("Enable") {
                locationHelper.openLocationSettings() // Panggil method di ILocationHelper
            }
            .show()
    }

     // Implementasi dari BaseMapActivity: Method abstract onStopButtonClicked. Sama persis untuk kedua flavor.
     override fun onStopButtonClicked() {
         Log.d(TAG, "onStopButtonClicked() called.")
         locationHelper.stopLocationUpdates() // Panggil method di ILocationHelper
         mLatLng?.let { // Pastikan mLatLng (LatLng MapLibre) tidak null
             // Konversi LatLng MapLibre ke Location standar Android
             val stoppedLocation = Location("user_stopped").apply {
                  latitude = it.latitude
                  longitude = it.longitude
             }
             viewModel.update(false, stoppedLocation.latitude, stoppedLocation.longitude) // Panggil ViewModel dengan Location standar
         } ?: run { // Jika mLatLng null (seharusnya tidak terjadi kalau tombol Stop visible)
              Log.w(TAG, "onStopButtonClicked called but mLatLng is null.")
         }

         removeMarker() // Panggil method helper spesifik MapLibre di Activity ini (hapus marker)
         // showToast(getString(R.string.location_unset)) // Ditangani oleh showToastEvent dari ViewModel
         lifecycleScope.launch { viewModel.showToastEvent.emit(getString(R.string.location_unset)) } // Emit Toast via event

         // --- Mengatur Visibilitas Button ---
         binding.startButton.visibility = View.VISIBLE
         binding.stopButton.visibility = View.GONE
         binding.addfavorite.visibility = View.GONE
          // Jika ada tombol "Dapatkan Lokasi Asli/Mock", tampilkan lagi:
          binding.getlocation.visibility = View.VISIBLE // Tampilkan kembali
         // ---------------------------------
     }

    // Implementasi dari BaseMapActivity: Menampilkan notifikasi "Start" (panggil notificationHelper dari Base)
    // SAMA PERSIS UNTUK KEDUA FLAVOR. notificationHelper di-inject di Base.
    override fun showStartNotification(address: String) {
         Log.d(TAG, "showStartNotification() called with address: $address")
         // Panggil method di NotificationHelper dari Base
        notificationsChannel.showStartNotification(address)
    }

    // Implementasi dari BaseMapActivity: Membatalkan notifikasi (panggil notificationHelper dari Base)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    override fun cancelNotification() {
         Log.d(TAG, "cancelNotification() called.")
        // Panggil method di NotificationHelper dari Base
        notificationsChannel.cancelNotification()
    }

    // Implementasi dari BaseMapActivity: Menampilkan dialog tambah favorit (panggil dialogHelper dari Base & viewModel)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    override fun addFavoriteDialog() {
        Log.d(TAG, "addFavoriteDialog() called.")
        dialogHelper.showAddFavoriteDialog(layoutInflater) { favoriteName -> // layoutInflater dari Base
             if (mLatLng != null) { // mLatLng (LatLng MapLibre)
                // Panggil ViewModel.storeFavorite dengan lat/lon dari LatLng MapLibre
                viewModel.storeFavorite(favoriteName, mLatLng!!.latitude, mLatLng!!.longitude)
            } else {
                 // showToast(getString(R.string.location_not_select)) // Ditangani oleh showToastEvent dari ViewModel
                  lifecycleScope.launch { viewModel.showToastEvent.emit(getString(R.string.location_not_select)) } // Emit Toast via event
            }
        }
    }

    // Implementasi dari BaseMapActivity: Menampilkan dialog daftar favorit (panggil dialogHelper dari Base & viewModel)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    override fun showFavoriteListDialog() {
         Log.d(TAG, "showFavoriteListDialog() called.")
         // BaseMapActivity sudah mengamati viewModel.allFavList.
         // Di sini, kita hanya perlu memanggil dialogHelper dengan list favorit yang sudah ada di ViewModel.
         // Ambil list dari ViewModel.value
         val favList = viewModel.allFavList.value // Ambil nilai StateFlow saat ini

         dialogHelper.showFavoriteListDialog(
             context = this@MapActivity, // Gunakan context Activity Map ini
             favList = favList, // Menggunakan list yang diambil dari ViewModel.value
             onItemClick = { favorite ->
                 // Buat Location object dari favorit (Location standar Android)
                 val favLocation = Location("favorite").apply {
                     latitude = favorite.lat!!
                     longitude = favorite.lng!!
                 }
                 // Update lat/lon di Base Activity
                 this@MapActivity.lat = favLocation.latitude
                 this@MapActivity.lon = favLocation.longitude
                 // Pindah map ke lokasi favorit yang dipilih
                 moveMapToNewLocation(favLocation, true) // Panggil method abstract di MapActivity ini
                 // Dismiss dialog favorit setelah item diklik
                 // DialogHelper harus mengurus dismiss dialog-nya sendiri
                 // atau mengembalikan referensi AlertDialog untuk di-dismiss di sini.
                 // dialog.dismiss() // Contoh jika dialogHelper mengembalikan AlertDialog
             },
             onItemDelete = { favorite ->
                  Log.d(TAG, "Favorite item deleted: ${favorite.address}")
                 viewModel.deleteFavorite(favorite) // Panggil ViewModel untuk hapus
             },
             favListAdapter = favListAdapter // Reuse adapter
         )
         // Tidak perlu lagi mengamati allFavList di sini karena observasi sudah di Base.
    }


    // Implementasi dari BaseMapActivity: Menampilkan dialog about. Sama persis untuk kedua flavor.
    override fun showAboutDialog() {
         Log.d(TAG, "showAboutDialog() called.")
         dialogHelper.showAboutDialog(this) // Gunakan context Activity Map ini
    }

    // Implementasi dari BaseMapActivity: Menampilkan dialog update utama. Sama persis untuk kedua flavor.
    override fun showUpdateAvailableDialog(updateInfo: YourUpdateModel) {
        Log.d(TAG, "showUpdateAvailableDialog() called.")
        dialogHelper.showUpdateDialog(
            context = this, // Gunakan context Activity Map ini
            updateInfo = updateInfo.changelog, // Menggunakan data dari YourUpdateModel
            onUpdateClicked = {
                Log.d(TAG, "Update dialog: Update clicked.")
                viewModel.startDownload(updateInfo) // Panggil ViewModel untuk mulai download
                // Jika dialog update utama disimpan dan perlu di-dismiss di sini
                // dialog.dismiss()
            },
            onCancelClicked = {
                 Log.d(TAG, "Update dialog: Cancel clicked.")
                viewModel.postponeUpdate() // Panggil ViewModel untuk tunda update
                 // Jika dialog update utama disimpan dan perlu di-dismiss di sini
                 // dialog.dismiss()
            }
        )
    }

    // Implementasi dari BaseMapActivity: Menampilkan/menyembunyikan dialog Xposed missing.
    // Method abstract showXposedMissingDialog DIHAPUS dari BaseMapActivity
    // karena dialog Xposed Missing dikelola sepenuhnya di BaseMapActivity.

    // Implementasi dari BaseMapActivity: Menampilkan token Gojek.
    // Method abstract showGojekToken DIHAPUS dari BaseMapActivity
    // karena dialog Token Gojek dikelola sepenuhnya di BaseMapActivity.


    // =====================================================================
    // Implementasi Interface LocationListener (Callback dari ILocationHelper)
    // Dipanggil helper saat hasil operasi lokasi. SAMA PERSIS UNTUK KEDUA FLAVOR!
    // Menggunakan Location object standar Android.
    // =====================================================================

    // onLocationResult, onLocationError, onPermissionGranted, onPermissionDenied -> IMPLEMENTASI SAMA PERSIS DENGAN SEBELUMNYA


    // =====================================================================
    // Implementasi Interface PermissionResultListener (Callback dari PermissionHelper)
    // Dipakai untuk hasil permission SELAIN lokasi, misal Notifikasi.
    // Di-trigger dari BaseMapActivity.onRequestPermissionsResult().
    // SAMA PERSIS UNTUK KEDUA FLAVOR!
    // =====================================================================

    // onPermissionResult, onPermissionsResult -> IMPLEMENTASI SAMA PERSIS DENGAN SEBELUMNYA


    // =====================================================================
    // Implementasi Listener Map Callback MapLibre
    // =====================================================================

    // Implementasi dari OnMapReadyCallback MapLibre: Dipanggil saat map MapLibre sudah siap
    override fun onMapReady(mapLibreMap: MapLibreMap) {
         this.mapLibreMap = mapLibreMap // Simpan objek MapLibreMap

         Timber.d("MapLibreMap is ready")

         // Inisialisasi AnnotationManager jika pakai MapLibre v10+ Annotation Plugin
         // if (mapLibreMap.getStyle() != null && pointAnnotationManager == null) {
         //     pointAnnotationManager = mapLibreMap.annotations.createPointAnnotationManager()
         // }

         // Memuat style map MapLibre (API MapLibre)
         mapLibreMap.setStyle(Style.Builder().fromUri("asset://osm_bright.json")) { style -> // Ganti URI style jika perlu
            Timber.d("MapLibre Style loaded")
            // Setup setelah style dimuat...

            // Pindah kamera ke lokasi awal (misal dari lat/lon di Base)
             val initialLatLng = org.maplibre.android.geometry.LatLng(this@MapActivity.lat, this@MapActivity.lon) // LatLng MapLibre
             val initialCameraPosition = CameraPosition.Builder().target(initialLatLng).zoom(15.0).build()
             mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(initialCameraPosition))
             Timber.d("MapLibre camera moved to initial location: ${initialLatLng.latitude}, ${initialLatLng.longitude}")

            // Setup click listener pada map MapLibre (API MapLibre)
            mapLibreMap.addOnMapClickListener { clickedLatLng -> // Parameter click listener LatLng MapLibre
                 mLatLng = clickedLatLng // Set mLatLng MapLibre
                 Timber.d("MapLibre clicked at: ${clickedLatLng.latitude}, ${clickedLatLng.longitude}")

                updateMarker(clickedLatLng) // Panggil method helper spesifik flavor
                 // mapLibreMap.animateCamera(CameraUpdateFactory.newLatLng(clickedLatLng)) // Optional: Gerakkan kamera

                this@MapActivity.lat = clickedLatLng.latitude // Sinkronisasi ke Base
                this@MapActivity.lon = clickedLatLng.longitude // Sinkronisasi ke Base

                viewModel.triggerReverseGeocoding(clickedLatLng.latitude, clickedLatLng.longitude) // Panggil ViewModel

                true // Mengonsumsi event
            }
         }
    }


    // =====================================================================
    // Metode Helper Spesifik MapActivity (MapLibre) - Untuk UI/Map
    // =====================================================================

    // Update atau tambah marker di map MapLibre
    // Menggunakan LatLng MapLibre. Implementasi Annotations API lama.
    private fun updateMarker(latLng: org.maplibre.android.geometry.LatLng, title: String? = "Lokasi") { // Menerima LatLng MapLibre
         if (!::mapLibreMap.isInitialized) return // Pastikan map sudah siap

         if (currentMarker == null) {
             Timber.d("Adding new marker at ${latLng.latitude}, ${latLng.longitude}")
             currentMarker = mapLibreMap.addMarker(MarkerOptions().position(latLng).title(title)) // API MapLibre lama
         } else {
             Timber.d("Updating existing marker to ${latLng.latitude}, ${latLng.longitude}")
             currentMarker?.apply { position = latLng; this.title = title } // API MapLibre lama
         }
    }
     // Jika pakai Annotations API v10+ (lebih disarankan):
     /*
     private fun updateMarker(latLng: org.maplibre.android.geometry.LatLng, title: String? = "Lokasi") {
          pointAnnotationManager?.let { manager ->
              currentMarker?.let { oldMarker -> manager.delete(oldMarker) } // Hapus marker lama
              val pointAnnotationOptions = PointAnnotationOptions().withPoint(Point.fromLngLat(latLng.longitude, latLng.latitude)).withTextField(title).withIconImage("marker-icon")
              currentMarker = manager.create(pointAnnotationOptions) // Simpan referensi annotation
          }
     }
     */


    // Hapus marker dari map MapLibre
    // Implementasi Annotations API lama.
    private fun removeMarker() {
         if (!::mapLibreMap.isInitialized) return // Pastikan map sudah siap
        Timber.d("Removing marker")
        currentMarker?.remove() // API MapLibre lama
        currentMarker = null
    }
     // Jika pakai Annotations API v10+ (lebih disarankan):
     /*
     private fun removeMarker() {
          Timber.d("Removing marker")
          pointAnnotationManager?.deleteAll() // Hapus semua marker AnnotationManager
          currentMarker = null // Pastikan referensi lokal null
     }
     */


    // Metode internal Activity untuk dismiss dialog hasil parsial search jika di-manage di sini.
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    private fun dismissPartialSearchResultsDialog() {
        // Implementasi ini tergantung gimana dialogHelper.showPartialSearchResultsDialog bekerja.
        // dialogHelper.dismissPartialSearchResultsDialog() // Asumsi method ini ada di DialogHelper
    }

}
