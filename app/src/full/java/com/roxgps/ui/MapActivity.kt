package com.roxgps.ui // Pastikan package ini sesuai

// =====================================================================
// Import Library MapActivity (Setelah Refactoring Observasi & Integrasi Token)
// =====================================================================

// Import dari androidx atau Material yang dibutuhkan di MapActivity
// import androidx.activity.viewModels // ViewModel di-inject di Base, tidak perlu di sini
// import androidx.lifecycle.ViewModel // Tidak perlu import tipe ViewModel
// Import MapLibre atau Google Maps API spesifik (sesuaikan flavor)
// import org.maplibre.android.MapLibre // MapLibre
// import org.maplibre.android.maps.* // MapLibre
// import org.maplibre.android.geometry.LatLng // MapLibre
// import org.maplibre.android.annotations.* // MapLibre Annotations lama
// import com.google.android.gms.maps.GoogleMap // Google Maps
// import com.google.android.gms.maps.OnMapReadyCallback // Google Maps
// import com.google.android.gms.maps.SupportMapFragment // Google Maps
// import com.google.android.gms.maps.CameraUpdateFactory // Google Maps
// import com.google.android.gms.maps.model.LatLng // Google Maps
// import com.google.android.gms.maps.model.Marker // Google Maps
// import com.google.android.gms.maps.model.MarkerOptions // Google Maps
// import com.google.android.gms.maps.model.BitmapDescriptorFactory // Google Maps

// Import Hilt untuk Field Injection Helper (Hanya helper spesifik flavor)
// Import resource, binding, viewmodel, utils, dll.
// Import Material Components (jika dipakai di dialog)
// Import Semua Helper dan Listener (Di-inject di Base atau di MapActivity ini)
// Import Sealed class State dari Repository/Helper BARU
// Import hook class YourUpdateModel

// Import Google Maps spesifik untuk flavor Google, MapLibre spesifik untuk flavor MapLibre
// Contoh untuk FLAVOR GOOGLE MAPS:

// Contoh untuk FLAVOR MAPLIBRE:
// import org.maplibre.android.MapLibre // MapLibre
// import org.maplibre.android.maps.* // MapLibre
// import org.maplibre.android.geometry.LatLng // MapLibre LatLng (Beda dengan Google)
// import org.maplibre.android.annotations.* // MapLibre Annotations lama


// Import Hilt untuk Field Injection (hanya untuk helper spesifik flavor)
// Import yang mungkin tidak diperlukan di Activity kalau logic di Helper
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.roxgps.R
import com.roxgps.adapter.FavListAdapter
import com.roxgps.helper.LocationListener
import com.roxgps.helper.PermissionResultListener
import com.roxgps.service.BackgroundTaskService
import com.roxgps.utils.NotificationsChannel
import com.roxgps.utils.Relog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

// =====================================================================
// Class MapActivity (Contoh untuk FLAVOR GOOGLE MAPS - Setelah Refactoring)
// = implements OnMapReadyCallback (Google), LocationListener, PermissionResultListener
// =====================================================================

// Anotasi Hilt untuk Dependency Injection (Sudah ada di Base)
@AndroidEntryPoint
// MapActivity meng-extend BaseMapActivity, mengimplementasikan callback map (sesuai flavor),
// DAN listener callback dari Helper (LocationListener, PermissionResultListener)
class MapActivity : BaseMapActivity(), OnMapReadyCallback, LocationListener, PermissionResultListener { // Implement OnMapReadyCallback Google untuk flavor Google

    // === Perbaiki TAG ===
    companion object {
        private const val TAG = "MapActivity" // Gunakan const val di companion object
    }

    // =====================================================================
    // Properti Spesifik MapActivity (Google Maps Flavor)
    // =====================================================================
    // Objek GoogleMap, dijamin tidak null setelah onMapReady GoogleMap
    private lateinit var googleMap: GoogleMap // Untuk flavor Google Maps
    // Untuk flavor MapLibre: private lateinit var mapLibreMap: MapLibreMap


    // Variable untuk menyimpan LatLng spesifik flavor lokasi yang sedang aktif/ditampilkan/dipilih user.
    private var mLatLng: LatLng? = null // <-- Menggunakan LatLng Google Maps
    // Untuk flavor MapLibre: private var mLatLng: org.maplibre.android.geometry.LatLng? = null

    // Marker yang sedang aktif di map.
    private var currentMarker: Marker? = null // <-- Menggunakan Marker Google Maps
    // Untuk flavor MapLibre (Annotations lama): private var currentMarker: org.maplibre.android.annotations.Marker? = null
    // Untuk flavor MapLibre (Annotations v10+): private var pointAnnotationManager: PointAnnotationManager? = null


