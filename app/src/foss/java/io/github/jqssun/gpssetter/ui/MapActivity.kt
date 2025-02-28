package io.github.jqssun.gpssetter.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction


import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.databinding.ActivityMapBinding
import io.github.jqssun.gpssetter.ui.viewmodel.MainViewModel
import io.github.jqssun.gpssetter.utils.ext.getAddress
import io.github.jqssun.gpssetter.utils.ext.showToast
import kotlinx.coroutines.launch

// import com.google.android.gms.maps.CameraUpdateFactory
// import com.google.android.gms.maps.MapLibreMap
// import com.google.android.gms.maps.OnMapReadyCallback
// import com.google.android.gms.maps.SupportMapFragment
// import com.google.android.gms.maps.model.BitmapDescriptorFactory
// import com.google.android.gms.maps.model.LatLng
// import com.google.android.gms.maps.model.Marker
// import com.google.android.gms.maps.model.MarkerOptions

import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.SupportMapFragment
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions

typealias CustomLatLng = LatLng

class MapActivity: BaseMapActivity(), OnMapReadyCallback, MapLibreMap.OnMapClickListener {

    private lateinit var mMap: MapLibreMap
    private var mLatLng: LatLng? = null
    private var mMarker: Marker? = null

    override fun getActivityInstance(): BaseMapActivity {
        return this@MapActivity
    }

    override fun hasMarker(): Boolean {
        // TODO: if (!mMarker?.isVisible!!){
        if (mMarker != null) {
            return true
        }
        return false
    }

    private fun updateMarker(it: LatLng) {
        // TODO: mMarker?.isVisible = true
        if (mMarker == null) {
            mMarker = mMap.addMarker(
                MarkerOptions().position(it)
            )
        } else {
            mMarker?.position = it!!
        }
    }

    private fun removeMarker() {
        mMarker?.remove() // mMarker?.isVisible = false
        mMarker = null
    }

    override fun initializeMap() {
        MapLibre.getInstance(this)
        // val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
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
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng!!, 12.0f.toDouble()))
                mMarker?.apply {
                    position = latLng
                    // TODO:
                    // isVisible = true
                    // showInfoWindow()
                }
            }
        }
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        mMap = mapLibreMap
        with(mMap){
            setStyle("https://demotiles.maplibre.org/style.json")
            // TODO: mapType = viewModel.mapType

            val zoom = 12.0f
            lat = viewModel.getLat
            lon  = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng.let {
                updateMarker(it!!)
                // TODO:
                // .draggable(false).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                // .visible(false)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom.toDouble()))
            }
            setPadding(0,80,0,170)
            addOnMapClickListener(this@MapActivity)
            if (viewModel.isStarted){
                mMarker?.let {
                    // TODO:
                    // it.isVisible = true
                    // it.showInfoWindow()
                }
            }
        }
    }

    override fun onMapClick(latLng: LatLng): Boolean {
        mLatLng = latLng
        mMarker?.let { marker ->
            mLatLng.let {
                // marker.isVisible = true
                updateMarker(it!!)
                mMap.animateCamera(CameraUpdateFactory.newLatLng(it))
                lat = it.latitude
                lon = it.longitude
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    override fun setupButton(){
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
            viewModel.update(true, lat, lon)
            mLatLng.let {
                updateMarker(it!!)
            }
            binding.startButton.visibility = View.GONE
            binding.stopButton.visibility = View.VISIBLE
            lifecycleScope.launch {
                mLatLng?.getAddress(getActivityInstance())?.let { address ->
                    address.collect{ value ->
                        showStartNotification(value)
                    }
                }
            }
            showToast(getString(R.string.location_set))
        }
        binding.stopButton.setOnClickListener {
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
            }
            removeMarker()
            binding.stopButton.visibility = View.GONE
            binding.startButton.visibility = View.VISIBLE
            cancelNotification()
            showToast(getString(R.string.location_unset))
        }
    }
}
