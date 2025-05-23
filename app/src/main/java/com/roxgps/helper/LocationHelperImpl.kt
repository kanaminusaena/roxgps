package com.roxgps.helper

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.roxgps.data.FakeLocationData
import com.roxgps.repository.SettingsRepository
import com.roxgps.utils.PrefManager
import com.roxgps.xposed.IXposedHookManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.random.Random

/**
 * Abstract base class untuk implementasi ILocationHelper.
 * Menyediakan fungsionalitas dasar yang bisa digunakan oleh implementasi
 * spesifik flavor (GoogleLocationHelperImpl dan MapLibreLocationHelperImpl).
 *
 * @author loserkidz
 * @since 2025-05-23 13:42:45
 */
abstract class LocationHelperImpl(
    @ApplicationContext protected val context: Context,
    protected val settingsRepository: SettingsRepository,
    protected val prefManager: PrefManager,
    protected val locationManager: LocationManager,
    protected val random: Random,
    protected val xposedHookManager: IXposedHookManager,
    fusedLocationProviderClient: FusedLocationProviderClient, // <-- Tambahkan ini jika dibutuhkan GoogleLocationHelperImpl
) : ILocationHelper {

    companion object {
        private const val TAG = "LocationHelperImpl"
    }

    // === Protected Properties ===
    protected var locationListener: LocationListener? = null
    protected var realLocationJob: Job? = null
    protected var fakingUpdateJob: Job? = null

    // Coroutine scope untuk operasi asynchronous
    protected val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // === StateFlow Implementation ===
    private val _isFakingActive = MutableStateFlow(false)
    override val isFakingActive: StateFlow<Boolean> = _isFakingActive.asStateFlow()

    private val _currentFakeLocation = MutableStateFlow<Location?>(null)
    override val currentFakeLocation: StateFlow<Location?> = _currentFakeLocation.asStateFlow()

    // === Basic Location Service Check ===
    override fun isLocationServiceEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    // === Basic Faking Implementation ===
    override fun startFaking(targetLocation: Location) {
        Timber.d("$TAG: Starting faking with target location: ${targetLocation.latitude}, ${targetLocation.longitude}")
        _currentFakeLocation.value = targetLocation
        _isFakingActive.value = true

        // Stop real updates when faking
        stopRealLocationUpdates()

        // Enable Xposed hook
        xposedHookManager.enableFakingMechanism(true)
    }

    override fun stopFaking() {
        Timber.d("$TAG: Stopping faking")
        _currentFakeLocation.value = null
        _isFakingActive.value = false

        // Cancel any active faking job
        fakingUpdateJob?.cancel()
        fakingUpdateJob = null

        // Disable Xposed hook
        xposedHookManager.enableFakingMechanism(false)

        // Resume real updates
        startRealLocationUpdates()
    }

    // === Abstract Methods That Must Be Implemented By Flavor-Specific Classes ===
    abstract override fun requestLocationUpdates(listener: LocationListener)
    abstract override fun stopLocationUpdates()
    abstract override fun getLastKnownLocation(): Location?
    abstract override fun getRealLocationUpdates(): Flow<Location>
    abstract override fun startRealLocationUpdates()
    abstract override fun stopRealLocationUpdates()
    abstract override fun getFakeLocationData(
        isRandomPositionEnabled: Boolean,
        accuracy: Float,
        randomRange: Int,
        updateIntervalMs: Long,
        desiredSpeed: Float
    ): FakeLocationData?

    // === Protected Helper Methods ===
    /**
     * Calculates random offset for fake location
     */
    protected fun calculateRandomOffset(
        baseLatitude: Double,
        baseLongitude: Double,
        randomRange: Int
    ): Pair<Double, Double> {
        val randomOffsetMeters = random.nextDouble(0.0, randomRange.toDouble())
        val randomDirection = random.nextDouble() * 2 * Math.PI

        val earthCircumferenceInMeters = 40075000.0

        val latOffset = randomOffsetMeters * kotlin.math.cos(randomDirection) / (earthCircumferenceInMeters / 360.0)
        val lonOffset = randomOffsetMeters * kotlin.math.sin(randomDirection) / (earthCircumferenceInMeters / 360.0 * kotlin.math.cos(
            Math.toRadians(baseLatitude)
        ))

        return Pair(baseLatitude + latOffset, baseLongitude + lonOffset)
    }
}