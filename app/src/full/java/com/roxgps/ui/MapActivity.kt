package com.roxgps.ui // Sesuaikan dengan package Activity kamu

// --- IMPORTS YANG DIBUTUHKAN OLEH KODE MAPACTIVITY INI ---
import android.Manifest // Untuk permission
import android.annotation.SuppressLint // Untuk @SuppressLint
import android.content.pm.PackageManager // Untuk cek permission result
// ... import lain dari Android SDK ...
import android.os.Bundle // Untuk Bundle
import android.view.View // Untuk View
import androidx.core.app.ActivityCompat // Masih perlu jika ada logic ActivityCompat tersisa di sini (seharusnya minimal)
import androidx.lifecycle.lifecycleScope // Untuk coroutine lifecycle
// --- IMPORTS KHUSUS GOOGLE MAPS ---
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.* // LatLng, Marker, MarkerOptions, BitmapDescriptorFactory, CameraPosition
// -----------------------------------
import com.roxgps.R // Import R (resources ID)
// --- IMPORTS UTILITY / EXTENSION YANG DIPAKAI DI MAPACTIVITY ---
import com.roxgps.utils.ext.getAddress // Asumsi ekstensi LatLng ke alamat
import com.roxgps.utils.ext.showToast // Asumsi ekstensi Context
// import com.roxgps.utils.NetworkUtils // Utility cek koneksi (jika dipakai di sini, tapi sekarang dipakai di Base)
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
// import androidx.appcompat.app.AppCompatActivity // BaseMapActivity extend ini
// import androidx.lifecycle.ViewModel // Tidak perlu diimpor di sini jika ViewModel diakses lewat properti di BaseMapActivity
// import kotlin.properties.Delegates // Delegates.notNull (di Base)


// MapActivity meng-extend BaseMapActivity yang sudah direfaktor
// BaseMapActivity yang direfaktor sudah mengimplementasikan LocationListener
class MapActivity : BaseMapActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener { // Tetap implement interface Google Maps

    // Properti GoogleMap dan Marker spesifik untuk implementasi ini
    private lateinit var mMap: GoogleMap // Objek GoogleMap, dijamin tidak null setelah onMapReady
    // mLatLng bisa null. Menyimpan LatLng lokasi yang sedang aktif/ditampilkan di Google Maps.
    // Ini berbeda dari lat/lon di BaseMapActivity yang merupakan state umum.
    // mLatLng berguna untuk berinteraksi langsung dengan API Google Maps.
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null // Marker di map Google Maps

    // Properti binding diakses dari BaseMapActivity (protected val binding by lazy { ActivityMapBinding.inflate(layoutInflater) })
    // ViewModel diakses dari BaseMapActivity (protected val viewModel by viewModels<MainViewModel>())


    // --- IMPLEMENTASI FUNGSI ABSTRACT DARI BASEMAPACTIVITY ---

    // Implementasi hasMarker: Cek apakah marker ada dan terlihat di Google Maps
    override fun hasMarker(): Boolean {
        return mMarker?.isVisible == true
    }

