package com.roxgps.ui // Pastikan package ini sesuai

//import org.maplibre.android.annotations.MarkerOptions
// Hapus import untuk Annotation API baru (PointAnnotationManager, dll.)
// import org.maplibre.maps.plugin.annotation.annotations
// import org.maplibre.maps.plugin.annotation.generated.PointAnnotationManager
// import org.maplibre.maps.plugin.annotation.generated.PointAnnotationOptions
// import org.maplibre.maps.plugin.annotation.generated.createPointAnnotationManager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.collection.isNotEmpty
import androidx.lifecycle.lifecycleScope
import com.roxgps.R
import com.roxgps.data.FakeLocationData
import com.roxgps.helper.LocationListener
import com.roxgps.helper.PermissionResultListener
import com.roxgps.service.BackgroundTaskService
import com.roxgps.utils.NotificationsChannel
import com.roxgps.utils.Relog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import timber.log.Timber

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
    companion object {private const val TAG = "MapActivityLibre"}
     // Ubah TAG agar beda dengan flavor Google

    // =====================================================================
    // Properti Spesifik MapActivity (MapLibre Flavor)
    // =====================================================================
    // Objek MapLibreMap, dijamin tidak null setelah onMapReady MapLibre
    private lateinit var mapLibreMap: MapLibreMap // Untuk flavor MapLibre

    // PERBAIKAN: Deklarasikan MapView MapLibre
    private lateinit var mapView: MapView // <<< Deklarasi MapView MapLibre
    private var currentSource: GeoJsonSource? = null
    private val sourceId = "location-source"
    private val layerId = "location-layer"

    // Variable untuk menyimpan LatLng spesifik flavor lokasi yang sedang aktif/ditampilkan/dipilih user.
    private var mLatLng: LatLng? = null // <-- Menggunakan LatLng MapLibre
    // Tambahkan variabel untuk SymbolManager (API Lama)
    private var symbolManager: SymbolManager? = null // Variabel untuk SymbolManager

    // Hapus variabel untuk PointAnnotation yang baru
    // private var currentPointAnnotation: org.maplibre.maps.plugin.annotation.generated.PointAnnotation? = null

    // Variabel untuk menyimpan referensi Symbol (API Lama) yang terakhir ditambahkan
    private var currentSymbol: Symbol? = null
    // Properti Dialog & Adapter (sama dengan Google)
    //private var favListAdapter: FavListAdapter = FavListAdapter() // Adapter ini spesifik ke UI list favorit, manage di sini

    // =====================================================================
    // Properti Helper (Di-inject)
    // =====================================================================
    // Helper Lokasi - Tipe Interface ILocationHelper. Implementasi MapLibreLocationHelper di-inject DI SINI
    // sesuai dengan @Binds di ActivityModule flavor 'foss'.
    /*@Inject // <-- Injeksi Helper Lokasi Spesifik Flavor (ILocationHelper)
    lateinit var locationHelper: ILocationHelper // Hilt akan meng-inject MapLibreLocationHelper
*/

    // Helper Permission, Notifikasi, Search, Dialog.
    // Mereka sudah di-inject di BaseMapActivity dan bersifat PROTECTED.
    // Akses properti mereka dari Base: this.permissionHelper, this.notificationsChannel, this.dialogHelper


    // =====================================================================
    // Implementasi Metode Lifecycle Activity
    // Panggil super, MapView/Fragment lifecycle, dan daftar/lepas helper/receiver.
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // super.onCreate() di Base akan menginisialisasi binding, viewModel, dll.
        super.onCreate(savedInstanceState)

        // PERBAIKAN: Akses MapView dari binding root dan panggil lifecycle onCreate
        // Pastikan ID mapView ada di map_container.xml
        mapView = binding.root.findViewById(R.id.mapView) // <<< Akses MapView dari binding root
        mapView.onCreate(savedInstanceState) // <<< Panggil lifecycle onCreate MapView
        // Menggunakan API Baru untuk memuat style peta
        mapView.getMapAsync(this)
        // Tidak perlu panggil observasi ViewModel di sini lagi!
        // Semua observasi StateFlow, LiveData, dan SharedFlow DILAKUKAN DI BaseMapActivity.observeViewModel()
        // Method observeViewModelState() dan observeViewModelEvents() di MapActivity DIHAPUS.

        // Inisialisasi SDK MapLibre JIKA dilakukan di Activity (kurang disarankan di sini, lebih baik di Application class)
        // MapLibre.getInstance(this) // Jika inisialisasi SDK di Activity (kurang disarankan)

        // Handle Intent (jika Activity diluncurkan dengan data/action tertentu) - Logic di Base
        // handleIntent(intent) // Sudah dipanggil di Base. Bisa di-override di sini kalau perlu logic tambahan.
    }

    // === Metode Lifecycle onStart, onResume, onPause, onStop, onSaveInstanceState, onLowMemory, onDestroy ===
    // Implementasi ini mengasumsikan BaseMapActivity menggunakan MapView MapLibre dan memanggil lifecycle-nya.
    /*override fun onStart() {
        super.onStart()
        Timber.tag(TAG).d("onStart()")
        // PERBAIKAN: Panggil lifecycle onStart MapView
        mapView.onStart() // <<< Panggil lifecycle onStart MapView

        // Mendaftar atau mengaktifkan listener yang butuh onStart
    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("onResume()")
        // PERBAIKAN: Panggil lifecycle onResume MapView
        mapView.onResume() // <<< Panggil lifecycle onResume MapView

        // Mendaftar BroadcastReceiver notifikasi jika NotificationHelper membutuhkannya di sini
        // notificationsChannel.registerReceiver(this) // <-- Mengakses notificationsChannel dari Base
    }

    override fun onPause() {
        super.onPause()
        Timber.tag(TAG).d("onPause()")
        // PERBAIKAN: Panggil lifecycle onPause MapView
        mapView.onPause() // <<< Panggil lifecycle onPause MapView

        // Melepas BroadcastReceiver notifikasi jika NotificationHelper membutuhkannya di sini
        // notificationsChannel.unregisterReceiver(this) // <-- Mengakses notificationsChannel dari Base
    }

    override fun onStop() {
        super.onStop()
        Timber.tag(TAG).d("onStop()")
        // PERBAIKAN: Panggil lifecycle onStop MapView
        mapView.onStop() // <<< Panggil lifecycle onStop MapView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.tag(TAG).d("onSaveInstanceState()")
        // PERBAIKAN: Panggil lifecycle onSaveInstanceState MapView
        mapView.onSaveInstanceState(outState) // <<< Panggil lifecycle onSaveInstanceState MapView
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.tag(TAG).d("onLowMemory()")
        // PERBAIKAN: Panggil lifecycle onLowMemory MapView
        mapView.onLowMemory() // <<< Panggil lifecycle onLowMemory MapView
    }*/

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("onDestroy()")
        // Menghentikan update lokasi menggunakan Helper saat Activity dihancurkan
        locationHelper.stopLocationUpdates() // Panggil helper lokasi (ILocationHelper)

        // PERBAIKAN: Panggil lifecycle onDestroy MapView
        mapView.onDestroy() // <<< Panggil lifecycle onDestroy MapView

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
        return symbolManager?.annotations?.isNotEmpty() == true
        // Jika pakai Annotations API v10+: return pointAnnotationManager?.annotations?.isNotEmpty() == true
    }

    // Implementasi dari BaseMapActivity: Inisialisasi Map MapLibre
    override fun initializeMap() {
        Timber.tag(TAG).d("Initializing MapLibreMap async.")
        // PERBAIKAN: Memuat map secara asynchronous MapLibre dari MapView yang sudah diinisialisasi
        mapView.getMapAsync(this) // <<< Panggil getMapAsync dari MapView
    }

    // Implementasi dari BaseMapActivity: Pindah kamera map ke lokasi baru menggunakan MapLibre API.
    @SuppressLint("TimberArgCount")
    override fun moveMapToNewLocation(location: Location, moveNewLocation: Boolean) { // Menerima Location objek standar Android
        Timber.tag(TAG).d("Moving map to location: ${location.latitude}, ${location.longitude}")

        // Pastikan mapLibreMap sudah diinisialisasi
        if (!::mapLibreMap.isInitialized) {
            Timber.e(TAG, "mapLibreMap not initialized, cannot move camera or add symbol.")
            return
        }

        // Hapus symbol lama jika ada
        currentSymbol?.let { symbol ->
            symbolManager?.delete(symbol) // Hapus symbol lama
            currentSymbol = null // Set referensi ke null
        }

        // Buat SymbolOptions untuk symbol baru (API Lama)
        val symbolOptions: SymbolOptions = SymbolOptions()
            .withLatLng(LatLng(location.latitude, location.longitude)) // Menggunakan LatLng dari API Lama
            // Kamu perlu menyediakan ikon untuk symbol.
            // Ikon harus ditambahkan ke peta SEBELUM membuat Symbol.
            // Ini berbeda dengan Annotation API baru yang bisa langsung pakai Bitmap.
            // Kamu perlu memuat gambar (misal dari drawable) dan menambahkannya ke Style peta
            // menggunakan Style.addImage().
            // Contoh: .withIconImage("red_marker_icon_id") // Ganti "red_marker_icon_id" dengan ID yang kamu gunakan saat menambahkan ikon ke Style
            // Jika ikon belum ditambahkan ke Style, Symbol tidak akan muncul.

            // Untuk contoh ini, kita asumsikan kamu sudah menambahkan ikon dengan ID "marker-icon" ke Style
            .withIconImage("marker-icon") // Ganti dengan ID ikon kamu (pastikan sudah ditambahkan ke style)
            .withIconSize(1.0f) // Ukuran ikon (1.0f = ukuran asli)
        // Kamu bisa menambahkan properti lain seperti:
        // .withTextField("Lokasi Palsu") // Teks di atas symbol
        // .withTextColor("red") // Warna teks (String nama warna atau hex)
        // .withTextHaloColor("white") // Warna halo teks
        // .withTextHaloWidth(1.0f) // Lebar halo teks
        // .withDraggable(true) // Membuat symbol bisa di-drag (jika dibutuhkan)

        // Pastikan symbolManager tidak null sebelum digunakan
        symbolManager?.let { manager ->
            // Buat Symbol dari options
            val symbol = manager.create(symbolOptions)
            // Simpan referensi symbol baru
            currentSymbol = symbol
            Timber.d("Added new symbol at ${location.latitude}, ${location.longitude}")
        } ?: run {
            Timber.e(TAG, "SymbolManager is null! Cannot add symbol.")
            // Tampilkan pesan error ke user jika manager null
            lifecycleScope.launch { viewModel.triggerShowToastEvent("Error: Map symbol manager not ready.") }
        }


        val targetLatLng = LatLng(location.latitude, location.longitude)
        mLatLng = targetLatLng // Simpan LatLng terbaru
        val cameraPosition = CameraPosition.Builder().target(targetLatLng).zoom(15.0).build()

        // Pastikan mapLibreMap sudah diinisialisasi sebelum memindahkan kamera
        if (::mapLibreMap.isInitialized) {
            if (moveNewLocation) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            } else {
                mapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
            Timber.d("MapLibre camera moved to ${targetLatLng.latitude}, ${targetLatLng.longitude}")
        } else {
            Timber.e(TAG, "mapLibreMap not initialized, cannot move camera.")
        }

        this@MapActivity.lat = location.latitude
        this@MapActivity.lon = location.longitude
    }

    // Implementasi dari BaseMapActivity: Setup listeners button. Sama persis untuk kedua flavor.
    @SuppressLint("ClickableViewAccessibility")
    override fun setupButtons() {
        Timber.tag(TAG).d("Setting up buttons.")
        // Mengakses referensi tombol dari binding Base (binding sama di kedua flavor)

        // Listener tombol Start (panggil ViewModel.update)
        binding.startButton.setOnClickListener {
            mLatLng?.let { latLng -> // Pastikan mLatLng (LatLng MapLibre) tidak null
                // Buat objek Location dari mLatLng (konversi dari LatLng MapLibre ke Location standar)
                val selectedLocation = Location("user_selected").apply {
                    latitude = latLng.latitude
                    longitude = latLng.longitude
                }
                // PERBAIKAN: Memanggil method update di ViewModel
                // Mengganti panggilan viewModel.update(...) dengan updateStartedState dan updateLocation
                viewModel.updateStartedState(true) // <<< Panggil updateStartedState
                viewModel.updateLocation(selectedLocation.latitude.toFloat(), selectedLocation.longitude.toFloat()) // <<< Panggil updateLocation (parameter Float)

                Timber.tag(TAG).d("Start button clicked. ViewModel update state and location.")


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
                // PERBAIKAN: Memanggil emit di ViewModel.showToastEvent
                lifecycleScope.launch { viewModel.triggerShowToastEvent(getString(R.string.invalid_location)) } // <<< Panggil emit di ViewModel.showToastEvent

            }
        }

        // Listener tombol Stop (panggil performStopButtonClick di Base)
        binding.stopButton.setOnClickListener {
            Relog.i(TAG, "Stop button clicked in Activity. Sending STOP_SERVICE command...")
            //Timber.tag("MapActivity").d("Stop button clicked in Activity")
            // === LOGIKA UNTUK MENGIRIM PERINTAH STOP KE SERVICE DI SINI ===
            val stopServiceIntent = Intent(this, BackgroundTaskService::class.java).apply {
                action = NotificationsChannel.ACTION_STOP_SERVICE // Gunakan aksi perintah stop Service
            }
            // Panggil startService (atau startForegroundService jika target SDK >= 26)
            // Jika Service Anda foreground dan menargetkan O+ (API 26+),
            // lebih baik menggunakan startForegroundService, tapi startService juga bekerja.
            startService(stopServiceIntent) // Mengirim perintah ke Service

            // Opsi: Tutup Activity ini setelah mengirim perintah stop
            // finish()
            // Optional: Jika ingin mencatat LOKASI STOP di ViewModel saat TOMBOL UI diklik:
            processStopLocationUpdate()
        }

        // Listener tombol Add Favorite (panggil addFavoriteDialog)
        binding.addfavorite.setOnClickListener {
            Timber.tag(TAG).d("Add Favorite button clicked. Showing dialog.")
            addFavoriteDialog() // <-- Panggil method abstract di MapActivity ini
        }

        // Listener tombol "Dapatkan Lokasi Asli/Mock" (Jika ada, panggil locationHelper)
        binding.getlocation.setOnClickListener {
            Timber.tag(TAG).d("Get Location button clicked. Requesting updates.")
            locationHelper.requestLocationUpdates(this) // 'this' karena MapActivity mengimplementasikan LocationListener. locationHelper adalah ILocationHelper.
        }

        // Setup search bar listener di BaseMapActivity (panggil ViewModel.searchAddress)
        // Logic listener ini ada di BaseMapActivity.setupNavView()
        // MapActivity ini akan mengamati Flow searchResult dari ViewModel di observeViewModelState().
    }


    // Implementasi dari BaseMapActivity: Mendapatkan lokasi terakhir (panggil locationHelper). Sama persis untuk kedua flavor.
    @SuppressLint("MissingPermission") // Suppress karena permission dicek oleh Helper ILocationHelper
    override fun getLastLocation() {
        Timber.tag(TAG).d("getLastLocation() called. Requesting Location updates via helper.")
        // locationHelper.getLastLocation(this) // Opsi: Panggil getLastLocation di helper
        locationHelper.requestLocationUpdates(this) // Panggil method di ILocationHelper (this = MapActivity implementing LocationListener)
    }

    // Implementasi dari BaseMapActivity: Menangani kesalahan terkait lokasi. Sama persis untuk kedua flavor.
    /*override fun handleLocationError() {
        Timber.tag(TAG).w("Handling Location Error.")
        Snackbar.make(binding.root, "Location services are disabled.", Snackbar.LENGTH_LONG)
            .setAction("Enable") {
                locationHelper.openLocationSettings() // Panggil method di ILocationHelper
            }
            .show()
    }*/

    // Implementasi dari BaseMapActivity: Method abstract onStopButtonClicked. Sama persis untuk kedua flavor.
    override fun onStopButtonClicked() {
        Timber.tag(TAG).d("onStopButtonClicked() called.")
        locationHelper.stopLocationUpdates() // Panggil method di ILocationHelper
        mLatLng?.let { // Pastikan mLatLng (LatLng MapLibre) tidak null
            // Konversi LatLng MapLibre ke Location standar Android
            val stoppedLocation = Location("user_stopped").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
            // PERBAIKAN: Memanggil method update di ViewModel
            // Mengganti panggilan viewModel.update(...) dengan updateStartedState dan updateLocation
            viewModel.updateStartedState(false) // <<< Panggil updateStartedState
            viewModel.updateLocation(stoppedLocation.latitude.toFloat(), stoppedLocation.longitude.toFloat()) // <<< Panggil updateLocation (parameter Float)

        } ?: run { // Jika mLatLng null (seharusnya tidak terjadi kalau tombol Stop visible)
            Timber.tag(TAG).w("onStopButtonClicked called but mLatLng is null.")
        }

        removeMarker() // Panggil method helper spesifik MapLibre di Activity ini (hapus marker)
        // showToast(getString(R.string.location_unset)) // Ditangani oleh showToastEvent dari ViewModel
        // PERBAIKAN: Memanggil emit di ViewModel.showToastEvent
        lifecycleScope.launch { viewModel.triggerShowToastEvent(getString(R.string.location_unset)) } // <<< Panggil emit di ViewModel.showToastEvent

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
    /*override fun showStartNotification(address: String) {
        Timber.tag(TAG).d("showStartNotification() called with address: $address")
        // Panggil method di NotificationHelper dari Base
        notificationsChannel.showStartNotification(address)
    }
*/
    // Implementasi dari BaseMapActivity: Membatalkan notifikasi (panggil notificationHelper dari Base)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    /*override fun cancelNotification() {
        Timber.tag(TAG).d("cancelNotification() called.")
        // Panggil method di NotificationHelper dari Base
        notificationsChannel.cancelNotification()
    }
