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
import io.github.jqssun.gpssetter.utils.ext.getAddress
import io.github.jqssun.gpssetter.utils.ext.showToast
import kotlinx.coroutines.launch

typealias CustomLatLng = LatLng

class MapActivity : BaseMapActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var mMap: GoogleMap
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null

    override fun hasMarker(): Boolean {
        // Returns true if marker is NOT visible (so location is not selected)
        return !(mMarker?.isVisible ?: false)
    }

    /**
     * Updates or adds the marker at the given LatLng and animates the camera to it.
     */
    private fun updateMarker(it: LatLng) {
        mMarker?.position = it
        mMarker?.isVisible = true
        // Automatically move/zoom the map to the marker
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f)) // 17f = street level
    }

    /**
     * Hides the marker.
     */
    private fun removeMarker() {
    mMarker?.isVisible = false
    // Zoom out the map to a world view (zoom level 2)
    mLatLng?.let {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
    }
}

    /**
     * Sets up the map fragment and requests async map load.
     */
    override fun initializeMap() {
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)
    }

    /**
     * Moves the map to the new location if requested, and updates the marker.
     */
    override fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            mLatLng = LatLng(lat, lon)
            mLatLng?.let { latLng ->
                updateMarker(latLng)
            }
        }
    }

    /**
     * Called when the GoogleMap is ready to use.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap) {
            if (ActivityCompat.checkSelfPermission(
                    this@MapActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                setMyLocationEnabled(true)
            } else {
                ActivityCompat.requestPermissions(
                    this@MapActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    99
                )
            }
            setTrafficEnabled(true)
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = false
            setPadding(0, 80, 0, 0)
            mapType = viewModel.mapType

            lat = viewModel.getLat
            lon = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng?.let {
                mMarker = addMarker(
                    MarkerOptions().position(it)
                        .draggable(false)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        .visible(false)
                )
                animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12.0f))
            }

            setOnMapClickListener(this@MapActivity)
            if (viewModel.isStarted) {
                mMarker?.let {
                    // Optionally show marker and info window
                     it.isVisible = true
                     it.showInfoWindow()
                }
            }
        }
         setupZoomButtons()
    }

    /**
     * Handles map clicks by updating the marker and moving the map.
     */
    override fun onMapClick(latLng: LatLng) {
        mLatLng = latLng
        mLatLng?.let {
            updateMarker(it)
            lat = it.latitude
            lon = it.longitude
        }
    }

    override fun getActivityInstance(): BaseMapActivity {
        return this@MapActivity
    }

    /**
     * Sets up UI buttons and their actions.
     */
    @SuppressLint("MissingPermission")
    override fun setupButtons() {
        binding.addfavorite.setOnClickListener {
            addFavoriteDialog()
        }
        binding.getlocation.setOnClickListener {
            getLastLocation()
            // When location is updated, moveMapToNewLocation(true) will trigger updateMarker and animate the camera
            mLatLng = LatLng(lat, lon)
            mLatLng?.let { updateMarker(it) }
        }

        if (viewModel.isStarted) {
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
        }

        binding.startButton.setOnClickListener {
            viewModel.update(true, lat, lon)
            mLatLng?.let {
                updateMarker(it)
            }
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
            lifecycleScope.launch {
                mLatLng?.getAddress(getActivityInstance())?.let { address ->
                    address.collect { value ->
                        showStartNotification(value)
                    }
                }
            }
            showToast(getString(R.string.location_set))
        }
        binding.stopButton.setOnClickListener {
            mLatLng?.let {
                viewModel.update(false, it.latitude, it.longitude)
            }
            removeMarker()
            binding.stopButton.visibility = View.GONE
            binding.startButton.visibility = View.VISIBLE
            cancelNotification()
            showToast(getString(R.string.location_unset))
        }
    }
    
    /**
     * Setup Plus and Minus Zoom Buttons (must be present in your layout)
     */
    private fun setupZoomButtons() {
        // Assuming your layout has ImageButtons with ids plusButton and minusButton
        plusButton = findViewById(R.id.plusButton)
        minusButton = findViewById(R.id.minusButton)

        plusButton.setOnClickListener {
            val currentZoom = mMap.cameraPosition.zoom
            mMap.animateCamera(CameraUpdateFactory.zoomTo(currentZoom + 1f))
        }
        minusButton.setOnClickListener {
            val currentZoom = mMap.cameraPosition.zoom
            mMap.animateCamera(CameraUpdateFactory.zoomTo(currentZoom - 1f))
        }
    }
}