package com.roxgps.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Implementasi ILocationHelper menggunakan Google Play Services.
 *
 * @author loserkidz
 * @since 2025-05-23 13:49:19
 */
@Singleton
class GoogleLocationHelperImpl @Inject constructor(
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
        private const val TAG = "GoogleLocationHelper"
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val FASTEST_INTERVAL_MS = 2000L
        private const val BG_UPDATE_INTERVAL_MS = 30000L
        private const val BG_FASTEST_INTERVAL_MS = 15000L
        private const val BG_LOCATION_REQUEST_CODE = 12345
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                Timber.v("$TAG: Location update received: ${location.latitude}, ${location.longitude}")
                locationListener?.onLocationResult(location)
            } ?: run {
                Relog.w("$TAG: Location result received but location is null")
                locationListener?.onLocationError("Location update is null")
            }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                Relog.w("$TAG: Location is not available")
                locationListener?.onLocationError("Location services currently unavailable")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(listener: LocationListener) {
        Relog.d("$TAG: Requesting location updates")
        locationListener = listener

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
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
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationListener = null
            Relog.i("$TAG: Location updates stopped successfully")
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Error stopping location updates")
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(): Location? {
        return try {
            Tasks.await(fusedLocationClient.lastLocation)
        } catch (e: Exception) {
            Relog.e(e, "$TAG: Failed to get last known location")
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun getRealLocationUpdates(): Flow<Location> = callbackFlow {
        // Check permissions explicitly first
        if (!isLocationServiceEnabled()) {
            Relog.e("$TAG: Location services disabled")
            close(IllegalStateException("Location services disabled"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location).isSuccess
                }
            }
        }

        try {
            // Explicitly check permissions
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

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )

            awaitClose {
                Relog.d("$TAG: Removing location updates from Flow")
                fusedLocationClient.removeLocationUpdates(callback)
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
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            BG_UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(BG_FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

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

            fusedLocationClient.requestLocationUpdates(locationRequest, pendingIntent)
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

            fusedLocationClient.removeLocationUpdates(pendingIntent)
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
            provider = "fused",
            isStarted = isFakingActive.value,
            isRandomPositionEnabled = isRandomPositionEnabled,
            randomRange = randomRange,
            updateIntervalMs = updateIntervalMs
        )
    }
}