package com.roxgps.ui // Sesuaikan dengan package Activity kamu

// --- IMPORTS YANG DIBUTUHKAN OLEH KODE MAPACTIVITY INI ---
import android.Manifest // Untuk permission
import android.annotation.SuppressLint // Untuk @SuppressLint
import android.content.pm.PackageManager // Untuk cek permission result
// ... import lain dari Android SDK ...
import android.os.Bundle // Untuk Bundle
import android.view.View // Untuk View
import androidx.core.app.ActivityCompat // Masih diperlukan untuk helper/utility di MapLibre Location Component
import androidx.lifecycle.lifecycleScope // Untuk coroutine lifecycle
// --- IMPORTS KHUSUS MAPLIBRE ---
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer // Jika pakai predefined tile server
import org.maplibre.android.annotations.Marker // Marker MapLibre
import org.maplibre.android.annotations.MarkerOptions // MarkerOptions MapLibre
import org.maplibre.android.camera.CameraPosition // CameraPosition MapLibre
import org.maplibre.android.camera.CameraUpdateFactory // CameraUpdateFactory MapLibre
import org.maplibre.android.geometry.LatLng // LatLng MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions // Location Component
import org.maplibre.android.location.modes.CameraMode // Location Component Camera Mode
import org.maplibre.android.location.modes.RenderMode // Location Component Render Mode
import org.maplibre.android.maps.MapLibreMap // MapLibreMap objek
import org.maplibre.android.maps.OnMapReadyCallback // Listener map ready MapLibre
import org.maplibre.android.maps.SupportMapFragment // SupportMapFragment MapLibre
// import org.maplibre.android.style.sources.Source // Contoh import jika set style butuh Source
// import org.maplibre.android.style.layers.Layer // Contoh import jika set style butuh Layer
// -------------------------------
import com.roxgps.R // Import R (resources ID)
// --- IMPORTS UTILITY / EXTENSION YANG DIPAKAI DI MAPACTIVITY ---
import com.roxgps.utils.ext.getAddress // Asumsi ekstensi LatLng ke alamat
import com.roxgps.utils.ext.showToast // Asumsi ekstensi Context
import com.roxgps.utils.NetworkUtils // Utility cek koneksi (jika dipakai di sini selain di Base)
// ------------------------------------------------------------
// --- IMPORTS DARI BASEMAPACTIVITY YANG SUDAH DIREFACTOR ---
import com.roxgps.ui.BaseMapActivity // Import BaseMapActivity yang sudah direfaktor
// Import helper-helper jika kamu perlu mengaksesnya langsung dari MapActivity (jarang, biasanya lewat Base)
// import com.roxgps.helper.LocationHelper // Logic lokasi sudah di Base
// import com.roxgps.helper.LocationListener // Listener diimplementasi di Base
// import com.roxgps.helper.DialogHelper // DialogHelper dipanggil dari Base
// import com.roxgps.helper.NotificationHelper // NotificationHelper dipanggil dari Base
// import com.xgps.helper.SearchHelper // SearchHelper dipanggil dari Base
// import com.roxgps.helper.SearchProgress // Jika digunakan langsung di sini
// ---------------------------------------------------------
import kotlinx.coroutines.launch // Untuk launch coroutine
import androidx.appcompat.app.AppCompatActivity // BaseMapActivity extend ini
// import androidx.lifecycle.ViewModel // Tidak perlu diimpor di sini jika ViewModel diakses lewat properti di BaseMapActivity
import kotlin.properties.Delegates // Delegates.notNull (di Base)


// --- KODE BASEMAPACTIVITY DUMMY DLL DIHAPUS KARENA SUDAH PUNYA FILE SENDIRI ---
// typealias CustomLatLng = LatLng // Ini bisa dihapus jika tidak benar-benar perlu
// class MapActivity: BaseMapActivity(), OnMapReadyCallback, MapLibreMap.OnMapClickListener { ... }
// ------------------------------------------------------------------------


