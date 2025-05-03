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

typealias CustomLatLng = LatLng

class MapActivity : BaseMapActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var mMap: GoogleMap
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null

    override fun hasMarker(): Boolean {
        return mMarker?.isVisible != true
    }

    override fun onMapClick(latLng: LatLng) {
        mLatLng = latLng
        mLatLng?.let {
            updateMarker(it)
            mMap.animateCamera(CameraUpdateFactory.newLatLng(it))
            lat = it.latitude
            lon = it.longitude

            lifecycleScope.launch {
                it.getAddress(this@MapActivity)?.collect { address ->
                    showToast("Alamat: $address")
                }
            }
        }
    }

    private fun updateMarker(latLng: LatLng, title: String = "Lokasi") {
        if (mMarker == null) {
            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            mMarker?.showInfoWindow()
        } else {
            mMarker?.apply {
                position = latLng
                isVisible = true
                this.title = title
                showInfoWindow()
            }
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
        mapFragment.getMapAsync(this)
    }

    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            val localLatLng = LatLng(lat, lon)
            mLatLng = localLatLng
            mLatLng?.let { latLng ->
                mMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(latLng)
                            .zoom(18.0f)
                            .bearing(0f)
                            .tilt(0f)
                            .build()
                    )
                )
                mMarker?.apply {
                    position = latLng
                    isDraggable = true
                    isVisible = true
                }

                lifecycleScope.launch {
                    val address = fetchAddress(latLng)
                    showToast("Alamat: $address")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                99
            )
        }

        mMap.apply {
            setTrafficEnabled(true)
            uiSettings.apply {
                isMyLocationButtonEnabled = false
                isZoomControlsEnabled = false
                isCompassEnabled = false
            }
            setPadding(0, 80, 0, 0)
            mapType = viewModel.mapType

            val zoom = 15.0f
            lat = viewModel.getLat
            lon = viewModel.getLng
            val initialLatLng = LatLng(lat, lon)
            mLatLng = initialLatLng

            mMarker = mMap.addMarker(
                MarkerOptions()
                    .position(initialLatLng)
                    .draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .visible(false)
            )

            animateCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, zoom))

            lifecycleScope.launch {
                initialLatLng.getAddress(this@MapActivity)?.collect { address ->
                    showToast("Alamat awal: $address")
                }
            }

            setOnMapClickListener(this@MapActivity)
        }
    }

    override fun getActivityInstance(): BaseMapActivity = this

    private suspend fun fetchAddress(latLng: LatLng): String {
        var result = "Alamat tidak ditemukan"
        val customLatLng = CustomLatLng(latLng.latitude, latLng.longitude)
        customLatLng.getAddress(this@MapActivity)?.collect { address ->
            result = address
        }
        return result
    }

    @SuppressLint("MissingPermission")
    override fun setupButtons() {
        binding.addfavorite.setOnClickListener {
            addFavoriteDialog()
        }

        binding.getlocation.setOnClickListener {
            getLastLocation()
            mLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 18.0f))
            }
        }

        binding.startButton.setOnClickListener {
            mLatLng?.let { latLng ->
                viewModel.update(true, latLng.latitude, latLng.longitude)
                updateMarker(latLng, "Harapan Palsu")
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f))
                binding.startButton.visibility = View.GONE
                binding.stopButton.visibility = View.VISIBLE

                lifecycleScope.launch {
                    try {
                        val address = fetchAddress(latLng)
                        showStartNotification(address)
                        showToast(getString(R.string.location_set))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(getString(R.string.location_error))
                    }
                }
            } ?: showToast(getString(R.string.invalid_location))
        }

        binding.stopButton.setOnClickListener {
            mLatLng?.let {
                viewModel.update(false, it.latitude, it.longitude)
            }
            removeMarker()
            showToast(getString(R.string.location_unset))
        }
    }
}