*/
    // Implementasi dari BaseMapActivity: Menampilkan dialog tambah favorit (panggil dialogHelper dari Base & viewModel)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    override fun addFavoriteDialog() {
        Timber.tag(TAG).d("addFavoriteDialog() called.")
        // Ubah nama metode dari showAddFavoriteDialog menjadi createAddFavoriteDialog
        // dan tambahkan .show() di akhir
        dialogHelper.createAddFavoriteDialog(layoutInflater) { favoriteName ->
            if (mLatLng != null) {
                viewModel.storeFavorite(favoriteName, mLatLng!!.latitude, mLatLng!!.longitude)
            } else {
                lifecycleScope.launch { viewModel.triggerShowToastEvent(getString(R.string.location_not_select)) }
            }
        }.show() // <<< Tambahkan .show() di sini
    }

    // Implementasi dari BaseMapActivity: Menampilkan dialog daftar favorit (panggil dialogHelper dari Base & viewModel)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    /*fun showFavoriteListDialog() { // <<< HAPUS 'override'
        Timber.tag(TAG).d("showFavoriteListDialog() called.")
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allFavList.collectLatest { favList ->
                    Timber.tag(TAG)
                        .d("Fav list collected, showing dialog with ${favList.size} items.")
                    // Ubah nama metode dari showFavoriteListDialog menjadi createFavoriteListDialog
                    // dan tambahkan .show() di akhir
                    dialogHelper.createFavoriteListDialog( // <<< UBAH NAMA METODE
                        layoutInflater = layoutInflater, // Tambahkan parameter layoutInflater
                        favList = favList,
                        onItemClick = { favorite ->
                            val favLocation = Location("favorite").apply {
                                latitude = favorite.lat!!
                                longitude = favorite.lng!!
                            }
                            this@MapActivity.lat = favLocation.latitude
                            this@MapActivity.lon = favLocation.longitude
                            moveMapToNewLocation(favLocation, true)
                            // Logika dismiss dialog setelah klik item tetap di Activity melalui callback
                        },
                        onItemDelete = { favorite ->
                            Timber.tag(TAG).d("Favorite item deleted: ${favorite.address}")
                            viewModel.deleteSingleFavorite(favorite)
                        }
                        // Parameter favListAdapter tidak dibutuhkan di createFavoriteListDialog
                        // favListAdapter = favListAdapter // <<< HAPUS PARAMETER INI
                    ).show() // <<< Tambahkan .show() di sini
                }
            }
        }
    }
    fun showAboutDialog() { // <<< HAPUS 'override'
        Timber.tag(TAG).d("showAboutDialog() called.")
        // Ubah nama metode dari showAboutDialog menjadi createAboutDialog
        // dan tambahkan .show() di akhir.
        // Pastikan juga memberikan parameter layoutInflater.
        dialogHelper.createAboutDialog(layoutInflater).show() // <<< UBAH PANGGILAN DAN TAMBAHKAN .show()
    }*/
    // Implementasi dari BaseMapActivity: Menampilkan dialog update utama. Sama persis untuk kedua flavor.
    /*override fun showUpdateAvailableDialog(updateInfo: YourUpdateModel) {
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
*/
    // Implementasi dari BaseMapActivity: Menampilkan/menyembunyikan dialog Xposed missing.
    // Method abstract showXposedMissingDialog DIHAPUS dari BaseMapActivity
    // karena dialog Xposed Missing dikelola sepenuhnya di BaseMapActivity.

    // Implementasi dari BaseMapActivity: Menampilkan token .
    // Method abstract showToken DIHAPUS dari BaseMapActivity
    // karena dialog Token  dikelola sepenuhnya di BaseMapActivity.


    // =====================================================================
    // Implementasi Interface LocationListener (Callback dari ILocationHelper)
    // Dipanggil helper saat hasil operasi lokasi. SAMA PERSIS DENGAN KEDUA FLAVOR!
    // Menggunakan Location object standar Android.
    // =====================================================================

    // PERBAIKAN: Menambahkan implementasi untuk onLocationResult
    override fun onLocationResult(location: Location) {
        Timber.tag(TAG)
            .d("onLocationResult received: Lat=${location.latitude}, Lon=${location.longitude}, Accuracy=${location.accuracy}")
        // TODO: Lakukan sesuatu dengan lokasi yang diterima
        // Misalnya, pindahkan peta ke lokasi baru
        moveMapToNewLocation(location, true) // Panggil method di MapActivity ini
        // Update ViewModel/PrefManager dengan lokasi baru
        viewModel.updateLocation(location.latitude.toFloat(), location.longitude.toFloat()) // Panggil method ViewModel
        // Optional: Tampilkan info lokasi di UI
        // binding.textViewLocationInfo.text = "Lat: ${location.latitude}, Lon: ${location.longitude}"
    }

    // PERBAIKAN: Menambahkan implementasi untuk onLocationError
    override fun onLocationError(errorMessage: String) {
        Timber.tag(TAG).e("onLocationError received: $errorMessage")
        // TODO: Tangani error lokasi
        // Misalnya, tampilkan pesan Toast atau Snackbar ke pengguna
        // showToast("Location Error: $errorMessage") // Menggunakan extension function showToast
        lifecycleScope.launch { viewModel.triggerShowToastEvent("Location Error: $errorMessage") } // Emit Toast via event
    }

    // onPermissionGranted, onPermissionDenied -> IMPLEMENTASI SAMA PERSIS DENGAN SEBELUMNYA


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
            val initialLatLng =
                LatLng(this@MapActivity.lat, this@MapActivity.lon) // LatLng MapLibre
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
    private fun updateMarker(latLng: LatLng, title: String? = "Lokasi") {
        if (!::mapLibreMap.isInitialized) {
            Timber.d("Map belum siap")
            return
        }

        val point = Point.fromLngLat(latLng.longitude, latLng.latitude)
        val feature = Feature.fromGeometry(point)
        title?.let { feature.addStringProperty("title", it) }

        mapLibreMap.style?.let { style ->
            if (currentSource == null) {
                Timber.d("Adding new marker at ${latLng.latitude}, ${latLng.longitude}")

                // Buat source baru dengan data GeoJSON
                currentSource = GeoJsonSource(sourceId).apply {
                    setGeoJson(feature)
                }

                // Tambahkan source ke style
                style.addSource(currentSource!!)

                // Buat dan tambahkan circle layer jika belum ada
                if (style.getLayer(layerId) == null) {
                    val circleLayer = CircleLayer(layerId, sourceId)
                    circleLayer.setProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor("#FF0000"), // Merah
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF") // Outline putih
                    )
                    style.addLayer(circleLayer)
                }
            } else {
                Timber.d("Updating existing marker to ${latLng.latitude}, ${latLng.longitude}")
                // Update posisi dengan setGeoJson
                currentSource?.setGeoJson(feature)
            }
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
        Timber.d("Removing marker (Symbol)")
        // Hapus symbol menggunakan manager
        currentSymbol?.let { symbol ->
            symbolManager?.delete(symbol) // Hapus symbol
            currentSymbol = null // Set referensi ke null
            Timber.d("Symbol removed.")
        } ?: run {
            Timber.d("removeMarker called but no currentSymbol found.")
        }
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
    /*private fun dismissPartialSearchResultsDialog() {
        // Implementasi ini tergantung gimana dialogHelper.showPartialSearchResultsDialog bekerja.
        // dialogHelper.dismissPartialSearchResultsDialog() // Asumsi method ini ada di DialogHelper
    }*/
    /*
    override fun requestLocationUpdates(listener: LocationListener) {
        // Memanggil metode requestLocationUpdates dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.requestLocationUpdates(this)
    }
    override fun stopLocationUpdates() {
        // Memanggil metode stopLocationUpdates dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.stopLocationUpdates()
    }
    // Di dalam file MapActivity.kt, di dalam kelas MapActivity { ... }
    // Implementasi metode abstrak getLastKnownLocation dari BaseMapActivity
    override fun getLastKnownLocation(): Location? {
        // Memanggil metode getLastKnownLocation dari locationHelper yang di-inject
        // dan mengembalikan hasilnya.
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        return locationHelper.getLastKnownLocation()
    }
    // Di dalam file MapActivity.kt, di dalam kelas MapActivity { ... }

// ... properti dan metode yang sudah ada ...

    // Implementasi metode abstrak isLocationServiceEnabled dari BaseMapActivity
    override fun isLocationServiceEnabled(): Boolean {
        // Memanggil metode isLocationServiceEnabled dari locationHelper yang di-inject
        // dan mengembalikan hasilnya.
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        return locationHelper.isLocationServiceEnabled()
    }
    // Di dalam file MapActivity.kt, di dalam kelas MapActivity { ... }

// ... properti dan metode yang sudah ada ...

    // Implementasi metode abstrak checkLocationPermissions dari BaseMapActivity
    override fun checkLocationPermissions(): Boolean {
        // Memanggil metode checkLocationPermissions dari locationHelper yang di-inject
        // dan mengembalikan hasilnya.
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        return locationHelper.checkLocationPermissions()
    }
    // Di dalam file MapActivity.kt, di dalam kelas MapActivity { ... }

// ... properti dan metode yang sudah ada ...

    // Implementasi metode abstrak openLocationSettings dari BaseMapActivity
    override fun openLocationSettings(context: Context) {
        // Memanggil metode openLocationSettings dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.openLocationSettings(context)
    }
    // Di dalam file MapActivity.kt, di dalam kelas MapActivity { ... }

// ... properti dan metode yang sudah ada ...

    // Implementasi metode abstrak openAppPermissionSettings dari BaseMapActivity
    override fun openAppPermissionSettings(context: Context) {
        // Memanggil metode openAppPermissionSettings dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.openAppPermissionSettings(context)
    }

    override fun startRealLocationUpdates() {
        // Memanggil metode startRealLocationUpdates dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.startRealLocationUpdates()
    }
    override fun stopRealLocationUpdates() {
        // Memanggil metode startRealLocationUpdates dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.stopRealLocationUpdates()
    }
    override fun startFaking(targetLocation: Location) {
        // Memanggil metode startFaking dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.startFaking(targetLocation)
    }
    override fun stopFaking() {
        // Memanggil metode stopFaking dari locationHelper yang di-inject
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        locationHelper.stopFaking()
    }
    override val isFakingActive: StateFlow<Boolean>
        get() = locationHelper.isFakingActive
    override val currentFakeLocation: StateFlow<Location?>
        get() = locationHelper.currentFakeLocation
    override fun getFakeLocationData(
        isRandomPositionEnabled: Boolean,
        accuracy: Float,
        randomRange: Int,
        updateIntervalMs: Long,
        desiredSpeed: Float
        // ... parameter lain jika ada di BaseMapActivity ...
    ): FakeLocationData? {
        // Memanggil metode getFakeLocationData dari locationHelper yang di-inject
        // dan mengembalikan hasilnya.
        // LocationHelper di-inject di BaseMapActivity, dan MapActivity menggunakannya.
        // Pastikan locationHelper sudah diinisialisasi sebelum dipanggil.
        // Kamu sudah punya @Inject lateinit var locationHelper: ILocationHelper di BaseMapActivity
        // Jadi seharusnya sudah tersedia di sini.
        return locationHelper.getFakeLocationData(
            isRandomPositionEnabled,
            accuracy,
            randomRange,
            updateIntervalMs,
            desiredSpeed
            // ... teruskan parameter lain jika ada ...
        )
    }*/
    private fun processStopLocationUpdate() {
        // Metode ini hanya perlu menangani update state ViewModel/PrefManager dengan lokasi saat stop.
        // Update UI (visibilitas tombol, marker) DITANGANI OLEH observer ViewModel.
        Relog.i(TAG, "processStopLocationUpdate called. Updating ViewModel state...") // Menggunakan TAG
        mLatLng?.let { // Pastikan mLatLng (LatLng spesifik flavor) tidak null
            // Konversi LatLng spesifik flavor ke Location standar Android
            val stoppedLocation = Location("user_stopped").apply {
                // Untuk flavor Google Maps (sesuaikan jika flavor lain pakai tipe LatLng berbeda):
                latitude = it.latitude
                longitude = it.longitude
            }
            // Panggil ViewModel dengan Location standar
            // Ini yang memicu update isStarted = false dan lokasi berhenti di PrefManager
            // Update state lokasi berhenti di ViewModel/PrefManager
            // Method ini dipanggil dari UI button listener jika ingin mencatat lokasi stop dari UI.
            viewModel.setSearchedLocation(stoppedLocation)
            // Optional: Lakukan aksi lain yang hanya perlu dilakukan saat proses stop berhasil
            // Misalnya, perbarui UI di Activity bahwa proses sudah berhenti
            // updateUiToStoppedState()
        } ?: run { // Jika mLatLng null (seharusnya tidak terjadi kalau tombol Stop visible)
            Relog.i(TAG, "processStopLocationUpdate called but mLatLng is null. Cannot update stopped location.")
        }
    }

}