    // Referensi dialog download progress (sama dengan MapLibre) - DIPINDAH KE BASE
    // private var downloadProgressDialog: AlertDialog? = null

    // Dialog untuk notifikasi Xposed missing (sama dengan MapLibre) - DIPINDAH KE BASE
    // private var xposedDialog: AlertDialog? = null

     // Dialog untuk notifikasi Token  (di-manage di Base, hanya di-trigger dari sini)
    // private var TokenDialog: AlertDialog? = null // <-- TIDAK PERLU DISIMPAN DI SINI, manage di Base


    // Properti Dialog & Adapter (sama dengan MapLibre)
    private var favListAdapter: FavListAdapter = FavListAdapter() // Adapter ini spesifik ke UI list favorit, manage di sini


    // Referensi dialog hasil parsial search (jika menampilkan di Activity) - DIPINDAH KE BASE jika di-manage di sana
    // private var partialSearchResultsDialog: AlertDialog? = null


    // =====================================================================
    // Properti Helper (Di-inject)
    // =====================================================================
    // Helper Lokasi - Tipe Interface ILocationHelper. Implementasi spesifik flavor di-inject DI SINI
    // sesuai dengan @Binds di ActivityModule flavor masing-masing.
    //@Inject // <-- Injeksi Helper Lokasi Spesifik Flavor (ILocationHelper)
    //lateinit var locationHelper: ILocationHelper // Hilt akan meng-inject GoogleLocationHelper atau MapLibreLocationHelper

    // NotificationHelper - Di-inject di Base, akses via super.notificationsChannel
    // DialogHelper - Di-inject di Base, akses via super.dialogHelper
    // PermissionHelper - Di-inject di Base, akses via super.permissionHelper
    // SearchHelper - Tidak di-inject di Activity, logic di ViewModel/Repository

    // Jika NotificationHelper perlu di-inject di sini karena ada aksi notifikasi spesifik flavor
    // @Inject lateinit var notificationHelper: NotificationHelper


    // =====================================================================
    // Implementasi Metode Lifecycle Activity
    // Panggil super, MapView/Fragment lifecycle, dan daftar/lepas helper/receiver.
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // super.onCreate() di Base akan menginisialisasi binding, viewModel, dll. DAN MapView.onCreate() jika Base menggunakan MapView.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map) // Pastikan layout Anda punya ID tombol stop

        // Tidak perlu panggil observasi ViewModel di sini lagi!
        // Semua observasi StateFlow, LiveData, dan SharedFlow DILAKUKAN DI BaseMapActivity.observeViewModelState() dan observeViewModelEvents()
        // Method observeViewModelState() dan observeViewModelEvents() di MapActivity DIHAPUS.

        // MapView lifecycle onCreate dipanggil di Base Activity onCreate jika Base menggunakan MapView
        // Jika Base menggunakan SupportMapFragment (umumnya lebih disarankan untuk Google Maps):
        // SupportMapFragment lifecycle dikelola oleh FragmentManager.

