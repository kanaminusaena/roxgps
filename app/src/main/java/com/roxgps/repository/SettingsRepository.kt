package com.roxgps.repository

import com.roxgps.datastore.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository untuk mengelola settings aplikasi
 * @author loserkidz
 * @since 2025-05-23 09:38:29
 */
interface SettingsRepository {
    // Akses ke semua settings
    val appSettingsFlow: Flow<AppSettings>

    // Faking Settings Flows
    val isRandomPositionEnabled: Flow<Boolean>
    val accuracyLevel: Flow<Float>
    val randomRange: Flow<Int>
    val updateIntervalMs: Flow<Long>
    val desiredSpeed: Flow<Float>

    // Faking Settings StateFlows
    val isRandomPositionEnabledState: StateFlow<Boolean>
    val accuracyLevelState: StateFlow<Float>
    val randomRangeState: StateFlow<Int>
    val updateIntervalMsState: StateFlow<Long>
    val desiredSpeedState: StateFlow<Float>

    // Token Management Flows
    val hookedAppTokenFlow: Flow<String>
    val tokenLastUpdatedFlow: Flow<Long>
    val hookedPackageNameFlow: Flow<String>
    val isTokenValidFlow: Flow<Boolean>

    // Token Management StateFlows
    val hookedAppTokenState: StateFlow<String>
    val tokenLastUpdatedState: StateFlow<Long>
    val hookedPackageNameState: StateFlow<String>
    val isTokenValidState: StateFlow<Boolean>

    // Update Methods
    /**
     * Update settings menggunakan transform function
     * @param transform Function untuk memodifikasi settings
     * @return AppSettings yang telah diupdate
     */
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings): AppSettings

    /**
     * Update status random position
     * @param isEnabled Status yang akan diset
     */
    suspend fun updateIsRandomPositionEnabled(isEnabled: Boolean)

    /**
     * Update level akurasi
     * @param accuracy Nilai akurasi dalam float
     */
    suspend fun updateAccuracyLevel(accuracy: Float)

    /**
     * Update range random position
     * @param range Range dalam meters
     */
    suspend fun updateRandomRange(range: Int)

    /**
     * Update interval update lokasi
     * @param interval Interval dalam milliseconds
     */
    suspend fun updateUpdateIntervalMs(interval: Long)

    /**
     * Update kecepatan simulasi
     * @param speed Kecepatan dalam m/s
     */
    suspend fun updateDesiredSpeed(speed: Float)

    /**
     * Get token info dari aplikasi yang di-hook
     * @return Triple<token, packageName, lastUpdated>
     */
    fun getTokenInfo(): Triple<String, String, Long> = Triple(
        first = hookedAppTokenState.value,
        second = hookedPackageNameState.value,
        third = tokenLastUpdatedState.value
    )

    /**
     * Update token dari aplikasi yang di-hook
     * @param token Token yang diterima dari hook
     * @param packageName Package name aplikasi yang di-hook
     */
    suspend fun updateHookedAppToken(token: String, packageName: String)
}