// MapActivity meng-extend BaseMapActivity yang sudah direfaktor
// BaseMapActivity yang direfaktor sudah mengimplementasikan LocationListener
class MapActivity: BaseMapActivity(), OnMapReadyCallback, MapLibreMap.OnMapClickListener { // Tetap implement interface MapLibre

    // Properti MapLibreMap dan Marker spesifik untuk implementasi ini
    private lateinit var mMap: MapLibreMap // Objek MapLibreMap
    // mLatLng bisa null. Menyimpan LatLng lokasi yang sedang aktif/ditampilkan di MapLibre.
    // Ini berbeda dari lat/lon di BaseMapActivity yang merupakan state umum.
    // mLatLng berguna untuk berinteraksi langsung dengan API MapLibre.
    private var mLatLng: LatLng? = null // LatLng MapLibre
    private var mMarker: Marker? = null // Marker MapLibre

    // Properti binding diakses dari BaseMapActivity (protected val binding by lazy { ActivityMapBinding.inflate(layoutInflater) })
    // Kamu tidak perlu mendeklarasikan ulang di sini.


    // --- IMPLEMENTASI FUNGSI ABSTRACT DARI BASEMAPACTIVITY ---

    // Implementasi hasMarker: Cek apakah marker ada
    override fun hasMarker(): Boolean {
        // TODO: if (!mMarker?.isVisible!!){ // TODO dari kode asli
        // Implementasi asli hanya cek non-null
        return mMarker != null // Cukup kembalikan true jika marker objeknya ada
    }

    // Implementasi initializeMap: Setup MapLibre Fragment dan inisialisasi MapLibre SDK
    override fun initializeMap() {
        // Mendapatkan MapLibre Access Token dari metadata Manifest
        // Ini masih boleh di sini karena ini spesifik ke setup MapLibre
        val key = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData.getString("com.maplibre.AccessToken")
        // Menginisialisasi MapLibre SDK
        // Pastikan Context yang dipass benar (this@MapActivity atau applicationContext)
        // WellKnownTileServer.Mapbox ini mungkin perlu diganti URL atau sumber tile lain jika tidak pakai server Mapbox/MapLibre default
        MapLibre.getInstance(applicationContext, key, WellKnownTileServer.Mapbox) // Gunakan applicationContext jika MapLibre butuh context yang hidup lebih lama

        // Mendapatkan SupportMapFragment dari layout atau membuat yang baru
        // val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment? // Jika fragment sudah ada di XML
        val mapFragment = SupportMapFragment.newInstance() // Jika membuat fragment baru di kode

        // Menambahkan fragment ke container di layout (R.id.map harus ada di layout kamu)
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commit()

        // Meminta objek MapLibreMap secara asynchronous
        mapFragment?.getMapAsync(this) // 'this' di sini adalah MapActivity yang implement OnMapReadyCallback
    }

