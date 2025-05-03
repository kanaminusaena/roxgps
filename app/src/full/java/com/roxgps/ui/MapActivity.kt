package com.roxgps.ui // Sesuaikan dengan package Activity kamu

// --- IMPORTS YANG DIBUTUHKAN OLEH KODE MAPACTIVITY INI ---
import android.Manifest // Untuk permission
import android.annotation.SuppressLint // Untuk @SuppressLint
import android.content.pm.PackageManager // Untuk cek permission result
// ... import lain dari Android SDK ...
import android.os.Bundle // Untuk Bundle
import android.view.View // Untuk View
import androidx.core.app.ActivityCompat // Untuk cek/request permission (sisa yang di onMapReady akan dihapus)
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
import com.roxgps.utils.ext.showToast // Asumsi ekstensi Context ke Toast
import com.roxgps.utils.NetworkUtils // Utility cek koneksi (jika dipakai di sini selain di Base)
// ------------------------------------------------------------
// --- IMPORTS DARI BASEMAPACTIVITY YANG SUDAH DIREFACTOR ---
import com.roxgps.ui.BaseMapActivity // Import BaseMapActivity yang sudah direfaktor
// Import helper-helper jika kamu perlu mengaksesnya langsung dari MapActivity (jarang, biasanya lewat Base)
// import com.roxgps.helper.LocationHelper
// import com.roxgps.helper.LocationListener // Jika implement listener langsung di sini (biasanya di Base)
// import com.roxgps.helper.DialogHelper
// import com.roxgps.helper.NotificationHelper
// import com.xgps.helper.SearchHelper
// import com.roxgps.helper.SearchProgress // Jika digunakan langsung di sini
// ---------------------------------------------------------
import kotlinx.coroutines.launch // Untuk launch coroutine
import androidx.appcompat.app.AppCompatActivity // BaseMapActivity extend ini
// import androidx.lifecycle.ViewModel // Tidak perlu diimpor di sini jika ViewModel diakses lewat properti di BaseMapActivity
import kotlin.properties.Delegates // Delegates.notNull (di Base)


