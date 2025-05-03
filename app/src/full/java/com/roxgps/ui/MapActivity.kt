package com.roxgps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.roxgps.R
import com.roxgps.utils.ext.getAddress
import com.roxgps.utils.ext.showToast
import kotlinx.coroutines.launch

// Alias ini oke aja buat kejelasan, tapi LatLng bawaan Google Maps itu udah cukup jelas.
// typealias CustomLatLng = LatLng // <-- Bisa dihapus kalau dirasa tidak perlu

// Pastikan BaseMapActivity punya variabel 'lat' dan 'lon' dengan Delegate.notNull<Double>()
// seperti yang pernah kita bahas.
abstract class BaseMapActivity : AppCompatActivity() { // Asumsi extends AppCompatActivity
    // Contoh di BaseMapActivity:
    // var lat: Double by Delegates.notNull()
    // var lon: Double by Delegates.notNull()
    // ...
}


class MapActivity : BaseMapActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var mMap: GoogleMap // Akan diinisialisasi di onMapReady, tidak bisa null setelah itu
    // Variabel mLatLng, bisa null. Menyimpan LatLng lokasi yang sedang aktif/ditampilkan.
    // Karena ini 'var' (mutable), kompiler Kotlin kadang hati-hati dengan 'smart cast'
    // saat diakses di konteks concurrency (meskipun di sini utamanya di UI thread).
    // Penanganan nullability pakai ?.let sudah benar.
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null // Marker di map, bisa null kalau belum ditambah atau sudah dihapus

    // Fungsi ini kayaknya mau cek apakah marker TIDAK terlihat, tapi logikanya terbalik.
    // hasMarker() biasanya cek apakah marker ADA dan TERLIHAT.
    // Lo bisa ganti ini sesuai kebutuhan:
    override fun hasMarker(): Boolean {
        // Jika mau cek apakah ada marker DAN terlihat:
        return mMarker?.isVisible == true
        // Jika mau cek apakah ada marker (terlihat atau tidak):
        // return mMarker != null
        // Jika mau cek apakah marker TIDAK terlihat (sesuai kode awal lo, tapi namanya membingungkan):
        // return mMarker?.isVisible != true
    }

    // Dipanggil saat user klik di map
    override fun onMapClick(latLng: LatLng) {
        mLatLng = latLng // Set mLatLng dengan lokasi klik (LatLng ini sendiri tidak null)
        // Karena mLatLng barusan diset ke LatLng non-null, mLatLng?.let { ... } ini akan selalu true.
        // Lo bisa langsung pakai 'latLng' yang dari parameter method ini di dalam blok ini
        // atau tetap pakai ?.let (aman, hanya sedikit redundan).
        mLatLng?.let {
            updateMarker(it) // Update posisi marker ke lokasi klik
            mMap.animateCamera(CameraUpdateFactory.newLatLng(it)) // Gerakkan kamera ke lokasi klik

            // Sinkronisasi ke variabel lat/lon di BaseMapActivity (sudah benar)
            lat = it.latitude // Latitude dari LatLng (tipe Double) diset ke lat (tipe Double)
            lon = it.longitude // Longitude dari LatLng (tipe Double) diset ke lon (tipe Double)

            // Fetch alamat dari lokasi klik menggunakan Coroutine
            lifecycleScope.launch {
                // it.getAddress(this@MapActivity) mengembalikan Flow<String>
                // .collect { address -> ... } akan dijalankan setiap kali ada nilai baru dari Flow (harusnyasih sekali aja untuk getAddress)
                it.getAddress(this@MapActivity)?.collect { address ->
                    showToast("Alamat: $address") // Tampilkan alamat
                }
                // Catatan: Pastikan ekstensi getAddress() ini async/suspend dan mengembalikan Flow dengan benar
            }
        }
    }

    // Update atau tambah marker di map
    private fun updateMarker(latLng: LatLng, title: String = "Lokasi") {
        if (mMarker == null) {
            // Jika marker belum ada, tambahkan marker baru
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng) // Set posisi marker (membutuhkan LatLng)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Icon default merah
            )
            mMarker?.showInfoWindow() // Tampilkan info window marker
        } else {
            // Jika marker sudah ada, update propertinya
            mMarker?.apply { // Gunakan apply scope biar lebih ringkas
                position = latLng // Update posisi (membutuhkan LatLng)
                isVisible = true // Pastikan marker terlihat
                this.title = title // Update judul
                showInfoWindow() // Tampilkan info window
            }
        }
    }

    // Hapus marker dari map
    private fun removeMarker() {
        mMarker?.remove() // Hapus marker jika tidak null
        mMarker = null // Set mMarker jadi null setelah dihapus
    }

    // Inisialisasi Map (dipanggil dari luar, misal onCreate)
    override fun initializeMap() {
        // Tambahkan fragment map ke activity
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment) // Replace container R.id.map dengan fragment map
            .commit()
        // Minta objek GoogleMap secara asynchronous
        mapFragment.getMapAsync(this) // 'this' refers to MapActivity implementing OnMapReadyCallback
    }

    // Pindah kamera map ke lokasi baru
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            // Bikin objek LatLng dari lat dan lon (dari BaseMapActivity)
            // lat dan lon harus sudah punya nilai Double karena pakai Delegates.notNull
            val localLatLng = LatLng(lat, lon)
            // Set mLatLng. Sekali lagi, mLatLng?.let di bawah ini akan selalu true.
            mLatLng = localLatLng
            mLatLng?.let { latLng ->
                // Gerakkan kamera ke lokasi baru dengan zoom dan properti kamera lainnya
                mMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(latLng) // Target kamera ke LatLng baru
                            .zoom(18.0f) // Level zoom
                            .bearing(0f)
                            .tilt(0f)
                            .build()
                    )
                )
                // Update marker (jika ada) ke lokasi baru
                mMarker?.apply {
                    position = latLng // Set posisi marker
                    isDraggable = true // Jadikan marker bisa digeser (jika perlu)
                    isVisible = true // Pastikan marker terlihat
                }

                // Fetch alamat dari lokasi baru menggunakan Coroutine
                lifecycleScope.launch {
                    val address = fetchAddress(latLng) // Panggil suspend function fetchAddress
                    showToast("Alamat: $address") // Tampilkan alamat
                }
            }
        }
    }

    // Dipanggil saat GoogleMap sudah siap digunakan
    @SuppressLint("MissingPermission") // Anotasi ini menandai kalau permission check dilakukan secara manual di dalam method
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap // Simpan objek GoogleMap

        // Cek dan minta permission lokasi jika belum diizinkan
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION // Permission lokasi akurat
            ) == PackageManager.PERMISSION_GRANTED // Jika permission sudah diberikan
        ) {
            mMap.isMyLocationEnabled = true // Aktifkan layer "My Location" (titik biru lokasi user)
        } else {
            // Jika belum, minta permission dari user
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), // Minta permission ACCESS_FINE_LOCATION
                99 // Request code
            )
        }

        // Konfigurasi properti map lainnya
        mMap.apply {
            setTrafficEnabled(true) // Tampilkan informasi lalu lintas
            uiSettings.apply {
                isMyLocationButtonEnabled = false // Matikan tombol "My Location" bawaan (karena kita mungkin pakai tombol custom)
                isZoomControlsEnabled = false // Matikan tombol zoom bawaan
                isCompassEnabled = false // Matikan kompas bawaan
            }
            setPadding(0, 80, 0, 0) // Set padding (misal untuk menghindari UI overlap)
            mapType = viewModel.mapType // Set tipe map (Normal, Satellite, Hybrid, Terrain) dari ViewModel

            val zoom = 15.0f // Level zoom awal
            // Ambil lat/lon awal dari ViewModel (sudah Double dari BaseMapActivity)
            lat = viewModel.getLat
            lon = viewModel.getLng
            val initialLatLng = LatLng(lat, lon) // Bikin LatLng awal (dari Double, aman)
            mLatLng = initialLatLng // Set mLatLng awal

            // Tambahkan marker awal tapi disembunyikan (visible = false)
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(initialLatLng) // Set posisi awal marker
                    .draggable(false) // Tidak bisa digeser
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .visible(false) // Mulai dalam keadaan tersembunyi
            )

            // Gerakkan kamera ke lokasi awal dengan zoom
            animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, zoom))

            // Fetch alamat awal dan tampilkan (menggunakan Coroutine)
            lifecycleScope.launch {
                // initialLatLng adalah LatLng (sudah Double). getAddress() butuh Context.
                // Error Type Mismatch (Float vs Double) di baris 192:
                // Error ini mungkin terjadi di bagian SINI atau di method fetchAddress().
                // Pastikan SEMUA nilai koordinat yang dipakai untuk membuat LatLng atau memanggil method
                // yang butuh Double (seperti LatLng(lat, lon), atau method getAddress() itu sendiri
                // jika parameternya LatLng atau Double) adalah DOUBLE.
                // Contoh: Jika sumber lat atau lon di ViewModel tiba-tiba Float,
                // pastikan dikonversi .toDouble() sebelum dipakai di LatLng().
                // Misalnya, JIKA error di baris ini (atau di dalam getAddress/fetchAddress)
                // itu karena lo ngasih Float value dari viewModel:
                // val latFloatFromViewModel: Float = viewModel.someFloatLat // <-- Contoh JIKA sumbernya Float
                // val lonFloatFromViewModel: Float = viewModel.someFloatLon // <-- Contoh JIKA sumbernya Float
                // val initialLatLng = LatLng(latFloatFromViewModel.toDouble(), lonFloatFromViewModel.toDouble()) // <-- KONVERSI!
                // Karena di kode lo Lat/Lon dari ViewModel sudah diset ke Double di 'lat'/'lon',
                // pembuatan initialLatLng dari 'lat'/'lon' ini seharusnya AMAN (sudah Double).
                // Error di baris 192 kemungkinan di pemakaian 'initialLatLng' ini atau 'it' (LatLng)
                // di method getAddress() atau di method fetchAddress() jika di sana ada kode yang salah mengolah tipe data.
                // Trace baris 192, lihat data apa yang dipakai dan pastikan tipenya Double.
                initialLatLng.getAddress(this@MapActivity)?.collect { address ->
                    showToast("Alamat awal: $address") // Tampilkan alamat awal
                }
            }

            setOnMapClickListener(this@MapActivity) // Set listener klik map
        }
    }

    // Mengembalikan instance MapActivity (kayaknya buat keperluan Dependency Injection atau sejenisnya)
    override fun getActivityInstance(): BaseMapActivity = this

    // Fetch alamat secara asynchronous (suspend function)
    // Dipanggil dari moveMapToNewLocation dan startButton click
    private suspend fun fetchAddress(latLng: LatLng): String {
        var result = "Alamat tidak ditemukan"
        // customLatLng di sini cuma alias, tidak mengubah tipe LatLng
        // val customLatLng = CustomLatLng(latLng.latitude, latLng.longitude) // <-- Line 192 KEMUNGKINAN DI SEKITAR SINI ATAU DI DALAM getAddress/fetchAddress
        // Jika error type mismatch di baris 192 terjadi saat membuat LatLng:
        // Pastikan latLng.latitude dan latLng.longitude itu Double (memang sudah Double karena LatLng).
        // Error lebih mungkin saat *pemakaian* customLatLng atau latLng ini di method getAddress.
        // Misalnya, JIKA getAddress menerima Float:
        // customLatLng.getAddress(this@MapActivity, customLatLng.latitude.toFloat(), customLatLng.longitude.toFloat()) <-- INI AKAN ERROR kalau getAddress butuh Double
        // Pastikan signature getAddress() itu terima Double atau LatLng.
        // customLatLng.getAddress(this@MapActivity)?.collect { address -> // Baris asli lo kayaknya begini
        //    result = address
        // }
        // Solusi paling pasti: cek signature method getAddress() dan method lain yang dipanggil di dalamnya.
        // Pastikan semua operasi dan parameter yang butuh Double memang menerima Double.

        // Kode asli lo:
        latLng.getAddress(this@MapActivity)?.collect { address ->
             result = address
        }
        return result
    }

    @SuppressLint("MissingPermission") // Anotasi ini menandai kalau permission check dilakukan secara manual
    override fun setupButtons() {
        binding.addfavorite.setOnClickListener { // Button tambah favorit
            addFavoriteDialog() // Panggil fungsi tambah favorit (kayaknya dari BaseMapActivity)
        }

        binding.getlocation.setOnClickListener { // Button dapatkan lokasi terakhir
            getLastLocation() // Panggil fungsi dapatkan lokasi terakhir (kayaknya dari BaseMapActivity)
            // Setelah dapat lokasi, mLatLng mungkin diset di getLastLocation() atau di listener lokasi
            mLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 18.0f)) // Gerakkan kamera ke lokasi jika mLatLng ada
            }
        }

        binding.startButton.setOnClickListener { // Button mulai (set lokasi palsu)
            mLatLng?.let { latLng -> // Pastikan mLatLng sudah ada (user sudah klik map atau dapat lokasi)
                viewModel.update(true, latLng.latitude, latLng.longitude) // Update ViewModel: set isStarted=true, lat, lon
                updateMarker(latLng, "Harapan Palsu") // Update marker (judul "Harapan Palsu" sesuai screenshot 😂)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f)) // Gerakkan kamera
                binding.startButton.visibility = View.GONE // Sembunyikan start button
                binding.stopButton.visibility = View.VISIBLE // Tampilkan stop button

                // Fetch alamat dan tampilkan notifikasi/toast
                lifecycleScope.launch {
                    try {
                        val address = fetchAddress(latLng) // Fetch alamat (menggunakan lokasi dari mLatLng)
                        showStartNotification(address) // Tampilkan notifikasi start (kayaknya dari BaseMapActivity)
                        showToast(getString(R.string.location_set)) // Tampilkan toast "Lokasi diset"
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(getString(R.string.location_error)) // Tampilkan toast error
                    }
                }
            } ?: showToast(getString(R.string.invalid_location)) // Jika mLatLng null, tampilkan toast error
        }

        binding.stopButton.setOnClickListener { // Button berhenti
            mLatLng?.let { // Pastikan mLatLng ada
                viewModel.update(false, it.latitude, it.longitude) // Update ViewModel: set isStarted=false
            }
            removeMarker()
            binding.stopButton.visibility = View.GONE
            binding.startButton.visibility = View.VISIBLE
            cancelNotification()
            showToast(getString(R.string.location_unset)) // Tampilkan toast "Lokasi dihapus"
            // Catatan: Kode untuk menampilkan kembali start/stop button setelah stop belum ada di sini.
            // binding.startButton.visibility = View.VISIBLE
            // binding.stopButton.visibility = View.GONE // Tambahin kalau perlu
        }
    }
}