    // Implementasi moveMapToNewLocation: Pindahkan kamera map MapLibre ke koordinat baru
    // lat dan lon diambil dari properti di BaseMapActivity yang sudah diupdate oleh LocationHelper
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        // lat dan lon harus sudah punya nilai Double karena Delegates.notNull di Base class
        if (moveNewLocation && ::mMap.isInitialized) { // Pastikan mMap sudah diinisialisasi
            // Membuat objek LatLng MapLibre dari lat dan lon
            val targetLatLng = LatLng(lat, lon) // Membuat LatLng dari Double lat/lon

            // Optional: Update mLatLng di sini jika mLatLng selalu merepresentasikan lokasi di tengah layar/marker
            mLatLng = targetLatLng // Update mLatLng dengan LatLng MapLibre

            // Menggerakkan kamera map MapLibre
            mMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                        .target(targetLatLng) // Target koordinat (LatLng MapLibre)
                        .zoom(12.0) // Level zoom (MapLibre zoom butuh Double)
                        .bearing(0.0) // Orientasi (bearing butuh Double)
                        .tilt(0.0) // Sudut pandang (tilt butuh Double)
                        .build()
                )
            )

            // Mengupdate posisi marker yang sudah ada atau membuatnya jika belum ada
            // Jika marker belum ada, updateMarker akan menambahkannya
            // updateMarker(targetLatLng) // <-- Memanggil updateMarker dengan LatLng MapLibre

            // TODO: Implementasi dari kode asli
            // mMarker?.apply {
            //     position = latLng // Membutuhkan LatLng
            //     // isVisible = true // Jika perlu set visible
            //     // showInfoWindow() // Jika perlu show info window
            // }
            // Implementasi yang lebih baik: langsung panggil updateMarker
             updateMarker(targetLatLng)


            // Fetch alamat dari lokasi baru menggunakan Coroutine
            lifecycleScope.launch {
                 // Fungsi getAddress() ini adalah extension function (asumsi ada di com.roxgps.utils.ext)
                val address = fetchAddress(targetLatLng) // Mengambil alamat dari LatLng MapLibre
                showToast("Alamat: $address") // Menampilkan Toast
            }
        }
    }

    // Implementasi setupButtons: Menetapkan click listeners untuk tombol-tombol
    // Panggilan fungsi di sini DIUBAH agar sesuai dengan BaseMapActivity yang sudah direfaktor (menggunakan helper atau logic baru di Base)
    @SuppressLint("MissingPermission") // Suppress warning jika ada akses permission langsung (seharusnya tidak ada lagi)
    override fun setupButtons(){
        // Listener untuk tombol Tambah Favorit
        binding.addfavorite.setOnClickListener {
            // Memanggil fungsi addFavoriteAction() di BaseMapActivity yang sudah direfaktor
            // Logic ini nanti akan memanggil dialogHelper.showAddFavoriteDialog
            addFavoriteAction() // Fungsi ini sekarang ada di BaseMapActivity yang direfaktor
        }

        // Listener untuk tombol Dapatkan Lokasi
        binding.getlocation.setOnClickListener {
            // Memanggil fungsi di BaseMapActivity yang trigger request lokasi via LocationHelper
            // Fungsi ini akan memanggil locationHelper.requestLastKnownLocation()
            requestLocation() // Asumsikan ada fungsi requestLocation() di BaseMapActivity yang direfaktor

            // Update map (animasi kamera) berdasarkan lokasi BARU yang didapat dari LocationHelper
            // seharusnya dilakukan di onLocationResult di BaseMapActivity, BUKAN di sini setelah panggil request.
            // Jadi, kode animasi kamera di sini dihapus.
            // mLatLng?.let { ... animateCamera ... } // DIHAPUS dari versi Google Maps, pastikan tidak ada di sini.
        }

        // Cek status awal ViewModel untuk mengatur visibilitas tombol saat Activity pertama kali dibuat
        // Ini perlu dipanggil di setupButtons atau di onCreate/onResume
        if (viewModel.isStarted) { // diasumsikan isStarted ada di ViewModel
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
        } else {
            binding.startButton.visibility = View.VISIBLE
            binding.stopButton.visibility = View.GONE
        }


        // Listener untuk tombol Start Proses (misal: start simulasi lokasi)
        binding.startButton.setOnClickListener {
            if (mLatLng != null) { // Pastikan mLatLng (lokasi terakhir yang diketahui/diset) tidak null
                // Perbarui status lokasi palsu di ViewModel (ViewModel diakses dari BaseMapActivity)
                // Menggunakan mLatLng.latitude (Double) dan mLatLng.longitude (Double)
                viewModel.update(true, mLatLng!!.latitude, mLatLng!!.longitude) // !! aman di sini karena sudah cek != null

                // Perbarui marker di map MapLibre
                updateMarker(mLatLng!!) // Menggunakan mLatLng (LatLng MapLibre)

                // Pindahkan kamera ke lokasi marker palsu
                 // Animasikan kamera ke LatLng mLatLng dengan zoom 18.0
                 mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLatLng!!, 18.0)) // Menggunakan mLatLng (LatLng) dan zoom Double

                // Atur visibilitas tombol UI spesifik MapActivity ini
                binding.startButton.visibility = View.GONE
                binding.stopButton.visibility = View.VISIBLE

                // Fetch alamat dan tampilkan notifikasi awal menggunakan Coroutine
                lifecycleScope.launch {
                    try {
                         // Ambil alamat berdasarkan lokasi marker palsu
                         // mLatLng?.getAddress(...) sudah mengembalikan Flow<String> (asumsi)
                         // Fungsi fetchAddress mengambil LatLng MapLibre dan mengembalikan String alamat
                        val address = fetchAddress(mLatLng!!) // Mengambil alamat dari mLatLng (LatLng MapLibre)
                        // Panggil fungsi di BaseMapActivity yang menggunakan NotificationHelper
                        showStartNotification(address) // Fungsi ini sekarang ada di BaseMapActivity yang direfaktor
                        showToast(getString(R.string.location_set)) // Tampilkan toast sukses
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(getString(R.string.location_error)) // Tampilkan toast error
                    }
                }
            } else {
                // Tampilkan pesan jika lokasi marker tidak tersedia (mLatLng masih null)
                showToast(getString(R.string.invalid_location))
            }
        }

        // Listener untuk tombol Stop Proses
        binding.stopButton.setOnClickListener {
            // --- PANGGIL FUNGSI stopProcess() DARI BASEMAPACTIVITY ---
            // Seluruh logika stop dipindahkan ke fungsi stopProcess()
            stopProcess() // <-- Panggil fungsi abstract stopProcess() yang diimplementasi di MapActivity ini
            // ----------------------------------------------------

            // Kode lama di sini dihapus karena sudah ada di stopProcess():
            // if (mLatLng != null) { viewModel.update(false, mLatLng!!.latitude, mLatLng!!.longitude) }
            // removeMarker()
            // binding.stopButton.visibility = View.GONE
            // binding.startButton.visibility = View.VISIBLE
            // cancelNotification()
            // showToast(getString(R.string.location_unset))
            // else { showToast(getString(R.string.invalid_location)) }
        }
    }

    // --- FUNGSI ABSTRACT stopProcess() DARI BASEMAPACTIVITY (IMPLEMENTASI DI SINI) ---
    // Logika inti untuk menghentikan proses lokasi palsu
    override fun stopProcess() {
        // Cek mLatLng, karena logic update ViewModel dan remove marker butuh info lokasi terakhir
        mLatLng?.let { currentLatLng -> // currentLatLng tipenya LatLng MapLibre
            // 1. Update status di ViewModel
            // ViewModel diakses dari BaseMapActivity. update() adalah fungsi ViewModel.
            viewModel.update(false, currentLatLng.latitude, currentLatLng.longitude) // Menggunakan currentLatLng.latitude (Double) dan .longitude (Double)
        }
        // Jika mLatLng null saat stop diklik, update ViewModel mungkin tidak menyertakan lokasi.

        // 2. Hapus marker dari map MapLibre (fungsi di MapActivity ini)
        removeMarker()

        // 3. Mengubah tampilan tombol UI (akses binding di MapActivity ini)
        binding.startButton.visibility = View.VISIBLE
        binding.stopButton.visibility = View.GONE

        // 4. Menghentikan notifikasi (fungsi ini ada di BaseMapActivity yang panggil NotificationHelper)
        cancelNotification() // cancelNotification() ada di BaseMapActivity yang direfaktor

        // 5. Menampilkan Toast (fungsi ini ada di BaseMapActivity)
        showToast(getString(R.string.location_unset)) // showToast() ada di BaseMapActivity yang direfaktor
    }
    // ------------------------------------------------------------------------------


    // --- FUNGSI MAPLIBRE SPECIFIC YANG TERSISA DI MAPACTIVITY INI ---

    // Dipanggil saat MapLibreMap siap digunakan (implementasi OnMapReadyCallback)
    @SuppressLint("MissingPermission") // Suppress warning karena permission dicek dan diminta oleh LocationHelper (via BaseActivity)
    override fun onMapReady(mapLibreMap: MapLibreMap) {
        mMap = mapLibreMap // Simpan referensi MapLibreMap

        // --- LOGIC PERMISSION LOKASI DI SINI DIHAPUS KARENA SUDAH DITANGANI LOCATIONHELPER ---
        // if (ActivityCompat.checkSelfPermission(...) { ... } else { ... } // DIHAPUS
        // -------------------------------------------------------------------------------

        // Setelah map siap, kita bisa aktifkan layer "My Location" JIKA izin sudah diberikan
        // Status izin bisa didapat dari LocationHelper.checkLocationPermissions()
        // atau setelah onPermissionGranted() di BaseMapActivity dipanggil.
        // Aktivas MapLibre Location Component perlu MapLibreMap dan Style yang siap.
        // MapLibre Location Component Initialization (harus dipanggil SETELAH Style dimuat ASYNC)
        // Prosesnya agak beda dari Google Maps. Butuh Style objek.
        // Yang paling rapi, aktivasi Location Component dilakukan DI DALAM callback setStyle.
        // Dan isLocationComponentEnabled di set TRUE HANYA JIKA izin lokasi ada (cek via LocationHelper).

        // Konfigurasi style map MapLibre
        var typeUrl = "https://demotiles.maplibre.org/style.json" // Style default
        // Periksa nilai viewModel.mapType (ViewModel diakses dari BaseMapActivity)
        // Perbaiki perbandingan dengan Integer. Gunakan '===' atau '.equals()' jika mapType String,
        // atau lebih baik pakai Enum/Sealed Class. Asumsi mapType adalah Int.
        if (viewModel.mapType == 2) { // Satellite
            typeUrl = "mapbox://styles/mapbox/satellite-streets-v12" // Perlu aksses Mapbox styles? Atau MapLibre?
        } else if (viewModel.mapType == 3) { // Terrain
            typeUrl = "mapbox://styles/mapbox/outdoors-v12" // Perlu aksses Mapbox styles? Atau MapLibre?
        } else if (viewModel.mapType == 4) { // Hybrid
            typeUrl = "mapbox://styles/mapbox/navigation-day-v1" // Perlu aksses Mapbox styles? Atau MapLibre?
        } else {
             // Default atau case 0/1
             typeUrl = "mapbox://styles/mapbox/streets-v12" // Perlu aksses Mapbox styles? Atau MapLibre?
        }
         // TODO: Pastikan URL style ini valid untuk MapLibre, terutama Mapbox styles mungkin butuh Mapbox SDK.
         // Mungkin perlu diganti dengan style dari MapTiler atau sumber tile server lain yang compatible dengan MapLibre.

        mMap.setStyle(typeUrl) { style ->
            // Callback ini dipanggil setelah style selesai dimuat
            // Di sini aman untuk mengaktifkan Location Component MapLibre

            // Mengaktifkan Location Component HANYA JIKA izin lokasi sudah diberikan
            // Panggil check permission dari LocationHelper via Base Activity
            if (locationHelper.checkLocationPermissions()) {
                 val locationComponent = mMap.locationComponent
                 // Membuat Activation Options (perlu Context, Style)
                 val activationOptions = LocationComponentActivationOptions.builder(this@MapActivity, style)
                     .useDefaultLocationEngine(true) // Menggunakan LocationEngine bawaan MapLibre
                     // Atur konfigurasi lain jika perlu (render mode, camera mode awal, dll)
                     .build()

                 // Mengaktifkan Location Component dengan opsi
                 locationComponent.activateLocationComponent(activationOptions)
                 // Mengatur status enabled Location Component
                 locationComponent.isLocationComponentEnabled = true // Aktifkan layer "My Location" MapLibre

                 // Mengatur mode kamera dan render Location Component
                 // Ini bisa dipanggil kapan saja setelah Location Component diaktifkan
                 locationComponent.cameraMode = CameraMode.TRACKING // Kamera mengikuti lokasi
                 locationComponent.renderMode = RenderMode.COMPASS // Icon lokasi menghadap arah kompas

                 // Jika perlu mendapatkan lokasi PERTAMA setelah map siap dan izin ada,
                 // panggil requestLocation() dari BaseMapActivity di sini.
                 // requestLocation() // Optional: request lokasi pertama
            } else {
                 // Jika izin belum ada, Location Component tidak diaktifkan dulu.
                 // Akan diaktifkan nanti di onPermissionGranted() di BaseActivity jika izin diberikan dan style sudah siap.
                 // Perlu listener untuk Style load completion jika requestLocation dipicu onPermissionGranted.
            }
        }


        // Konfigurasi setting UI MapLibreMap
        with(mMap){
            // TODO: fix bug with drawer (TODO dari kode asli) - Mungkin ini terkait padding atau insets
            // Set padding di BaseMapActivity.setupNavView() sudah mencakup padding bawah (bottom)
            // Mungkin perlu set padding kanan/kiri atau atas juga di sini jika UI MapLibre berbeda

            uiSettings.setAllGesturesEnabled(true) // Mengaktifkan semua gesture map (zoom, scroll, rotate, tilt)
            uiSettings.setCompassEnabled(true) // Menampilkan kompas
            uiSettings.setCompassMargins(0,480,120,0) // Mengatur margin kompas (left, top, right, bottom)
            uiSettings.setLogoEnabled(true) // Menampilkan logo MapLibre
            uiSettings.setLogoMargins(0,0,0,80) // Mengatur margin logo
            uiSettings.setAttributionEnabled(false) // Menyembunyikan atribusi (pastikan ini diizinkan oleh lisensi tile/style)
            // uiSettings.setAttributionMargins(80,0,0,80) // TODO dari kode asli
            // setPadding(0,0,0,80) // Padding map (sudah di set di BaseMapActivity.setupNavView)

             // --- Tentukan posisi awal map ---
            // Ambil lat/lon awal dari ViewModel (properti di BaseMapActivity)
            // lat dan lon sudah Double karena Delegates.notNull
            val initialLatLng = LatLng(lat, lon) // Membuat LatLng MapLibre dari lat/lon

            // Set mLatLng awal (opsional, jika mLatLng perlu merepresentasikan posisi awal)
            mLatLng = initialLatLng // Update mLatLng dengan LatLng MapLibre

            // Tambahkan marker awal (invisible, tidak bisa digeser)
            // Panggil updateMarker untuk membuat atau mengupdate marker awal
            updateMarker(initialLatLng) // Menggunakan initialLatLng (LatLng MapLibre)

            // TODO: MarkerOptions().position(it!!) // TODO dari kode asli - ini logic pembuatan marker, sudah ada di updateMarker
            // .draggable(false).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED).visible(false) // Opsi marker, bisa ditambahkan ke updateMarker jika perlu

             // Gerakkan kamera map ke lokasi awal
            animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15.0)) // Menggunakan initialLatLng (LatLng) dan zoom Double. Asumsi zoom 15.0 cocok.

            // Fetch alamat dari lokasi awal menggunakan Coroutine
            lifecycleScope.launch {
                val address = fetchAddress(initialLatLng) // Mengambil alamat dari initialLatLng (LatLng MapLibre)
                showToast("Alamat awal: $address") // Menampilkan Toast
            }
            // -------------------------------

            // Menetapkan listener saat map diklik
            addOnMapClickListener(this@MapActivity) // 'this@MapActivity' karena listener di kelas ini
            // Bisa tambahkan listener lain jika perlu, misal:
            // addOnMarkerClickListener(...)
            // addOnCameraIdleListener(...)
        }
         // TODO: if (viewModel.isStarted){ ... } - TODO dari kode asli. Ini logic terkait status ViewModel
         // Jika isStarted, marker mungkin perlu diset visible dan show info window.
         // Logic ini bisa ditambahkan setelah marker dibuat/updated, atau sebagai response terhadap perubahan ViewModel.
         // Implementasi di MapActivity.stopProcess() sudah menghapus marker saat stop, jadi mungkin TODO ini terkait status start?
         // Jika ViewModel.isStarted true saat MapActivity dibuat/ready, marker perlu ditampilkan.
         // Ini bisa dicek di onMapReady setelah marker dibuat, atau di onResume jika status bisa berubah.
         if (viewModel.isStarted && ::mMarker.isInitialized && mMarker != null) {
              // TODO: mMarker?.isVisible = true
              // TODO: mMarker?.showInfoWindow()
              // Ini bisa ditambahkan di sini atau di response ViewModel.isStarted jika diobservasi.
         }
    }


    // Dipanggil saat user klik di map (implementasi OnMapClickListener MapLibre)
    // Mengembalikan Boolean: true jika event dikonsumsi, false jika tidak
    override fun onMapClick(latLng: LatLng): Boolean { // Menerima LatLng MapLibre
        mLatLng = latLng // Update mLatLng dengan lokasi klik (LatLng MapLibre)

        // Karena mLatLng baru saja di-set, dia tidak null.
        // Menggunakan 'let' untuk kejelasan, atau bisa langsung pakai 'latLng' parameter.
        mLatLng?.let { clickedLatLng -> // clickedLatLng tipenya LatLng MapLibre
            // Mengupdate posisi marker yang sudah ada atau membuatnya
            updateMarker(clickedLatLng) // Menggunakan clickedLatLng (LatLng MapLibre)

            // Menggerakkan kamera map ke lokasi klik
            mMap.animateCamera(CameraUpdateFactory.newLatLng(clickedLatLng)) // Menggunakan clickedLatLng (LatLng MapLibre)

            // Sinkronisasi lat/lon di BaseMapActivity dengan lokasi klik
            lat = clickedLatLng.latitude // lat (Double) diupdate
            lon = clickedLatLng.longitude // lon (Double) diupdate

            // Fetch alamat dari lokasi klik menggunakan Coroutine
            lifecycleScope.launch {
                val address = fetchAddress(clickedLatLng) // Mengambil alamat dari clickedLatLng (LatLng MapLibre)
                showToast("Alamat: $address") // Menampilkan Toast
            }
             // TODO: mMarker?.let { marker -> mLatLng.let { ... } } // TODO dari kode asli, ini redundan setelah logic dipindah ke atas
        }
        return true // Mengonsumsi event klik
    }


    // Fetch alamat secara asynchronous
    // Fungsi ini mengambil LatLng (MapLibre), memanggil extension getAddress(), dan mengembalikan String alamat.
    // Fungsi getAddress() diasumsikan ada di file utility (misal di com.roxgps.utils.ext) dan mengembalikan Flow<String> atau sejenisnya.
    private suspend fun fetchAddress(latLng: LatLng): String { // Menerima LatLng MapLibre
        var result = "Alamat tidak ditemukan"
        try {
            // Memanggil extension function getAddress() pada LatLng.
            // getAddress() diasumsikan adalah suspend function yang menghasilkan Flow<String>
             // Perlu memastikan ekstensi getAddress ini bisa menerima LatLng MapLibre atau ada overload/converter
            latLng.getAddress(this@MapActivity)?.collect { address -> // Menggunakan latLng (LatLng MapLibre) dan Context
                 result = address // Update hasil saat alamat didapat dari Flow
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result = "Gagal mendapatkan alamat: ${e.message}"
        }
        return result // Mengembalikan hasil alamat
    }

    // --- Implementasi abstract method getActivityInstance() ---
    // Fungsi ini kemungkinan tidak diperlukan lagi jika Context atau referensi Activity
    // di helper-helper sudah diatur dengan benar menggunakan 'this' dari Activity.
    // Direkomendasikan untuk menghapus abstract fun ini dari BaseMapActivity
    // dan implementasinya di sini.
    override fun getActivityInstance(): BaseMapActivity { // TODO dari kode asli: return this@MapActivity
         // Asumsi fungsi abstract ini akan dihapus dari BaseMapActivity
        // Jika tetap ada, implementasi ini sudah benar (mengembalikan instance Activity)
         return this@MapActivity
    }
    // ---------------------------------------------------------


    // --- FUNGSI MAPLIBRE SPECIFIC LAINNYA ---

    // Update atau tambah marker di map MapLibre
    // Membutuhkan LatLng MapLibre. Pastikan argumen yang diberikan tipenya LatLng MapLibre.
    private fun updateMarker(latLng: LatLng, title: String = "Lokasi") { // Menerima LatLng MapLibre
        if (mMarker == null) {
            // Jika marker belum ada, tambahkan marker baru MapLibre
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng) // Posisi marker (membutuhkan LatLng MapLibre)
                    .title(title) // Judul marker
                    // MapLibre tidak punya BitmapDescriptorFactory.defaultMarker()
                    // Perlu cara lain untuk membuat icon marker, misal dari Drawable resource.
                    // .icon(...) // Implementasi icon marker MapLibre
                    .draggable(true) // Jadikan marker bisa digeser (opsional)
                    // MapLibre MarkerOptions tidak punya .visible(), gunakan marker.isVisible = true setelah add
            )
             // MapLibre Marker tidak punya showInfoWindow() di MarkerOptions, panggil setelah marker dibuat
            mMarker?.apply {
                 // TODO: .isVisible = true // Implementasi dari kode asli
                 isVisible = true // Set marker visible
                 // TODO: .showInfoWindow() // Implementasi dari kode asli
                 // MapLibre Marker Info Window API beda dari Google Maps.
                 // Mungkin perlu implementasi custom InfoWindowAdapter.
                 // Untuk sekarang, abaikan showInfoWindow() jika tidak ada implementasi custom.
            }
            // Optional: Tambahkan listener geser marker jika draggable = true
            // mMap.addOnMarkerDragListener(...)
        } else {
            // Jika marker sudah ada, update posisinya
            mMarker?.apply {
                position = latLng // Posisi marker (membutuhkan LatLng MapLibre)
                isVisible = true // Pastikan terlihat
                this.title = title // Update judul
                 // TODO: showInfoWindow() // Sama seperti di atas, butuh implementasi custom
            }
        }
    }

    // Hapus marker dari map MapLibre
    private fun removeMarker() {
        mMarker?.remove() // Hapus marker MapLibre
        mMarker = null // Set properti marker jadi null
    }

    // ... Tambahkan override fungsi lifecycle lain jika perlu (onPause, onStop, onSaveInstanceState, onActivityResult)
    // Pastikan onSaveInstanceState, onPause, onStop dipanggil untuk mMap di sini jika MapLibre membutuhkannya.
    // override fun onSaveInstanceState(outState: Bundle) { mMap.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
    // override fun onResume() { super.onResume(); mMap.onResume() }
    // override fun onPause() { super.onPause(); mMap.onPause() }
    // override fun onStop() { super.onStop(); mMap.onStop() }
    // override fun onLowMemory() { super.onLowMemory(); mMap.onLowMemory() }
    // override fun onDestroy() { super.onDestroy(); mMap.onDestroy() } // Pastikan dipanggil setelah super.onDestroy() di BaseMapActivity

    // ... Tambahkan override onRequestPermissionsResult jika masih dipakai untuk permission LAIN selain lokasi
    // override fun onRequestPermissionsResult(...) { ... }

}