    // Implementasi initializeMap: Setup Google Map Fragment
    override fun initializeMap() {
        // Menggunakan SupportMapFragment untuk Google Maps
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment) // R.id.map harus ada di layout kamu
            .commit()
        // Meminta objek GoogleMap secara asynchronous
        mapFragment.getMapAsync(this) // 'this' di sini adalah MapActivity yang implement OnMapReadyCallback
    }

    // Implementasi moveMapToNewLocation: Pindahkan kamera map Google Maps ke koordinat baru
    // lat dan lon diambil dari properti di BaseMapActivity yang sudah diupdate oleh LocationHelper
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        // lat dan lon harus sudah punya nilai Double karena Delegates.notNull di Base class
        if (moveNewLocation && ::mMap.isInitialized) { // Pastikan mMap sudah diinisialisasi
            // Membuat objek LatLng Google Maps dari lat dan lon
            val targetLatLng = LatLng(lat, lon) // Membuat LatLng dari Double lat/lon

            // Optional: Update mLatLng di sini jika mLatLng selalu merepresentasikan lokasi di tengah layar/marker
            mLatLng = targetLatLng // Update mLatLng dengan LatLng Google Maps

            // Menggerakkan kamera map Google Maps
            mMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(targetLatLng) // Target koordinat (LatLng Google Maps)
                        .zoom(18.0f) // Level zoom (Float)
                        .bearing(0f) // Orientasi (Float)
                        .tilt(0f) // Sudut pandang (Float)
                        .build()
                )
            )

            // Mengupdate posisi marker yang sudah ada atau membuatnya jika belum ada
            // Jika marker belum ada, updateMarker akan menambahkannya
             updateMarker(targetLatLng) // Membutuhkan LatLng Google Maps

            // Fetch alamat dari lokasi baru menggunakan Coroutine
            lifecycleScope.launch {
                 // Fungsi getAddress() ini adalah extension function (asumsi ada di com.roxgps.utils.ext)
                val address = fetchAddress(targetLatLng) // Mengambil alamat dari LatLng Google Maps
                showToast("Alamat: $address") // Menampilkan Toast (fungsi di BaseMapActivity)
            }
        }
    }

    // Implementasi setupButtons: Menetapkan click listeners untuk tombol-tombol
    // Panggilan fungsi di sini DIUBAH agar sesuai dengan BaseMapActivity yang sudah direfaktor (menggunakan helper atau logic baru di Base)
    @SuppressLint("MissingPermission") // Suppress warning jika ada akses permission langsung di sini (seharusnya tidak)
    override fun setupButtons() {
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
            requestLocation() // Fungsi ini sekarang ada di BaseMapActivity yang direfaktor

            // Update map (animasi kamera) berdasarkan lokasi BARU yang didapat dari LocationHelper
            // seharusnya dilakukan di onLocationResult di BaseMapActivity, BUKAN di sini setelah panggil request.
            // Jadi, kode animasi kamera di sini dihapus.
            // mLatLng?.let { ... animateCamera ... } // DIHAPUS
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
                // --- TAMBAHKAN CEK INI ---
                // Pastikan mMap sudah diinisialisasi sebelum digunakan (untuk memanggil mMap.animateCamera)
                if (::mMap.isInitialized) {
                // ---------------------
                    // Perbarui status lokasi palsu di ViewModel (ViewModel diakses dari BaseMapActivity)
                    // Menggunakan mLatLng.latitude (Double) dan mLatLng.longitude (Double)
                    viewModel.update(true, mLatLng!!.latitude, mLatLng!!.longitude) // !! aman di sini karena sudah cek != null

                    // Perbarui marker di map Google Maps
                    updateMarker(mLatLng!!) // Menggunakan mLatLng (LatLng Google Maps)

                    // Pindahkan kamera ke lokasi marker palsu
                     mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLatLng!!, 18.0f)) // Menggunakan mLatLng (LatLng) dan zoom Float

                    // Atur visibilitas tombol UI spesifik MapActivity ini
                    binding.startButton.visibility = View.GONE
                    binding.stopButton.visibility = View.VISIBLE

                    // Fetch alamat dan tampilkan notifikasi awal menggunakan Coroutine
                    lifecycleScope.launch {
                        try {
                            // Ambil alamat berdasarkan lokasi marker palsu
                             // mLatLng?.getAddress(...) sudah mengembalikan Flow<String> (asumsi)
                            val address = fetchAddress(mLatLng!!) // Mengambil alamat dari mLatLng (LatLng Google Maps)
                            // Panggil fungsi di BaseMapActivity yang menggunakan NotificationHelper
                            showStartNotification(address) // Fungsi ini sekarang ada di BaseMapActivity yang direfaktor
                            showToast(getString(R.string.location_set)) // Tampilkan toast sukses (fungsi di Base)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            showToast(getString(R.string.location_error)) // Tampilkan toast error (fungsi di Base)
                        }
                    }
                } else {
                     // Tampilkan pesan jika map belum siap
                     showToast("Peta belum siap. Mohon tunggu sebentar.") // Fungsi di BaseMapActivity
                }
            } else {
                showToast(getString(R.string.invalid_location)) // Tampilkan pesan jika lokasi tidak valid (fungsi di Base)
            }
        }

        // Listener untuk tombol Stop Proses
        binding.stopButton.setOnClickListener {
            // --- PANGGIL FUNGSI stopProcess() DARI BASEMAPACTIVITY ---
            // Seluruh logika stop dipindahkan ke fungsi stopProcess()
            stopProcess() // <-- Panggil fungsi abstract stopProcess() yang diimplementasi di MapActivity ini
            // ----------------------------------------------------
        }
    }

    // --- IMPLEMENTASI FUNGSI ABSTRACT stopProcess() DARI BASEMAPACTIVITY ---
    // Logika inti untuk menghentikan proses lokasi palsu
    override fun stopProcess() {
        // Cek mLatLng, karena logic update ViewModel dan remove marker butuh info lokasi terakhir
        mLatLng?.let { currentLatLng -> // currentLatLng tipenya LatLng Google Maps
            // 1. Update status di ViewModel
            // ViewModel diakses dari BaseMapActivity. update() adalah fungsi ViewModel.
            viewModel.update(false, currentLatLng.latitude, currentLatLng.longitude) // Menggunakan currentLatLng.latitude (Double) dan .longitude (Double)
        }
        // Jika mLatLng null saat stop diklik, update ViewModel mungkin tidak menyertakan lokasi.

        // 2. Hapus marker dari map Google Maps (fungsi di MapActivity ini)
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


    // --- FUNGSI GOOGLE MAPS SPECIFIC YANG TERSISA DI MAPACTIVITY INI ---

    // Dipanggil saat GoogleMap siap digunakan (implementasi OnMapReadyCallback)
    @SuppressLint("MissingPermission") // Suppress warning karena permission dicek dan diminta oleh LocationHelper (via BaseActivity)
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap // Simpan referensi GoogleMap

        // --- LOGIC PERMISSION LOKASI DI SINI DIHAPUS KARENA SUDAH DITANGANI LOCATIONHELPER VIA BASE ---
        // if (ActivityCompat.checkSelfPermission(...) { ... } else { ... } // DIHAPUS
        // ------------------------------------------------------------------------------------------

        // Setelah map siap, kita bisa aktifkan layer "My Location" JIKA izin sudah diberikan
        // Panggil fungsi cek permission dari Base Activity
        if (checkLocationPermissions()) { // Fungsi checkLocationPermissions() ada di BaseMapActivity yang direfaktor
             mMap.isMyLocationEnabled = true // Aktifkan layer "My Location" Google Maps
             // Optional: Kalau mau langsung tampilkan lokasi user di map saat onMapReady DAN izin ada
             // requestLocation() // Panggil request lokasi dari Base Activity
        } else {
             // Jika izin belum ada, layer "My Location" tidak diaktifkan dulu.
             // Akan diaktifkan nanti di onPermissionGranted() di BaseActivity jika izin diberikan.
        }


        // Konfigurasi setting UI GoogleMap
        mMap.apply {
            setTrafficEnabled(true) // Aktifkan traffic
            uiSettings.apply {
                isMyLocationButtonEnabled = false // Sembunyikan tombol "My Location" bawaan
                isZoomControlsEnabled = false // Sembunyikan tombol zoom bawaan
                isCompassEnabled = false // Sembunyikan kompas bawaan
            }
            // Padding map agar tidak terhalang UI (misal AppBar atau tombol)
            // Padding bawah sudah di set di BaseMapActivity.setupNavView()
            // Set padding atas, kiri, kanan di sini jika diperlukan dan tidak di handle di Base
            // setPadding(0, 80, 0, 0) // Contoh padding atas 80dp (sesuaikan)

            // Set tipe map dari ViewModel (ViewModel diakses dari BaseMapActivity)
            mapType = viewModel.mapType // mapType diasumsikan ada di ViewModel

            // --- Tentukan posisi awal map ---
            // Ambil lat/lon awal dari ViewModel (properti di BaseMapActivity)
            // lat dan lon sudah Double karena Delegates.notNull
            val initialLatLng = LatLng(lat, lon) // Membuat LatLng Google Maps dari lat/lon

            // Set mLatLng awal (opsional, jika mLatLng perlu merepresentasikan posisi awal)
            mLatLng = initialLatLng // Update mLatLng dengan LatLng Google Maps

            // Tambahkan marker awal (invisible, tidak bisa digeser)
            // Panggil updateMarker untuk membuat atau mengupdate marker awal
            updateMarker(initialLatLng) // Menggunakan initialLatLng (LatLng Google Maps)

             // Gerakkan kamera map ke lokasi awal
            animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15.0f)) // Menggunakan initialLatLng (LatLng) dan zoom Float. Asumsi zoom 15.0 cocok.

            // Fetch alamat dari lokasi awal menggunakan Coroutine
            lifecycleScope.launch {
                val address = fetchAddress(initialLatLng) // Mengambil alamat dari initialLatLng (LatLng Google Maps)
                showToast("Alamat awal: $address") // Menampilkan Toast (fungsi di Base)
            }
            // -------------------------------

            // Menetapkan listener saat map diklik
            setOnMapClickListener(this@MapActivity) // 'this@MapActivity' karena listener di kelas ini
            // Bisa tambahkan listener lain jika perlu, misal:
            // setOnMarkerClickListener(...)
            // setOnCameraIdleListener(...)
        }
    }


    // Dipanggil saat user klik di map (implementasi OnMapClickListener Google Maps)
    // Tidak perlu mengembalikan Boolean
    override fun onMapClick(latLng: LatLng) { // Menerima LatLng Google Maps
        // latLng dari callback ini adalah LatLng lokasi yang diklik
        mLatLng = latLng // Update mLatLng dengan lokasi klik (LatLng Google Maps)

        // Karena mLatLng baru saja di-set, dia tidak null.
        // Menggunakan 'let' untuk kejelasan, atau bisa langsung pakai 'latLng' parameter.
        mLatLng?.let { clickedLatLng -> // clickedLatLng tipenya LatLng Google Maps
            // Mengupdate posisi marker yang sudah ada atau membuatnya
            updateMarker(clickedLatLng) // Menggunakan clickedLatLng (LatLng Google Maps)

            // Menggerakkan kamera map ke lokasi klik
            mMap.animateCamera(CameraUpdateFactory.newLatLng(clickedLatLng)) // Menggunakan clickedLatLng (LatLng Google Maps)

            // Sinkronisasi lat/lon di BaseMapActivity dengan lokasi klik
            lat = clickedLatLng.latitude // lat (Double) diupdate
            lon = clickedLatLng.longitude // lon (Double) diupdate

            // Fetch alamat dari lokasi klik menggunakan Coroutine
            lifecycleScope.launch {
                val address = fetchAddress(clickedLatLng) // Mengambil alamat dari clickedLatLng (LatLng Google Maps)
                showToast("Alamat: $address") // Menampilkan Toast (fungsi di Base)
            }
        }
    }


    // Fetch alamat secara asynchronous
    // Fungsi ini mengambil LatLng (Google Maps), memanggil extension getAddress(), dan mengembalikan String alamat.
    // Fungsi getAddress() diasumsikan ada di file utility (misal di com.roxgps.utils.ext) dan mengembalikan Flow<String> atau sejenisnya.
    private suspend fun fetchAddress(latLng: LatLng): String { // Menerima LatLng Google Maps
        var result = "Alamat tidak ditemukan"
        try {
            // Memanggil extension function getAddress() pada LatLng Google Maps.
            // getAddress() diasumsikan adalah suspend function yang menghasilkan Flow<String>
            latLng.getAddress(this@MapActivity)?.collect { address -> // Menggunakan latLng (LatLng Google Maps) dan Context
                 result = address // Update hasil saat alamat didapat dari Flow
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result = "Gagal mendapatkan alamat: ${e.message}"
        }
        return result // Mengembalikan hasil alamat
    }

    // --- IMPLEMENTASI FUNGSI ABSTRACT getActivityInstance() DIHAPUS DARI BASEMAPACTIVITY ---
    // Implementasi ini dihapus dari sini karena fungsi abstractnya dihapus dari Base
    // override fun getActivityInstance(): BaseMapActivity { ... } // DIHAPUS


    // --- FUNGSI GOOGLE MAPS SPECIFIC LAINNYA ---

    // Update atau tambah marker di map Google Maps
    // Membutuhkan LatLng Google Maps. Pastikan argumen yang diberikan tipenya LatLng Google Maps.
    private fun updateMarker(latLng: LatLng, title: String = "Lokasi") { // Menerima LatLng Google Maps
        if (mMarker == null) {
            // Jika marker belum ada, tambahkan marker baru Google Maps
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng) // Posisi marker (membutuhkan LatLng Google Maps)
                    .title(title) // Judul marker
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Icon marker default
                    .draggable(true) // Jadikan marker bisa digeser (opsional)
                    .visible(true) // Pastikan terlihat
            )
            mMarker?.showInfoWindow() // Tampilkan info window
            // Optional: Tambahkan listener geser marker jika draggable = true
            // mMap.setOnMarkerDragListener(...)
        } else {
            // Jika marker sudah ada, update posisinya
            mMarker?.apply {
                position = latLng // Posisi marker (membutuhkan LatLng Google Maps)
                isVisible = true // Pastikan terlihat
                this.title = title // Update judul
                showInfoWindow() // Tampilkan info window
            }
        }
    }

    // Hapus marker dari map Google Maps
    private fun removeMarker() {
        mMarker?.remove() // Hapus marker Google Maps
        mMarker = null // Set properti marker jadi null
    }

    // ... Tambahkan override fungsi lifecycle lain jika perlu (onPause, onStop, onSaveInstanceState, onActivityResult)
    // ... Tambahkan override onRequestPermissionsResult jika masih dipakai untuk permission LAIN selain lokasi (permission lokasi sudah di LocationHelper)
}
