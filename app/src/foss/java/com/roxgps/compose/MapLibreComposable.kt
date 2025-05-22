package com.roxgps.compose // Sesuaikan package dengan lokasi file ini

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
//import com.google.android.gms.maps.OnMapReadyCallback
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
/**
 * Composable yang menampung (host) MapView MapLibre menggunakan AndroidView.
 * Mengelola lifecycle MapView dan menyediakan callback saat map siap.
 *
 * @param modifier Modifier untuk tata letak Composable.
 * @param onMapReady Callback yang dipanggil saat MapLibreMap siap.
 * @param onMapViewCreated Callback yang dipanggil setelah MapView dibuat,
 * memberikan akses ke instance MapView untuk manajemen lifecycle.
 */
@Composable
fun MapLibreComposable(
    modifier: Modifier = Modifier,
    // Callback saat map siap (MapLibreMap)
    onMapReady: (MapLibreMap) -> Unit = {},
    // Callback saat MapView dibuat (MapView) - Penting untuk manajemen lifecycle
    onMapViewCreated: (MapView) -> Unit = {}
) {
    val context = LocalContext.current // Ambil Context
    val lifecycleOwner = LocalLifecycleOwner.current // Ambil LifecycleOwner

    // Gunakan rememberSaveable jika ingin menyimpan state MapView saat konfigurasi berubah
    // val mapView = rememberSaveable(key = "mapView") { MapView(context) } // Contoh dengan rememberSaveable

    // Gunakan remember untuk membuat instance MapView dan mengingatnya selama Composable ada
    val mapView = remember { MapView(context) } // Membuat instance MapView

    // Kelola lifecycle MapView sesuai dengan lifecycle Composable/Activity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle()) // Panggil onCreate MapView
                Lifecycle.Event.ON_START -> mapView.onStart() // Panggil onStart MapView
                Lifecycle.Event.ON_RESUME -> mapView.onResume() // Panggil onResume MapView
                Lifecycle.Event.ON_PAUSE -> mapView.onPause() // Panggil onPause MapView
                Lifecycle.Event.ON_STOP -> mapView.onStop() // Panggil onStop MapView
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy() // Panggil onDestroy MapView
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer) // Tambahkan observer lifecycle

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer) // Hapus observer saat Composable dibuang
            // Cleanup tambahan jika diperlukan
        }
    }

    // AndroidView menampung View tradisional di dalam Compose UI
    AndroidView(
        factory = {
            // Factory ini dipanggil saat View perlu dibuat
            mapView // Mengembalikan instance MapView yang sudah dibuat
        },
        modifier = modifier.fillMaxSize() // Terapkan modifier ke View (MapView)
    ) { view ->
        // Blok 'update' ini dipanggil saat state Compose berubah dan perlu mengupdate View
        // Di sini, kita bisa memanggil getMapAsync setelah View (MapView) siap
        view.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(mapLibreMap: MapLibreMap) {
                // Panggil callback onMapReady yang diberikan dari luar Composable
                onMapReady(mapLibreMap)
            }
        })

        // Panggil callback onMapViewCreated setelah MapView dibuat
        onMapViewCreated(view)
    }
}

// Optional: Preview Composable MapViewComposable
// @Preview(showBackground = true)
// @Composable
// fun PreviewMapViewComposable() {
//     MapViewComposable() // Preview tanpa callback spesifik
// }
