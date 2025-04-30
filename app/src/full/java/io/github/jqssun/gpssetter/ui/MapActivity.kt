package io.github.jqssun.gpssetter.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.View
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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

    private lateinit var plusButton: ImageButton
    private lateinit var minusButton: ImageButton

    // Receiver to handle stop action from notification/service
    private val serviceStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Mimic stopButton click UI logic
            binding.stopButton.visibility = View.GONE
            binding.startButton.visibility = View.VISIBLE
            removeMarker()
            showToast(getString(R.string.location_unset))
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(serviceStoppedReceiver, IntentFilter("io.github.jqssun.gpssetter.ACTION_SERVICE_STOPPED"))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(serviceStoppedReceiver)
    }

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
     * Hides the marker and zooms out the map to a wider view.
     */
    private fun removeMarker() {
        mMarker?.isVisible = false
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
            mLatLng = LatLng(lat, lon)
            mLatLng?.let { updateMarker(it) }
        }

        if (viewModel.isStarted) {
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
        }

        binding.startButton.setOnClickListener {
            // Immediately update UI for responsiveness
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE

            viewModel.update(true, lat, lon)
            mLatLng?.let {
                updateMarker(it)
            }
            // Launching address lookup and notification in the background
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
            // Immediately update UI for responsiveness
            binding.stopButton.visibility = View.GONE
            binding.startButton.visibility = View.VISIBLE

            mLatLng?.let {
                viewModel.update(false, it.latitude, it.longitude)
            }
            removeMarker()
            cancelNotification()
            showToast(getString(R.string.location_unset))
        }
    }

    /**
     * Setup Plus and Minus Zoom Buttons (must be present in your layout)
     */
    private fun setupZoomButtons() {
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