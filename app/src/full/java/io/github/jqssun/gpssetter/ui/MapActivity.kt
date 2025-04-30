package io.github.jqssun.gpssetter.ui

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
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.utils.FileLogger
import io.github.jqssun.gpssetter.utils.ext.getAddress
import io.github.jqssun.gpssetter.utils.ext.showToast
import kotlinx.coroutines.launch

typealias CustomLatLng = LatLng

class MapActivity: BaseMapActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerDragListener {

    private lateinit var mMap: GoogleMap
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null

    override fun hasMarker(): Boolean {
        FileLogger.log("hasMarker() dipanggil. Marker: ${mMarker != null}", "MapActivity", "D")
        if (mMarker != null) {
            FileLogger.log("Marker ada", "MapActivity", "I")
            return true
        } else {
            FileLogger.log("Marker tidak ada", "MapActivity", "I")
        }
        return false
    }

    private fun updateMarker(it: LatLng) {
        FileLogger.log("updateMarker() dipanggil dengan lat=${it.latitude}, long=${it.longitude}", "MapActivity", "D")
        if (mMarker == null) {
            FileLogger.log("Buat marker baru di lat=${it.latitude}, long=${it.longitude}", "MapActivity", "I")
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(it)
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .visible(true)
            )
        } else {
            FileLogger.log("Update posisi marker ke lat=${it.latitude}, long=${it.longitude}", "MapActivity", "I")
            mMarker?.position = it
            mMarker?.isDraggable = true
            mMarker?.isVisible = true
        }
        mMarker?.let { marker ->
            mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
            FileLogger.log("Map digeser ke marker lat=${marker.position.latitude}, long=${marker.position.longitude}", "MapActivity", "I")
        }
    }

    private fun removeMarker() {
        FileLogger.log("removeMarker() dipanggil", "MapActivity", "D")
        if (mMarker != null) {
            FileLogger.log("Marker disembunyikan (lat=${mMarker?.position?.latitude}, long=${mMarker?.position?.longitude})", "MapActivity", "I")
            mMarker?.isVisible = false
        } else {
            FileLogger.log("Tidak ada marker yang disembunyikan", "MapActivity", "I")
        }
    }

    override fun initializeMap() {
        FileLogger.log("initializeMap() dipanggil", "MapActivity", "D")
        try {
            val mapFragment = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map, mapFragment)
                .commit()
            mapFragment?.getMapAsync(this)
            FileLogger.log("MapFragment diganti dan commit", "MapActivity", "I")
        } catch (e: Exception) {
            FileLogger.log("Error initializeMap: ${e.message}", "MapActivity", "E")
        }
    }

    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        FileLogger.log("moveMapToNewLocation($moveNewLocation) dipanggil", "MapActivity", "D")
        if (moveNewLocation) {
            FileLogger.log("Move ke lokasi baru: lat=$lat, long=$lon", "MapActivity", "I")
            mLatLng = LatLng(lat, lon)
            mLatLng.let { latLng ->
                FileLogger.log("LatLng yang di-set: $latLng", "MapActivity", "I")
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(latLng!!)
                        .zoom(18.0f)
                        .bearing(0f)
                        .tilt(0f)
                        .build()
                ))
                mMarker?.apply {
                    position = latLng
                    isDraggable = true
                    isVisible = true
                    FileLogger.log("Marker diupdate ke: lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "I")
                }
            }
        } else {
            FileLogger.log("Tidak pindah lokasi. lat=$lat, long=$lon", "MapActivity", "I")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        FileLogger.log("onMapReady() dipanggil", "MapActivity", "D")
        mMap = googleMap

        // Aktifkan listener drag marker:
        mMap.setOnMarkerDragListener(this)

        with(mMap) {
            if (ActivityCompat.checkSelfPermission(this@MapActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                setMyLocationEnabled(true)
                FileLogger.log("Permission granted", "MapActivity", "I")
            } else {
                FileLogger.log("Permission not granted, request permission", "MapActivity", "I")
                ActivityCompat.requestPermissions(this@MapActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 99)
            }
            setTrafficEnabled(true)
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = false
            setPadding(0,80,0,0)
            mapType = viewModel.mapType
        }

        val zoom = 18.0f
        lat = viewModel.getLat
        lon  = viewModel.getLng
        FileLogger.log("onMapReady: koordinat awal lat=$lat, long=$lon", "MapActivity", "I")
        mLatLng = LatLng(lat, lon)
        mLatLng.let {
            updateMarker(it!!)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
            FileLogger.log("Marker awal di lat=${it.latitude}, long=${it.longitude}", "MapActivity", "I")
        }

        setOnMapClickListener(this@MapActivity)
        if (viewModel.isStarted) {
            FileLogger.log("ViewModel sudah mulai", "MapActivity", "I")
            mMarker?.let {
                // TODO: it.isVisible = true, it.showInfoWindow()
            }
        } else {
            FileLogger.log("ViewModel belum mulai", "MapActivity", "I")
        }
    }

    override fun onMapClick(latLng: LatLng) {
        FileLogger.log("onMapClick() dipanggil: lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "D")
        mLatLng = latLng
        updateMarker(latLng)
        lat = latLng.latitude
        lon = latLng.longitude
        FileLogger.log("Marker diupdate via klik: lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "I")
    }

    // Listener drag marker Google Maps
    override fun onMarkerDragStart(marker: Marker) {
        FileLogger.log("onMarkerDragStart: Marker di lat=${marker.position.latitude}, long=${marker.position.longitude}", "MapActivity", "I")
    }

    override fun onMarkerDrag(marker: Marker) {
        // Optional: update UI selama drag jika perlu
    }

    override fun onMarkerDragEnd(marker: Marker) {
        FileLogger.log("onMarkerDragEnd: Marker selesai di lat=${marker.position.latitude}, long=${marker.position.longitude}", "MapActivity", "I")
        mLatLng = marker.position
        lat = marker.position.latitude
        lon = marker.position.longitude
        mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
        // Jika ingin update lain, tambahkan di sini
    }

    override fun getActivityInstance(): BaseMapActivity {
        FileLogger.log("getActivityInstance() dipanggil", "MapActivity", "D")
        return this@MapActivity
    }

    @SuppressLint("MissingPermission")
    override fun setupButtons() {
        FileLogger.log("setupButtons() dipanggil", "MapActivity", "D")
        binding.addfavorite.setOnClickListener {
            addFavoriteDialog()
        }
        binding.getlocation.setOnClickListener {
            getLastLocation()
            mLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 18.0f))
                FileLogger.log("Zoom in ke lokasi lat=${it.latitude}, long=${it.longitude}", "MapActivity", "I")
            }
        }

        if (viewModel.isStarted) {
            FileLogger.log("setupButtons: viewModel sudah mulai", "MapActivity", "I")
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
        } else {
            FileLogger.log("setupButtons: viewModel belum mulai", "MapActivity", "I")
        }

        binding.startButton.setOnClickListener {
            viewModel.update(true, lat, lon)
            FileLogger.log("startButton: SET lokasi lat=$lat, long=$lon", "MapActivity", "I")
            mLatLng.let {
                updateMarker(it!!)
            }
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    mLatLng?.getAddress(getActivityInstance())?.let { address ->
                        address.collect { value ->
                            FileLogger.log("Alamat didapat: $value lat=${mLatLng?.latitude} long=${mLatLng?.longitude}", "MapActivity", "I")
                            showStartNotification(value)
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.log("Error collect address: ${e.message}", "MapActivity", "E")
                }
            }
            showToast(getString(R.string.location_set))
        }
        binding.stopButton.setOnClickListener {
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
                FileLogger.log("stopButton: UNSET lokasi lat=${it.latitude}, long=${it.longitude}", "MapActivity", "I")
            }
            removeMarker()
            binding.stopButton.visibility = View.GONE
            binding.startButton.visibility = View.VISIBLE
            cancelNotification()
            showToast(getString(R.string.location_unset))
        }
    }
}