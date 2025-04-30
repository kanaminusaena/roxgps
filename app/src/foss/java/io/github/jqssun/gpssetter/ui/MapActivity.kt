package io.github.jqssun.gpssetter.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.utils.FileLogger
import io.github.jqssun.gpssetter.utils.ext.getAddress
import io.github.jqssun.gpssetter.utils.ext.showToast
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.SupportMapFragment
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.plugins.annotation.OnSymbolDragListener

typealias CustomLatLng = LatLng

class MapActivity : BaseMapActivity(), OnMapReadyCallback, MapLibreMap.OnMapClickListener {

    private lateinit var mMap: MapLibreMap
    private var mLatLng: LatLng? = null
    private var mMarker: Symbol? = null
    private var symbolManager: SymbolManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeMap()
        setupButtons()
    }

    override fun hasMarker(): Boolean {
        FileLogger.log("hasMarker() dipanggil. Marker: ${mMarker != null}", "MapActivity", "D")
        return mMarker != null
    }

    private fun updateMarker(latLng: LatLng) {
        FileLogger.log("updateMarker() dipanggil dengan lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "D")
        if (mMarker == null) {
            FileLogger.log("Buat marker baru di lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "I")
            mMarker = symbolManager?.create(
                SymbolOptions()
                    .withLatLng(latLng)
                    .withIconImage("marker-15")
                    .withDraggable(true)
            )
        } else {
            FileLogger.log("Update posisi marker ke lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "I")
            mMarker?.latLng = latLng
            symbolManager?.update(mMarker!!)
        }
        mMarker?.let { marker ->
            mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.latLng))
            FileLogger.log("Map digeser ke marker lat=${marker.latLng.latitude}, long=${marker.latLng.longitude}", "MapActivity", "I")
        }
    }

    private fun removeMarker() {
        FileLogger.log("removeMarker() dipanggil", "MapActivity", "D")
        if (mMarker != null) {
            FileLogger.log("Marker dihapus (lat=${mMarker?.latLng?.latitude}, long=${mMarker?.latLng?.longitude})", "MapActivity", "I")
            symbolManager?.delete(mMarker)
            mMarker = null
        } else {
            FileLogger.log("Tidak ada marker yang dihapus", "MapActivity", "I")
        }
    }

    override fun initializeMap() {
        FileLogger.log("initializeMap() dipanggil", "MapActivity", "D")
        try {
            val key = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.maplibre.AccessToken")
            MapLibre.getInstance(this, key, WellKnownTileServer.Mapbox)
            val mapFragment = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map, mapFragment)
                .commit()
            mapFragment.getMapAsync(this)
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
            mLatLng?.let { latLng ->
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(latLng)
                        .zoom(18.0)
                        .bearing(0.0)
                        .tilt(0.0)
                        .build()
                ))
                mMarker?.apply {
                    latLng = latLng
                    symbolManager?.update(this)
                    FileLogger.log("Marker diupdate ke: lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "I")
                }
            }
        } else {
            FileLogger.log("Tidak pindah lokasi. lat=$lat, long=$lon", "MapActivity", "I")
        }
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        FileLogger.log("onMapReady() dipanggil", "MapActivity", "D")
        mMap = mapLibreMap

        var typeUrl = "https://demotiles.maplibre.org/style.json"
        when (viewModel.mapType) {
            2 -> typeUrl = "mapbox://styles/mapbox/satellite-streets-v12"
            3 -> typeUrl = "mapbox://styles/mapbox/outdoors-v12"
            4 -> typeUrl = "mapbox://styles/mapbox/navigation-day-v1"
            else -> typeUrl = "mapbox://styles/mapbox/streets-v12"
        }

        mMap.setStyle(typeUrl) { style ->
            // SymbolManager setup
            val mapView = findViewById<View>(R.id.map)
            symbolManager = SymbolManager(mapView, mMap, style)
            symbolManager?.iconAllowOverlap = true
            symbolManager?.textAllowOverlap = true

            // Marker drag listener
            symbolManager?.addDragListener(object : OnSymbolDragListener {
                override fun onAnnotationDragStarted(annotation: Symbol) {
                    FileLogger.log("onMarkerDragStart: Marker di lat=${annotation.latLng.latitude}, long=${annotation.latLng.longitude}", "MapActivity", "I")
                }
                override fun onAnnotationDrag(annotation: Symbol) {
                    // Optional: update UI selama drag
                }
                override fun onAnnotationDragFinished(annotation: Symbol) {
                    FileLogger.log("onMarkerDragEnd: Marker selesai di lat=${annotation.latLng.latitude}, long=${annotation.latLng.longitude}", "MapActivity", "I")
                    mLatLng = annotation.latLng
                    lat = annotation.latLng.latitude
                    lon = annotation.latLng.longitude
                    updateMarker(annotation.latLng)
                }
            })

            // Location component
            if (ActivityCompat.checkSelfPermission(this@MapActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                FileLogger.log("Permission granted", "MapActivity", "I")
                val locationComponent = mMap.locationComponent
                locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this@MapActivity, style)
                        .useDefaultLocationEngine(true)
                        .build()
                )
                locationComponent.isLocationComponentEnabled = true
                locationComponent.cameraMode = CameraMode.TRACKING
                locationComponent.renderMode = RenderMode.COMPASS
            } else {
                FileLogger.log("Permission not granted, request permission", "MapActivity", "I")
                ActivityCompat.requestPermissions(this@MapActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 99)
            }

            val zoom = 18.0f
            lat = viewModel.getLat
            lon = viewModel.getLng
            FileLogger.log("onMapReady: koordinat awal lat=$lat, long=$lon", "MapActivity", "I")
            mLatLng = LatLng(lat, lon)
            mLatLng?.let {
                updateMarker(it)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom.toDouble()))
                FileLogger.log("Marker awal di lat=${it.latitude}, long=${it.longitude}", "MapActivity", "I")
            }

            mMap.addOnMapClickListener(this@MapActivity)
            if (viewModel.isStarted) {
                FileLogger.log("ViewModel sudah mulai", "MapActivity", "I")
            } else {
                FileLogger.log("ViewModel belum mulai", "MapActivity", "I")
            }
        }
    }

    override fun onMapClick(latLng: LatLng): Boolean {
        FileLogger.log("onMapClick() dipanggil: lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "D")
        mLatLng = latLng
        updateMarker(latLng)
        lat = latLng.latitude
        lon = latLng.longitude
        FileLogger.log("Marker diupdate via klik: lat=${latLng.latitude}, long=${latLng.longitude}", "MapActivity", "I")
        return true
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
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 18.0))
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
            mLatLng?.let { updateMarker(it) }
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
            mLatLng?.let {
                viewModel.update(false, it.latitude, it.longitude)
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