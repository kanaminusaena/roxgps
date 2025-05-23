package com.roxgps.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.roxgps.data.FakeLocationData
import com.roxgps.repository.SettingsRepository
import com.roxgps.service.LocationBroadcastReceiver
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import com.roxgps.xposed.IXposedHookManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import android.location.LocationListener as AndroidLocationListener

/**
 * Implementasi ILocationHelper menggunakan Android Location API untuk MapLibre.
 *
 * @author loserkidz
 * @since 2025-05-23 13:51:41
 */
@Singleton
class MapLibreLocationHelperImpl @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
    prefManager: PrefManager,
    locationManager: LocationManager,
    random: Random,
    xposedHookManager: IXposedHookManager
) : LocationHelperImpl(
    context,
    settingsRepository,
    prefManager,
    locationManager,
    random,
    xposedHookManager
) {
    companion object {
        private const val TAG = "MapLibreLocationHelper"
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_M = 1f
        private const val BG_UPDATE_INTERVAL_MS = 30000L
        private const val BG_MIN_DISTANCE_M = 5f
        private const val BG_LOCATION_REQUEST_CODE = 12346
    }

    // Rename to avoid conflict with parent's locationListener
    private val androidSystemListener = object : AndroidLocationListener {
        override fun onLocationChanged(location: Location) {
            Relog.v("$TAG: Location update received: ${location.latitude}, ${location.longitude}")
            locationListener?.onLocationResult(location)
        }

        override fun onProviderEnabled(provider: String) {
            Relog.d("$TAG: Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Relog.w("$TAG: Provider disabled: $provider")
            locationListener?.onLocationError("Location provider disabled: $provider")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Relog.d("$TAG: Provider status changed: $provider = $status")
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(listener: LocationListener) {
        Relog.d("$TAG: Requesting location updates")
        this.locationListener = listener

        try {
            // Try GPS provider first
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                MIN_DISTANCE_M,
                androidSystemListener,
                Looper.getMainLooper()
            )

            // Also request network provider as backup
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                UPDATE_INTERVAL_MS,
                MIN_DISTANCE_M,
                androidSystemListener,
                Looper.getMainLooper()
            )

            Relog.i("$TAG: Location updates requested successfully")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to request location updates")
            listener.onLocationError("Failed to start location updates: ${e.message}")
        }
    }

    override fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(androidSystemListener)
            this.locationListener = null
            Relog.i("$TAG: Location updates stopped successfully")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Error stopping location updates")
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(): Location? {
        try {
            // Try GPS provider first
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            // If GPS location is null, try network provider
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (location != null) {
                Relog.v("$TAG: Got last known location: ${location.latitude}, ${location.longitude}")
            } else {
                Relog.d("$TAG: No last known location available")
            }

            return location
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to get last known location")
            return null
        }
    }

    @SuppressLint("MissingPermission")
    override fun getRealLocationUpdates(): Flow<Location> = callbackFlow {
        // Check location services first
        if (!isLocationServiceEnabled()) {
            Relog.e("$TAG: Location services disabled")
            close(IllegalStateException("Location services disabled"))
            return@callbackFlow
        }

        // Check permissions explicitly
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Relog.e("$TAG: Location permissions not granted")
            close(SecurityException("Location permissions not granted"))
            return@callbackFlow
        }

        val flowLocationListener = object : AndroidLocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location).isSuccess
                Relog.v("$TAG: Flow received location update: ${location.latitude}, ${location.longitude}")
            }

            override fun onProviderEnabled(provider: String) {
                Relog.d("$TAG: Provider enabled in flow: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Relog.w("$TAG: Provider disabled in flow: $provider")
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Relog.d("$TAG: Provider status changed in flow: $provider = $status")
            }
        }

        try {
            // Request GPS updates
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    flowLocationListener,
                    Looper.getMainLooper()
                )
                Relog.d("$TAG: Requested GPS location updates for flow")
            } catch (e: IllegalArgumentException) {
                Relog.w("$TAG: GPS provider not available: ${e.message}")
            }

            // Request Network updates as backup
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    flowLocationListener,
                    Looper.getMainLooper()
                )
                Relog.d("$TAG: Requested Network location updates for flow")
            } catch (e: IllegalArgumentException) {
                Relog.w("$TAG: Network provider not available: ${e.message}")
            }

            // If neither provider is available, close the flow
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                Relog.e("$TAG: No location providers available")
                close(IllegalStateException("No location providers available"))
                return@callbackFlow
            }

            awaitClose {
                Relog.d("$TAG: Removing location updates from Flow")
                locationManager.removeUpdates(flowLocationListener)
            }
        } catch (e: SecurityException) {
            Relog.e(e, "$TAG: Security exception while requesting location updates")
            close(e)
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Error requesting location updates")
            close(e)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startRealLocationUpdates() {
        try {
            val intent = Intent(context, LocationBroadcastReceiver::class.java).apply {
                action = LocationBroadcastReceiver.ACTION_PROCESS_LOCATION
            }

            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        android.app.PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                BG_LOCATION_REQUEST_CODE,
                intent,
                flags
            )

            // Request updates from both providers
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                BG_UPDATE_INTERVAL_MS,
                BG_MIN_DISTANCE_M,
                pendingIntent
            )

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                BG_UPDATE_INTERVAL_MS,
                BG_MIN_DISTANCE_M,
                pendingIntent
            )

            Relog.i("$TAG: Background location updates started")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to start background updates")
        }
    }

    override fun stopRealLocationUpdates() {
        try {
            val intent = Intent(context, LocationBroadcastReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                BG_LOCATION_REQUEST_CODE,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            locationManager.removeUpdates(pendingIntent)
            Relog.i("$TAG: Background location updates stopped")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Error stopping background updates")
        }
    }

    override fun getFakeLocationData(
        isRandomPositionEnabled: Boolean,
        accuracy: Float,
        randomRange: Int,
        updateIntervalMs: Long,
        desiredSpeed: Float,
    ): FakeLocationData? {
        val targetLocation = currentFakeLocation.value ?: return null

        var lastRealLocation: Location? = null
        try {
            lastRealLocation = getLastKnownLocation()
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to get last real location for defaults")
        }

        val (fakeLatitude, fakeLongitude) = if (isRandomPositionEnabled) {
            calculateRandomOffset(targetLocation.latitude, targetLocation.longitude, randomRange)
        } else {
            Pair(targetLocation.latitude, targetLocation.longitude)
        }

        return FakeLocationData(
            latitude = fakeLatitude,
            longitude = fakeLongitude,
            accuracy = accuracy.takeIf { it > 0 } ?: lastRealLocation?.accuracy ?: 1.0f,
            speed = desiredSpeed.takeIf { it > 0 }
                ?: targetLocation.speed.takeIf { !it.isNaN() }
                ?: lastRealLocation?.speed?.takeIf { !it.isNaN() }
                ?: 0.0f,
            bearing = targetLocation.bearing.takeIf { !it.isNaN() }
                ?: lastRealLocation?.bearing?.takeIf { !it.isNaN() }
                ?: 0.0f,
            altitude = targetLocation.altitude.takeIf { !it.isNaN() }
                ?: lastRealLocation?.altitude?.takeIf { !it.isNaN() }
                ?: 0.0,
            time = System.currentTimeMillis(),
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            provider = LocationManager.GPS_PROVIDER,
            isStarted = isFakingActive.value,
            isRandomPositionEnabled = isRandomPositionEnabled,
            randomRange = randomRange,
            updateIntervalMs = updateIntervalMs
        )
    }
}