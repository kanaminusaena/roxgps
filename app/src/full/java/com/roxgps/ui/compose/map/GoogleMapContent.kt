package com.roxgps.ui.compose.map

import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@Composable
actual fun MapContent(
    mapType: Int,
    currentLocation: Location?,
    isFollowingUser: Boolean,
    onMapReady: () -> Unit,
    onMarkerClick: (LatLng) -> Unit,
    onMapClick: (LatLng) -> Unit,
    modifier: Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(0.0, 0.0),
            15f
        )
    }

    var map by remember { mutableStateOf<GoogleMap?>(null) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = when(mapType) {
                1 -> MapType.NORMAL
                2 -> MapType.SATELLITE
                3 -> MapType.TERRAIN
                else -> MapType.NORMAL
            },
            isMyLocationEnabled = true,
            isBuildingEnabled = true
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        ),
        onMapLoaded = {
            map = it
            onMapReady()
        },
        onMapClick = { latLng ->
            onMapClick(latLng)
        }
    ) {
        currentLocation?.let { location ->
            Marker(
                state = MarkerState(
                    position = LatLng(
                        location.latitude,
                        location.longitude
                    )
                ),
                title = "Current Location",
                onClick = {
                    onMarkerClick(it.position)
                    true
                }
            )
        }
    }

    LaunchedEffect(currentLocation) {
        if (isFollowingUser) {
            currentLocation?.let { location ->
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLng(
                        LatLng(location.latitude, location.longitude)
                    )
                )
            }
        }
    }
}