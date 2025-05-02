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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.roxgps.R
import com.roxgps.utils.ext.getAddress
import com.roxgps.utils.ext.showToast
import kotlinx.coroutines.launch

typealias CustomLatLng = LatLng

class MapActivity: BaseMapActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var mMap: GoogleMap
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null

    override fun hasMarker(): Boolean {
        if (!mMarker?.isVisible!!) {
            return true
        }
        return false
    }
    private fun updateMarker(latLng: LatLng) {
    if (mMarker == null) {
        // Tambahkan marker baru jika belum ada
        mMarker = mMap.addMarker(
            MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    } else {
        // Perbarui posisi marker jika sudah ada
        mMarker?.position = latLng
    }
}
private fun removeMarker() {
    mMarker?.remove()
    mMarker = null
}
    override fun initializeMap() {
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commit()
        mapFragment?.getMapAsync(this)
    }
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            mLatLng = LatLng(lat, lon)
            mLatLng.let { latLng ->
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                        .target(latLng!!)
                        .zoom(12.0f)
                        .bearing(0f)
                        .tilt(0f)
                        .build()
                ))
                mMarker?.apply {
                    position = latLng
                    isVisible = true
                    showInfoWindow()
                }
            }
        }
    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap){

            
            // gms custom ui
            if (ActivityCompat.checkSelfPermission(this@MapActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { 
                setMyLocationEnabled(true); 
            } else {
                ActivityCompat.requestPermissions(this@MapActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 99);
            }
            setTrafficEnabled(true)
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = false
            setPadding(0,80,0,0)
            mapType = viewModel.mapType


            val zoom = 12.0f
            lat = viewModel.getLat
            lon  = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng.let {
                mMarker = addMarker(
                    MarkerOptions().position(it!!).draggable(false).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
            }

            
            setOnMapClickListener(this@MapActivity)
            if (viewModel.isStarted){
                mMarker?.let {
                    // TODO:
                    // it.isVisible = true
                    // it.showInfoWindow()
                }
            }
        }
    }
    override fun onMapClick(latLng: LatLng) {
    // Perbarui marker ke lokasi yang diklik
    updateMarker(latLng)

    // Animasi kamera ke lokasi yang diklik
    mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

    // Perbarui nilai lat dan lon
    lat = latLng.latitude
    lon = latLng.longitude
}

    override fun getActivityInstance(): BaseMapActivity {
        return this@MapActivity
    }

    @SuppressLint("MissingPermission")
    override fun setupButtons(){
        binding.addfavorite.setOnClickListener {
            addFavoriteDialog()
        }
        binding.getlocation.setOnClickListener {
            getLastLocation()
        }

        if (viewModel.isStarted) {
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
        }

        binding.startButton.setOnClickListener {
    if (mLatLng != null) {
        viewModel.update(true, lat, lon)

        // Perbarui marker
        updateMarker(mLatLng!!)

        // Tampilkan tombol stop, sembunyikan tombol start
        binding.startButton.visibility = View.GONE
        binding.stopButton.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                mLatLng?.getAddress(getActivityInstance())?.let { addressFlow ->
                    addressFlow.collect { value ->
                        showStartNotification(value)
                    }
                }
                showToast(getString(R.string.location_set))
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(getString(R.string.location_error))
            }
        }
    } else {
        showToast(getString(R.string.invalid_location))
    }
}
        binding.stopButton.setOnClickListener {
    if (mLatLng != null) {
        // Perbarui ViewModel untuk menonaktifkan lokasi
        viewModel.update(false, mLatLng!!.latitude, mLatLng!!.longitude)

        // Hapus marker dari peta
        removeMarker()

        // Atur visibilitas tombol
        binding.stopButton.visibility = View.GONE
        binding.startButton.visibility = View.VISIBLE

        // Batalkan notifikasi
        cancelNotification()

        // Tampilkan pesan kepada pengguna
        showToast(getString(R.string.location_unset))
    } else {
        // Tampilkan pesan jika lokasi tidak valid
        showToast(getString(R.string.invalid_location))
    }
}
    }
}