// --- KODE BASEMAPACTIVITY, DUMMY VIEWMODEL & BINDING DI BAWAH INI DIHAPUS KARENA SUDAH PUNYA FILE SENDIRI YANG SUDAH DIREFACTOR ---
// abstract class BaseMapActivity : AppCompatActivity() { ... } // DIHAPUS
// class DummyMapViewModel { ... } // DIHAPUS
// class DummyBinding { ... } // DIHAPUS
// -------------------------------------------------------------------------------------------------------------------------------


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
    // Kamu tidak perlu mendeklarasikan ulang di sini kecuali jika kamu punya binding layout yang BERBEDA untuk MapActivity ini.
    // Jika kamu menggunakan layout yang sama dan binding yang sama dengan BaseMapActivity, properti binding di BaseMapActivity sudah cukup.
    // val binding: ActivityMapBinding // Pastikan ini sudah dideklarasi dan diinisialisasi di BaseMapActivity
    override fun stopProcess() {
        // Cek mLatLng, karena logic update ViewModel dan remove marker butuh info lokasi terakhir
        mLatLng?.let { currentLatLng ->
            // 1. Update status di ViewModel
            // ViewModel diakses dari BaseMapActivity. update() adalah fungsi ViewModel.
            viewModel.update(false, currentLatLng.latitude, currentLatLng.longitude) // Menggunakan currentLatLng (LatLng)
        }
        // Jika mLatLng null saat stop diklik, update ViewModel mungkin tidak menyertakan lokasi.
        // Pastikan ViewModel.update bisa handle null location atau ada logic lain.

        // 2. Hapus marker dari map Google Maps (fungsi di MapActivity)
        removeMarker()

        // 3. Mengubah tampilan tombol UI (akses binding di MapActivity)
        binding.startButton.visibility = View.VISIBLE
        binding.stopButton.visibility = View.GONE

        // 4. Menghentikan notifikasi (fungsi ini ada di BaseMapActivity yang panggil NotificationHelper)
        cancelNotification() // cancelNotification() ada di BaseMapActivity yang direfaktor

        // 5. Menampilkan Toast (fungsi ini ada di BaseMapActivity)
        showToast(getString(R.string.location_unset)) // showToast() ada di BaseMapActivity yang direfaktor
    }

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
            val targetLatLng = LatLng(lat, lon)

            // Optional: Update mLatLng di sini jika mLatLng selalu merepresentasikan lokasi di tengah layar/marker
            mLatLng = targetLatLng

            // Menggerakkan kamera map Google Maps
            mMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(targetLatLng) // Target koordinat
                        .zoom(18.0f) // Level zoom
                        .bearing(0f) // Orientasi (utara ke atas)
                        .tilt(0f) // Sudut pandang
                        .build()
                )
            )

            // Mengupdate posisi marker yang sudah ada atau membuatnya jika belum ada
            // Jika marker belum ada, updateMarker akan menambahkannya
             updateMarker(targetLatLng) // Membutuhkan LatLng

            // Fetch alamat dari lokasi baru menggunakan Coroutine
            lifecycleScope.launch {
                 // Fungsi getAddress() ini adalah extension function (asumsi ada di com.roxgps.utils.ext)
                val address = fetchAddress(targetLatLng) // Mengambil alamat dari LatLng
                showToast("Alamat: $address") // Menampilkan Toast
            }
        }
    }

    // Implementasi setupButtons: Menetapkan click listeners untuk tombol-tombol
    // Panggilan fungsi di sini DIUBAH agar sesuai dengan BaseMapActivity yang sudah direfaktor (menggunakan helper atau logic baru di Base)
    @SuppressLint("MissingPermission") // Mungkin masih perlu kalau ada akses permission langsung di sini (seharusnya tidak)
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
            requestLocation() // Asumsikan ada fungsi requestLocation() di BaseMapActivity yang direfaktor

            // Update map (animasi kamera) berdasarkan lokasi BARU yang didapat dari LocationHelper
            // seharusnya dilakukan di onLocationResult di BaseMapActivity, BUKAN di sini setelah panggil request.
            // Jadi, kode animasi kamera di sini dihapus.
            // mLatLng?.let { ... animateCamera ... } // DIHAPUS
        }

        // Listener untuk tombol Start Proses (misal: start simulasi lokasi)
        binding.startButton.setOnClickListener {
            mLatLng?.let { currentLatLng -> // Pastikan mLatLng (lokasi terakhir yang diketahui/diset) tidak null
                // Update status dan koordinat di ViewModel (ViewModel diakses dari BaseMapActivity)
                viewModel.update(true, currentLatLng.latitude, currentLatLng.longitude)

                // Update marker di map Google Maps
                updateMarker(currentLatLng, "Harapan Palsu") // Menggunakan currentLatLng (LatLng)

                // Gerakkan kamera
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15.0f)) // Menggunakan currentLatLng (LatLng)

                // Tampilkan/Sembunyikan Button UI spesifik MapActivity ini
                binding.startButton.visibility = View.GONE // Sembunyikan Start
                binding.stopButton.visibility = View.VISIBLE // Tampilkan Stop

                // Fetch alamat dan tampilkan notifikasi awal menggunakan Coroutine
                lifecycleScope.launch {
                    try {
                        val address = fetchAddress(currentLatLng) // Fetch alamat dari currentLatLng (LatLng)
                        // Panggil fungsi di BaseMapActivity yang menggunakan NotificationHelper
                        // BaseMapActivity yang direfaktor akan memanggil notificationHelper.showStartNotification()
                        showStartNotification(address) // Fungsi ini sekarang ada di BaseMapActivity yang direfaktor
                        showToast(getString(R.string.location_set)) // Tampilkan toast sukses
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(getString(R.string.location_error)) // Tampilkan toast error
                    }
                }
            } ?: showToast(getString(R.string.invalid_location)) // Jika mLatLng null, tampilkan toast
        }

        // Listener untuk tombol Stop Proses
        binding.stopButton.setOnClickListener {
            stopProcess() // <-- Panggil fungsi stopProcess() yang baru di MapActivity ini
        }
    }

    // --- FUNGSI GOOGLE MAPS SPECIFIC YANG TERSISA DI MAPACTIVITY INI ---

    // Dipanggil saat GoogleMap siap digunakan
    @SuppressLint("MissingPermission") // Suppress warning karena permission dicek dan diminta oleh LocationHelper (via BaseActivity)
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap // Simpan referensi GoogleMap

        // --- LOGIC PERMISSION LOKASI DI SINI DIHAPUS KARENA SUDAH DITANGANI LOCATIONHELPER ---
        // if (ActivityCompat.checkSelfPermission(...) { ... } else { ... } // DIHAPUS
        // -------------------------------------------------------------------------------

        // Setelah map siap, kita bisa aktifkan layer "My Location" JIKA izin sudah diberikan
        // Status izin bisa didapat dari LocationHelper.checkLocationPermissions()
        // atau setelah onPermissionGranted() di BaseMapActivity dipanggil.
        if (locationHelper.checkLocationPermissions()) { // Panggil check permission dari helper via Base
             mMap.isMyLocationEnabled = true // Aktifkan layer "My Location" Google Maps
             // Optional: Kalau mau langsung tampilkan lokasi user di map saat onMapReady DAN izin ada
             // locationHelper.requestLastKnownLocation() // Panggil request lokasi via Base
        } else {
             // Jika izin belum ada, layer "My Location" tidak diaktifkan dulu.
             // Akan diaktifkan nanti di onPermissionGranted() di BaseActivity jika izin diberikan.
        }


        // Konfigurasi setting UI GoogleMap
        mMap.apply {
            setTrafficEnabled(true)
            uiSettings.apply {
                isMyLocationButtonEnabled = false // Tombol "My Location" bawaan
                isZoomControlsEnabled = false // Tombol zoom bawaan
                isCompassEnabled = false // Kompas bawaan
            }
            // Padding map agar tidak terhalang UI (misal AppBar atau tombol)
            setPadding(0, 80, 0, 0) // Sesuaikan padding dengan tinggi UI elemen kamu

            // Set tipe map dari ViewModel (ViewModel diakses dari BaseMapActivity)
            mapType = viewModel.mapType // mapType diasumsikan ada di ViewModel

            // --- Tentukan posisi awal map ---
            // Ambil lat/lon awal dari ViewModel (properti di BaseMapActivity yang sudah diinisialisasi)
            val initialLatLng = LatLng(lat, lon) // Membuat LatLng dari lat/lon

            // Set mLatLng awal (opsional, jika mLatLng perlu merepresentasikan posisi awal)
            mLatLng = initialLatLng

            // Tambahkan marker awal (invisible, tidak bisa digeser)
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(initialLatLng) // Posisi awal marker
                    .draggable(false) // Tidak bisa digeser
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Icon marker
                    .visible(false) // Awalnya tidak terlihat
            )

            // Gerakkan kamera map ke lokasi awal
            animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15.0f)) // Animasi kamera

            // Fetch alamat dari lokasi awal menggunakan Coroutine
            lifecycleScope.launch {
                val address = fetchAddress(initialLatLng) // Mengambil alamat
                showToast("Alamat awal: $address") // Menampilkan Toast
            }
             // -------------------------------

            // Menetapkan listener saat map diklik
            setOnMapClickListener(this@MapActivity) // 'this@MapActivity' karena listener di kelas ini
            // Bisa tambahkan listener lain jika perlu, misal:
            // setOnMarkerClickListener(...)
            // setOnCameraIdleListener(...)
        }
    }

    // Dipanggil saat user klik di map (implementasi dari OnMapClickListener)
    override fun onMapClick(latLng: LatLng) {
        // latLng dari callback ini adalah LatLng lokasi yang diklik
        mLatLng = latLng // Update mLatLng dengan lokasi klik

        // Karena mLatLng baru saja di-set, dia tidak null.
        // Menggunakan 'let' untuk kejelasan, atau bisa langsung pakai 'latLng' parameter.
        mLatLng?.let { clickedLatLng ->
            // Mengupdate posisi marker yang sudah ada
            updateMarker(clickedLatLng) // Menggunakan clickedLatLng (LatLng)

            // Menggerakkan kamera map ke lokasi klik
            mMap.animateCamera(CameraUpdateFactory.newLatLng(clickedLatLng)) // Menggunakan clickedLatLng (LatLng)

            // Sinkronisasi lat/lon di BaseMapActivity dengan lokasi klik
            lat = clickedLatLng.latitude // lat (Double) diupdate
            lon = clickedLatLng.longitude // lon (Double) diupdate

            // Fetch alamat dari lokasi klik menggunakan Coroutine
            lifecycleScope.launch {
                val address = fetchAddress(clickedLatLng) // Mengambil alamat dari clickedLatLng (LatLng)
                showToast("Alamat: $address") // Menampilkan Toast
            }
        }
    }


    // Fetch alamat secara asynchronous
    // Fungsi ini mengambil LatLng, memanggil extension getAddress(), dan mengembalikan String alamat.
    // Fungsi getAddress() diasumsikan ada di file utility (misal di com.roxgps.utils.ext) dan mengembalikan Flow<String> atau sejenisnya.
    private suspend fun fetchAddress(latLng: LatLng): String { // Menerima LatLng
        var result = "Alamat tidak ditemukan"
        try {
            // Memanggil extension function getAddress() pada LatLng.
            // getAddress() diasumsikan adalah suspend function yang menghasilkan Flow<String>
            latLng.getAddress(this@MapActivity)?.collect { address -> // Menggunakan latLng (LatLng) dan Context
                 result = address // Update hasil saat alamat didapat dari Flow
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result = "Gagal mendapatkan alamat: ${e.message}"
        }
        return result // Mengembalikan hasil alamat
    }

    // --- FUNGSI GOOGLE MAPS SPECIFIC LAINNYA ---

    // Update atau tambah marker di map Google Maps
    // Membutuhkan LatLng. Pastikan argumen yang diberikan tipenya LatLng.
    private fun updateMarker(latLng: LatLng, title: String = "Lokasi") { // Menerima LatLng
        if (mMarker == null) {
            // Jika marker belum ada, tambahkan marker baru
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng) // Posisi marker (membutuhkan LatLng)
                    .title(title) // Judul marker
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Icon marker
                    .draggable(true) // Jadikan marker bisa digeser (opsional)
                    .visible(true) // Pastikan terlihat
            )
            mMarker?.showInfoWindow() // Tampilkan info window
            // Optional: Tambahkan listener geser marker jika draggable = true
            // mMap.setOnMarkerDragListener(...)
        } else {
            // Jika marker sudah ada, update posisinya
            mMarker?.apply {
                position = latLng // Posisi marker (membutuhkan LatLng)
                isVisible = true // Pastikan terlihat
                this.title = title // Update judul
                showInfoWindow() // Tampilkan info window
            }
        }
    }

    // Hapus marker dari map Google Maps
    private fun removeMarker() {
        mMarker?.remove() // Hapus marker
        mMarker = null // Set properti marker jadi null
    }

    // ... Tambahkan override fungsi lifecycle lain jika perlu (onPause, onStop, onSaveInstanceState, onActivityResult)
    // Misalnya, simpan status map di onSaveInstanceState
    // override fun onSaveInstanceState(outState: Bundle) { ... super.onSaveInstanceState(outState) }

    // ... Tambahkan override onRequestPermissionsResult jika masih dipakai untuk permission LAIN selain lokasi (permission lokasi sudah di LocationHelper)
    // override fun onRequestPermissionsResult(...) { ... }

}