        // Handle Intent (jika Activity diluncurkan dengan hook/action tertentu) - Logic di Base
        // handleIntent(intent) // Sudah dipanggil di Base. Bisa di-override di sini kalau perlu logic tambahan.
    }

    // === Metode Lifecycle onStart, onResume, onPause, onStop, onSaveInstanceState, onLowMemory, onDestroy ===
    // Implementasi ini tergantung apakah Base menggunakan MapView (perlu panggil lifecycle MapView)
    // atau SupportMapFragment (tidak perlu). Sesuaikan dengan implementasi Base lo.
    // BaseMapActivity yang lo kasih terakhir menggunakan SupportMapFragment, jadi override lifecycle ini TIDAK PERLU.
    // Jika Base menggunakan MapView Google Maps:
    /*
    override fun onStart() {
        super.onStart()
        // binding.mapContainer.map.onStart() // Asumsi mapContainer adalah MapView Google
    }

    override fun onResume() {
        super.onResume()
        // binding.mapContainer.map.onResume() // Asumsi mapContainer adalah MapView Google
        // notificationHelper.registerReceiver(this) // --> Gunakan helper jika perlu, atau ViewModel emit event
    }

    override fun onPause() {
        super.onPause()
        // binding.mapContainer.map.onPause() // Asumsi mapContainer adalah MapView Google
        // notificationHelper.unregisterReceiver(this) // --> Gunakan helper jika perlu
    }

    override fun onStop() {
        super.onStop()
        // binding.mapContainer.map.onStop() // Asumsi mapContainer adalah MapView Google
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // binding.mapContainer.map.onSaveInstanceState(outState) // Asumsi mapContainer adalah MapView Google
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // binding.mapContainer.map.onLowMemory() // Asumsi mapContainer adalah MapView Google
    }
    */
    override fun onDestroy() {
        super.onDestroy()
        // Menghentikan update lokasi menggunakan Helper saat Activity dihancurkan
        locationHelper.stopLocationUpdates() // Panggil helper lokasi (ILocationHelper)
        // Jika Base menggunakan MapView Google Maps
        // binding.mapContainer.map.onDestroy() // Asumsi mapContainer adalah MapView Google

        // Cleanup resources jika ada di MapActivity spesifik flavor (misal AnnotationManager MapLibre v10+)
        // pointAnnotationManager?.onDestroy() // Untuk MapLibre v10+
    }
    // ===============================================================================================


    // =====================================================================
    // DIHAPUS: Metode Mengamati StateFlow dan LiveData dari ViewModel
    // LOGIKA INI SUDAH ADA DI BaseMapActivity.observeViewModel()
    // =====================================================================
    // private fun observeViewModelState() { /* DIHAPUS */ }
    // private fun observeViewModelEvents() { /* DIHAPUS */ }


    // =====================================================================
    // Implementasi Metode Abstract dari BaseMapActivity
    // Logik panggil Helper atau Map API spesifik flavor Google Maps.
    // =====================================================================

    // Implementasi dari BaseMapActivity: Mengecek status marker
    override fun hasMarker(): Boolean {
        // Untuk flavor Google Maps:
        return currentMarker != null
    }

    // Implementasi dari BaseMapActivity: Inisialisasi Map
    override fun initializeMap() {
        // Untuk flavor Google Maps:
        // Asumsi binding.mapContainer adalah ID dari SupportMapFragment di layout XML
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this) // 'this' refers to MapActivity implementing OnMapReadyCallback Google
        Relog.i(TAG, "Initializing GoogleMap async...") // Menggunakan TAG

        // Jika binding.mapContainer adalah MapView Google, gunakan: binding.mapContainer.getMapAsync(this)

        // Untuk flavor MapLibre:
        // MapLibre.getInstance(this) // Mungkin lebih baik di Application atau BaseMapActivity onCreate
        // binding.mapContainer.map.getMapAsync(this) // 'this' implementing OnMapReadyCallback MapLibre
    }

    // Implementasi dari BaseMapActivity: Pindah kamera map ke lokasi baru
    override fun moveMapToNewLocation(location: Location, moveNewLocation: Boolean) { // Menerima Location objek standar Android
        Relog.i(TAG, "Moving map to location: ${location.latitude}, ${location.longitude}") // Menggunakan TAG
// Pastikan map sudah siap (sesuaikan pengecekan dengan flavor)
        // Untuk flavor Google Maps:
        if (::googleMap.isInitialized) {
            // Membuat LatLng spesifik flavor dari Location objek parameter
            val targetLatLng =
                LatLng(location.latitude, location.longitude) // <-- Menggunakan LatLng Google Maps

            // Set mLatLng di MapActivity ini (properti di MapActivity, spesifik flavor LatLng)
            mLatLng = targetLatLng

            // Gerakkan kamera (API spesifik flavor)
            // Untuk flavor Google Maps:
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(targetLatLng, 15.0f) // Zoom level float di Google
            if (moveNewLocation) { googleMap.animateCamera(cameraUpdate) } else { googleMap.moveCamera(cameraUpdate) }


             // Tambahkan atau pindahkan marker di lokasi baru (panggil method helper spesifik flavor di Activity ini)
             // Method updateMarker membutuhkan LatLng spesifik flavor.
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

    // Implementasi dari BaseMapActivity: Setup listeners button
    // SAMA PERSIS UNTUK KEDUA FLAVOR (panggil ViewModel, Helper umum, atau method abstract di Activity ini).
    @SuppressLint("ClickableViewAccessibility")
    override fun setupButtons() {
        Relog.i(TAG, "Setting up buttons.")
         // Mengakses referensi tombol dari binding Base (binding sama di kedua flavor)

        // Listener tombol Start (panggil ViewModel.update)
         binding.startButton.setOnClickListener {
             mLatLng?.let { latLng -> // Pastikan mLatLng (LatLng spesifik flavor) tidak null
                 // Buat objek Location dari mLatLng (konversi dari LatLng spesifik flavor ke Location standar)
                 val selectedLocation = Location("user_selected").apply {
                     // Untuk flavor Google Maps:
                     latitude = latLng.latitude
                     longitude = latLng.longitude
                 }
                 Relog.i(TAG, "Start button clicked. Preparing to start service...")
                 // === LOGIKA UNTUK MEMULAI SERVICE FAKING LOKASI DI SINI ===
                 // Anda perlu membuat Intent untuk memulai BackgroundTaskService
                 // dengan action START_SERVICE atau action default
                 // Dan meneruskan lokasi terpilih ke Service jika Service yang memulai faking
                 val startServiceIntent = Intent(this, BackgroundTaskService::class.java).apply {
                     // Optional: Set aksi khusus untuk memulai Service
                     // action = NotificationsChannel.ACTION_START_SERVICE // Definisikan aksi START_SERVICE jika ada
                     // Optional: Teruskan lokasi terpilih ke Service
                     putExtra("latitude", selectedLocation.latitude)
                     putExtra("longitude", selectedLocation.longitude)
                 }
                 // Panggil startService (atau startForegroundService jika target SDK >= 26)
                 startService(startServiceIntent)
                 // Update marker dengan title khusus (panggil method helper spesifik flavor di Activity ini)
                 updateMarker(latLng, "Harapan Palsu") // Gunakan mLatLng (LatLng spesifik flavor Google)
                 // Gerakkan kamera (API spesifik flavor)
                 // Untuk flavor Google Maps:
                 googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18.0f))
                 // Trigger reverse geocoding untuk alamat dan notifikasi (SAMA seperti MapLibre)
                 // ViewModel yang memicu reverse geocoding, dan alamat akan ditangkap observer searchResult di Base.
                 viewModel.triggerReverseGeocoding(selectedLocation.latitude, selectedLocation.longitude)
                 // Setelah alamat didapat, BaseMapActivity.observeViewModel akan menampilkan notifikasi via showStartNotification().

             } ?: run { // Jika mLatLng null
                 // showToast(getString(R.string.invalid_location)) // Ditangani oleh showToastEvent dari ViewModel
                  viewModel.triggerShowToastEvent(getString(R.string.invalid_location)) // Emit Toast via event
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
           Relog.i(TAG, "Add Favorite button clicked. Showing dialog.")
           addFavoriteDialog() // <-- Panggil method abstract di MapActivity ini
         }

        // Listener tombol "Dapatkan Lokasi Asli/Mock" (Jika ada, panggil locationHelper)
        binding.getlocation.setOnClickListener {
           Relog.i(TAG, "Get Location button clicked. Requesting updates.")
           locationHelper.requestLocationUpdates(this) // 'this' karena MapActivity mengimplementasikan LocationListener. locationHelper adalah ILocationHelper.
        }

        // Setup search bar listener di BaseMapActivity (panggil ViewModel.searchAddress)
        // Logic listener ini ada di BaseMapActivity.setupNavView()
        // MapActivity ini akan mengamati Flow searchResult dari ViewModel di observeViewModelState().
    }
    /*override fun requestLocationUpdates(listener: LocationListener) {
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
    }
*/
// ... properti dan metode lain yang sudah ada ...

// ... properti dan metode lain yang sudah ada ...
    // Implementasi dari BaseMapActivity: Mendapatkan lokasi terakhir (panggil locationHelper)
    // SAMA PERSIS UNTUK KEDUA FLAVOR. locationHelper adalah ILocationHelper.
    /*@SuppressLint("MissingPermission") // Suppress karena permission dicek oleh Helper ILocationHelper
    override fun getLastLocation() {
        Relog.i(TAG, "getLastLocation() called. Requesting Location updates via helper.")
         // locationHelper.getLastLocation(this) // Opsi: Panggil getLastLocation di helper
         locationHelper.requestLocationUpdates(this) // Panggil method di ILocationHelper (this = MapActivity implementing LocationListener)
    }*/
    // Di dalam kelas MapActivity atau BaseMapActivity

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

    // Implementasi dari BaseMapActivity: Menampilkan notifikasi "Start" (panggil notificationHelper)
    // SAMA PERSIS UNTUK KEDUA FLAVOR. notificationHelper di-inject di Base.
    /*override fun showStartNotification(address: String) {
         Log.d(TAG, "showStartNotification() called with address: $address")
         // Panggil method di NotificationHelper dari Base
        notificationsChannel.showStartNotification(address)
    }*/

    // Implementasi dari BaseMapActivity: Membatalkan notifikasi (panggil notificationHelper)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    /*override fun cancelNotification() {
         Log.d(TAG, "cancelNotification() called.")
        // Panggil method di NotificationHelper dari Base
        notificationsChannel.cancelNotification()
    }*/

    // Implementasi dari BaseMapActivity: Menampilkan dialog tambah favorit (panggil dialogHelper & viewModel)
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

    // Implementasi dari BaseMapActivity: Menampilkan dialog daftar favorit (panggil dialogHelper & viewModel)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    fun showFavoriteListDialog() { // <<< HAPUS 'override'
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

    // Implementasi dari BaseMapActivity: Menampilkan dialog about (panggil dialogHelper)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    fun showAboutDialog() { // <<< HAPUS 'override'
        Timber.tag(TAG).d("showAboutDialog() called.")
        // Ubah nama metode dari showAboutDialog menjadi createAboutDialog
        // dan tambahkan .show() di akhir.
        // Pastikan juga memberikan parameter layoutInflater.
        dialogHelper.createAboutDialog(layoutInflater).show() // <<< UBAH PANGGILAN DAN TAMBAHKAN .show()
    }

    // Implementasi dari BaseMapActivity: Menampilkan dialog update utama (panggil dialogHelper & viewModel)
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    /*override fun showUpdateAvailableDialog(updateInfo: YourUpdateModel) { // <<< HAPUS 'override'
        Timber.tag(TAG).d("showUpdateAvailableDialog() called.")
        // Ubah nama metode dari showUpdateDialog menjadi createUpdateDialog
        // dan tambahkan .show() di akhir.
        // Pastikan juga memberikan parameter layoutInflater.
        dialogHelper.createUpdateDialog( // <<< UBAH NAMA METODE
            layoutInflater = layoutInflater, // <<< TAMBAHKAN PARAMETER INI
            updateInfo = updateInfo.changelog,
            onUpdateClicked = {
                Timber.tag(TAG).d("Update dialog: Update clicked.")
                viewModel.startDownload(updateInfo)
            },
            onCancelClicked = {
                Timber.tag(TAG).d("Update dialog: Cancel clicked.")
                viewModel.postponeUpdate()
            }
        ).show() // <<< Tambahkan .show() di sini
    }*/

    // Implementasi dari BaseMapActivity: Menampilkan/menyembunyikan dialog Xposed missing (panggil dialogHelper)
    // ViewModel meng-emit event showXposedDialogEvent untuk memicu ini.
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    fun showXposedMissingDialog(isShow: Boolean) {
        Timber.tag(TAG).d("showXposedMissingDialog() called with isShow=$isShow")
         // Dialog Xposed Missing di-manage di BaseMapActivity.
         // Method ini dipanggil dari Base untuk memberitahu MapActivity apakah perlu menampilkan dialog.
         // Implementasi di Base sekarang langsung menggunakan dialogHelper dan properti dialog di Base.
         // Logic di sini TIDAK perlu lagi memanggil dialogHelper.showXposedMissingDialog
         // atau mengelola properti dialog xposedDialog.
         // Ini method abstract ini bisa dihapus dari Base atau diubah agar Base yang handle UI dialognya.
         // Karena di Base kita sudah punya properti xposedDialog dan logic tampil/sembunyi di observeViewModel,
         // method abstract ini bisa dihapus dari Base MapActivity.
         // ATAU, jika dialogHelper.showXposedMissingDialog mengembalikan AlertDialog,
         // maka logic manage dialog di BaseMapActivity harus dihapus, dan logic manage dialognya ada di sini.
         // Mari kita asumsikan DialogHelper mengelola life-nya sendiri ATAU mengembalikan AlertDialog yang di-manage di sini.
         // Jika di-manage di Base, method abstract ini tidak perlu.

         // Jika dialog Xposed Missing di-manage di MapActivity (bukan di Base):
         if (isShow) {
             xposedDialog?.dismiss() // Dismiss yang lama jika ada
             xposedDialog = dialogHelper.createXposedMissingDialog() // Tampilkan yang baru
         } else {
             xposedDialog?.dismiss() // Sembunyikan/Dismiss
             xposedDialog = null
         }
         // Karena di BaseMapActivity yang baru kita manage properti xposedDialog, method abstract ini
         // di BaseMapActivity HARUS dihapus, dan observasi di Base langsung panggil dialogHelper
         // dan manage properti xposedDialog di Base.

         // Catatan: Jika method showXposedMissingDialog tetap ada di BaseMapActivity,
         // dan di Base diobservasi isShow, maka implementasi di sini harus memanggil dialogHelper
         // dan manage properti dialog di sini.
         // Mari kita ikuti pola di Base yang baru: Base manage properti dialog & panggil dialogHelper.
         // Jadi, method abstract ini di BaseMapActivity HARUS DIHAPUS.
    }

    // Implementasi dari BaseMapActivity: Menampilkan token  (BARU)
    // Method ini dipanggil dari Base saat viewModel.Token berubah atau saat menu diklik.
    override fun showToken(token: String?) { // <<< TAMBAHKAN KEMBALI 'override'
        Timber.tag(TAG).d("showToken() called with token (first 5 chars): ${token?.take(5)}...")
        // Ubah nama metode dari showTokenDialog menjadi createTokenDialog
        // dan tambahkan .show() di akhir.
        dialogHelper.createTokenDialog(this, token).show()
    }
    // =====================================================================
    // Implementasi Interface LocationListener (Callback dari ILocationHelper)
    // Dipanggil helper saat hasil operasi lokasi (get last, request updates).
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    // =====================================================================

    // Implementasi dari LocationListener: Dipanggil helper saat lokasi berhasil didapatkan
    override fun onLocationResult(location: Location) { // location objek Location standar Android
        Timber.tag(TAG)
            .d("onLocationResult received: Lat=${location.latitude}, Lon=${location.longitude}")
        // Lokasi didapat dari helper, update lat/lon di BaseMapActivity
        this@MapActivity.lat = location.latitude // Update lat di Base (Double)
        this@MapActivity.lon = location.longitude // Update lon di Base (Double)

        // Update mLatLng di MapActivity ini (untuk marker Map spesifik flavor Google)
        // Konversi Location standar ke LatLng spesifik flavor Google
        mLatLng = LatLng(location.latitude, location.longitude)


        // Perbarui map menggunakan method abstract moveMapToNewLocation() di MapActivity ini
        // moveMapToNewLocation butuh Location object sebagai parameter pertama.
        moveMapToNewLocation(location, true) // Panggil method abstract di MapActivity ini

        // Tampilkan toast atau update UI lain - DITANGANI OLEH showToastEvent DARI VIEWMODEL

        // Update UI tombol Start/Stop dan Favorit (SAMA PERSIS seperti MapLibre)
         /*binding.startButton.visibility = View.VISIBLE // Tampilkan tombol Start
         binding.stopButton.visibility = View.GONE // Sembunyikan tombol Stop
         binding.addfavorite.visibility = View.GONE // Sembunyikan tombol Favorit
         binding.getlocation.visibility = View.VISIBLE // Tampilkan tombol Get Location
*/
        // Memicu reverse geocoding untuk mendapatkan alamat dari lokasi yang baru didapat
        // ViewModel yang memicu reverse geocoding, dan alamat akan ditangkap observer searchResult di Base.
        // ViewModel sudah punya akses ke Location dari onLocationResult, jadi bisa langsung panggil di sini
        viewModel.triggerReverseGeocoding(location.latitude, location.longitude)
        // Alamat akan datang via searchResult StateFlow di BaseMapActivity.observeViewModel().
    }

    // Implementasi dari LocationListener: Dipanggil helper saat terjadi error lokasi
    override fun onLocationError(errorMessage: String) { // message String pesan error dari helper
        Timber.tag(TAG).e("onLocationError received: $errorMessage")
        // Tampilkan pesan error ke user - DITANGANI OLEH showToastEvent DARI VIEWMODEL
        // ViewModel bisa emit event toast di LocationHelper callback onLocationError jika LocationHelper diinject ViewModel
        // Atau emit di sini:
        lifecycleScope.launch { viewModel.triggerShowToastEvent("Location Error: $errorMessage") }

        Timber.e("Location Error: $errorMessage") // Log error
        // Panggil method abstract handleLocationError() untuk tampilkan Snackbar atau dialog lain (misal Enable Location)
        handleLocationError() // Panggil method abstract di MapActivity ini
    }

    // Implementasi dari LocationListener: Dipanggil helper saat izin lokasi berhasil diberikan
    // Callback ini DARI LocationHelper.
    fun onPermissionGranted() { // Tidak ada parameter
        Timber.tag(TAG).d("onPermissionGranted received from LocationHelper.")
        // showToast("Izin Lokasi Diberikan") // DITANGANI OLEH showToastEvent DARI VIEWMODEL
        lifecycleScope.launch { viewModel.triggerShowToastEvent("Location Permission Granted.") }

        Timber.d("Location Permission Granted by Helper")
        // Opsi: Coba request lokasi lagi atau trigger start button click
        // locationHelper.requestLocationUpdates(this) // Panggil helper lokasi lagi (this = MapActivity implementing LocationListener)
        // binding.startButton.performClick() // Memicu klik tombol Start (mengakses binding Base)
    }

    // Implementasi dari LocationListener: Dipanggil helper saat izin lokasi ditolak
    // Callback ini DARI LocationHelper.
    fun onPermissionDenied() { // Tidak ada parameter
        Timber.tag(TAG).d("onPermissionDenied received from LocationHelper.")
        // showToast("Izin Lokasi Ditolak. Fitur lokasi tidak akan berfungsi.") // DITANGANI OLEH showToastEvent DARI VIEWMODEL
        lifecycleScope.launch { viewModel.triggerShowToastEvent("Location Permission Denied. Features may be limited.") }

        Timber.w("Location Permission Denied by Helper")
        // Opsi: Tampilkan dialog yang menjelaskan mengapa izin lokasi penting
        handleLocationError() // Bisa pakai method ini untuk tampilkan Snackbar dengan tombol Enable SettingsCompose
    }


    // =====================================================================
    // Implementasi Interface PermissionResultListener (Callback dari PermissionHelper)
    // Dipakai untuk hasil permission SELAIN lokasi, misal Notifikasi.
    // Di-trigger dari BaseMapActivity.onRequestPermissionsResult().
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    // =====================================================================

    // Implementasi dari PermissionResultListener: Dipanggil helper saat hasil permintaan izin tunggal diterima
    /*override fun onPermissionResult(permission: String, isGranted: Boolean) {
        Relog.i(TAG, "onPermissionResult received for $permission: $isGranted")
        when(permission) {
            Manifest.permission.POST_NOTIFICATIONS -> {
                if (isGranted) {
                    // showToast("Izin Notifikasi Diberikan.") // DITANGANI OLEH showToastEvent
                     lifecycleScope.launch { viewModel.triggerShowToastEvent("Notification Permission Granted.") }
                    Timber.d("Notification Permission Granted")
                    // Logic setelah izin notifikasi diberikan (misal, coba tampilkan notifikasi lagi jika gagal sebelumnya)
                } else {
                    // showToast("Izin Notifikasi Ditolak.") // DITANGANI OLEH showToastEvent
                    lifecycleScope.launch { viewModel.triggerShowToastEvent("Notification Permission Denied.") }
                     Timber.w("Notification Permission Denied")
                    // Logic setelah izin notifikasi ditolak (misal, beri tahu user bahwa notifikasi tidak akan tampil)
                }
            }
            // Handle permission tunggal lainnya jika ada
        }
    }*/

    /*// Implementasi dari PermissionResultListener: Dipanggil helper saat hasil permintaan beberapa izin diterima
    override fun onPermissionsResult(permissions: Map<String, Boolean>) {
       Log.d(TAG, "onPermissionsResult received for multiple permissions.")
       // Handle hasil permission multiple non-lokasi jika ada
       // Contoh: cek hasil READ_CONTACTS, WRITE_CALENDAR, dll.
       // for ((permission, isGranted) in permissions) {
       //    // Lakukan sesuatu untuk setiap permission
       // }
    }

*/
    // =====================================================================
    // Implementasi Listener Map Callback (Map-spesifik)
    // GOOGLE MAPS IMPLEMENTATION
    // =====================================================================

    // Implementasi dari OnMapReadyCallback Google Maps: Dipanggil saat map sudah siap
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap // Simpan objek GoogleMap

        Timber.d("GoogleMap is ready")

        // Optional: Aktifkan My Location Layer (titik biru) Google Maps
        // Perlu cek permission sebelum ini! (PermissionHelper)
        // Gunakan PermissionHelper dari Base Activity
        @SuppressLint("MissingPermission") // Suppress karena PermissionHelper yang seharusnya melakukan cek
        if (super.permissionHelper.checkLocationPermissions()) { // Mengakses permissionHelper dari Base
           googleMap.isMyLocationEnabled = true // Aktifkan layer My Location
           googleMap.uiSettings.isMyLocationButtonEnabled = true // Aktifkan tombol My Location
        } else {
             // Jika permission belum ada, request via PermissionHelper
             // locationHelper.requestLocationUpdates(this) // Ini akan memicu cek permission di Helper
             // Atau request via PermissionHelper di sini:
             // super.permissionHelper.requestLocationPermissions(this, PERMISSION_ID) // Panggil PermissionHelper dari Base
        }


        // Pindah kamera ke lokasi awal (misal dari lat/lon di Base)
        // Gunakan lat/lon dari properti Base MapActivity yang diinisialisasi dari PrefManager
        val initialLatLng = LatLng(this@MapActivity.lat, this@MapActivity.lon) // LatLng Google Maps
        val initialCameraUpdate = CameraUpdateFactory.newLatLngZoom(initialLatLng, 15.0f) // API Google
        googleMap.moveCamera(initialCameraUpdate) // Langsung pindah saat awal

        Timber.d("GoogleMap camera moved to initial location: ${initialLatLng.latitude}, ${initialLatLng.longitude}")


        // Setup click listener pada map Google Maps (API Google)
        googleMap.setOnMapClickListener { clickedLatLng -> // Parameter click listener adalah LatLng Google
            // clickedLatLng dari callback ini TIDAK PERNAH null
            mLatLng = clickedLatLng // Set mLatLng di MapActivity ini dengan lokasi klik (LatLng Google). Setelah baris ini, mLatLng TIDAK null.
            Timber.tag(TAG)
                .d("GoogleMap clicked at: ${clickedLatLng.latitude}, ${clickedLatLng.longitude}")

            // Update posisi marker (method helper spesifik flavor di Activity ini)
            updateMarker(
                clickedLatLng,
                title = TODO()
            ) // Gunakan clickedLatLng (LatLng Google Maps)

            // Optional: Gerakkan kamera ke lokasi klik
            // googleMap.animateCamera(CameraUpdateFactory.newLatLng(clickedLatLng))

            // Sinkronisasi ke variabel lat/lon di BaseMapActivity
            this@MapActivity.lat = clickedLatLng.latitude
            this@MapActivity.lon = clickedLatLng.longitude

            // Trigger reverse geocoding untuk mendapatkan alamat dari koordinat klik
            // ViewModel yang memicu reverse geocoding, dan alamat akan ditangkap observer searchResult di Base.
            viewModel.triggerReverseGeocoding(clickedLatLng.latitude, clickedLatLng.longitude)
            // Alamat akan datang via searchResult StateFlow di BaseMapActivity.observeViewModel().

            // Tidak perlu return true/false di GoogleMap setOnMapClickListener
        }

        // Optional: Setup long click listener, marker click listener dll. (API Google)
        // googleMap.setOnMapLongClickListener { ... }
        // googleMap.setOnMarkerClickListener { ... }
    }


    // =====================================================================
    // Metode Helper Spesifik MapActivity (Map-spesifik) - Untuk UI/Map
    // GOOGLE MAPS IMPLEMENTATION
    // =====================================================================

    // Update atau tambah marker di map Google Maps
    // Menerima LatLng spesifik flavor Google.
    fun updateMarker(latLng: LatLng, title: String?) { // Menerima LatLng Google Maps
        if (!::googleMap.isInitialized) return // Pastikan map sudah siap

        if (currentMarker == null) {
            Timber.d("Adding new marker at ${latLng.latitude}, ${latLng.longitude}")
            currentMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
             Timber.d("Updating existing marker to ${latLng.latitude}, ${latLng.longitude}")
            currentMarker?.apply {
                position = latLng
                this.title = title
            }
        }
    }


    // Hapus marker dari map Google Maps
    fun removeMarker() {
         if (!::googleMap.isInitialized) return // Pastikan map sudah siap
        Timber.d("Removing marker")
        currentMarker?.remove()
        currentMarker = null
    }

    // Metode internal Activity untuk dismiss dialog hasil parsial search jika di-manage di sini.
    // SAMA PERSIS UNTUK KEDUA FLAVOR.
    private fun dismissPartialSearchResultsDialog() {
        // Implementasi ini tergantung gimana dialogHelper.showPartialSearchResultsDialog bekerja.
        // Jika dialogHelper mengembalikan AlertDialog dan di-manage di Activity
        // partialSearchResultsDialog?.dismiss()
        // Jika DialogHelper mengelola life-nya sendiri (pola yang disarankan)
        // Tidak perlu melakukan apa-apa di sini.
        // Logika dismiss sudah ada di observeViewModelState di Base saat state berubah dari PartialResult
    }

    // =====================================================================
    // Implementasi Method Abstract Baru untuk Menampilkan Token 
    // =====================================================================
    // Implementasi di sini akan menampilkan token di dialog.

}
