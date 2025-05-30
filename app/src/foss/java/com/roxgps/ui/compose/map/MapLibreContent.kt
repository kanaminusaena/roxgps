package com.roxgps.ui.compose.map

import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

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
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                getMapAsync { map ->
                    map.setStyle(
                        when(mapType) {
                            1 -> Style.MAPBOX_STREETS
                            2 -> Style.SATELLITE
                            3 -> Style.TERRAIN
                            else -> Style.MAPBOX_STREETS
                        }
                    ) { style ->
                        symbolManager = SymbolManager(this, map, style)
                        onMapReady()
                    }

                    map.addOnMapClickListener { point ->
                        onMapClick(point)
                        true
                    }
                }
            }
        },
        modifier = modifier,
        update = { view ->
            mapView = view
            currentLocation?.let { location ->
                if (isFollowingUser) {
                    view.getMapAsync { map ->
                        val position = LatLng(location.latitude, location.longitude)

                        // Update marker
                        symbolManager?.deleteAll()
                        symbolManager?.create(
                            SymbolOptions()
                                .withLatLng(position)
                                .withIconImage("marker")
                                .withIconSize(1.5f)
                        )

                        // Animate camera
                        map.animateCamera(
                            CameraUpdateFactory.newLatLng(position)
                        )
                    }
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            symbolManager?.onDestroy()
            mapView?.onDestroy()
        }
    }
}