// File: com/roxgps/repository/SettingsRepositoryImpl.kt
package com.roxgps.repository

/*import androidx.datastore.core.DataStore
import androidx.datastore.dataStore*/
import androidx.datastore.core.DataStore
import com.roxgps.datastore.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================
// SettingsRepositoryImpl (Menggunakan Proto DataStore)
// =====================================================================

/**
 * Implementasi dari SettingsRepository menggunakan Proto DataStore
 * @author loserkidz
 * @since 2025-05-23 09:45:56
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val appSettingsDataStore: DataStore<AppSettings>,
    private val repositoryScope: CoroutineScope
) : SettingsRepository {

    private val dataStore: DataStore<AppSettings> = appSettingsDataStore

    // Akses ke semua settings
    // Akses ke semua settings
    override val appSettingsFlow: Flow<AppSettings> = appSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading AppSettings from DataStore. Emitting default instance.")
                emit(AppSettings.getDefaultInstance())
            } else {
                throw exception
            }
        }

    // Token Flow implementations dengan error handling
    override val hookedAppTokenFlow: Flow<String> = this.dataStore.data
        .map<AppSettings, String> { settings -> settings.hookedAppToken }
        .catch { _: Throwable -> emit("") }

    override val tokenLastUpdatedFlow: Flow<Long> = this.dataStore.data
        .map<AppSettings, Long> { settings -> settings.tokenLastUpdated }
        .catch { _: Throwable -> emit(0L) }

    override val hookedPackageNameFlow: Flow<String> = this.dataStore.data
        .map<AppSettings, String> { settings -> settings.hookedPackageName }
        .catch { _: Throwable -> emit("") }

    override val isTokenValidFlow: Flow<Boolean> = this.dataStore.data
        .map<AppSettings, Boolean> { settings -> settings.isTokenValid }
        .catch { _: Throwable -> emit(false) }

    // Token StateFlow implementations
    private val _hookedAppTokenState: MutableStateFlow<String> = MutableStateFlow("")
    override val hookedAppTokenState: StateFlow<String> = _hookedAppTokenState

    private val _tokenLastUpdatedState: MutableStateFlow<Long> = MutableStateFlow(0L)
    override val tokenLastUpdatedState: StateFlow<Long> = _tokenLastUpdatedState

    private val _hookedPackageNameState: MutableStateFlow<String> = MutableStateFlow("")
    override val hookedPackageNameState: StateFlow<String> = _hookedPackageNameState

    private val _isTokenValidState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isTokenValidState: StateFlow<Boolean> = _isTokenValidState

    init {
        repositoryScope.launch {
            appSettingsDataStore.data
                .catch { exception: Throwable ->
                    Timber.e(exception, "Error collecting settings")
                    emit(AppSettings.getDefaultInstance())
                }
                .collect { settings: AppSettings ->
                    _hookedAppTokenState.value = settings.hookedAppToken
                    _tokenLastUpdatedState.value = settings.tokenLastUpdated
                    _hookedPackageNameState.value = settings.hookedPackageName
                    _isTokenValidState.value = settings.isTokenValid
                }
        }
    }

    override suspend fun updateHookedAppToken(token: String, packageName: String) {
        appSettingsDataStore.updateData { currentSettings: AppSettings ->
            currentSettings.toBuilder()
                .setHookedAppToken(token)
                .setTokenLastUpdated(System.currentTimeMillis())
                .setHookedPackageName(packageName)
                .setIsTokenValid(token.isNotEmpty())
                .build()
        }
    }

    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings): AppSettings {
        return appSettingsDataStore.updateData(transform)
    }

    override suspend fun updateIsRandomPositionEnabled(isEnabled: Boolean) {
        appSettingsDataStore.updateData { currentSettings: AppSettings ->
            currentSettings.toBuilder()
                .setIsRandomPositionEnabled(isEnabled)
                .build()
        }
    }

    override suspend fun updateAccuracyLevel(accuracy: Float) {
        appSettingsDataStore.updateData { currentSettings: AppSettings ->
            currentSettings.toBuilder()
                .setAccuracyLevelSetting(accuracy)
                .build()
        }
    }

    override suspend fun updateRandomRange(range: Int) {
        appSettingsDataStore.updateData { currentSettings: AppSettings ->
            currentSettings.toBuilder()
                .setRandomPositionRangeMeters(range)
                .build()
        }
    }

    override suspend fun updateUpdateIntervalMs(interval: Long) {
        appSettingsDataStore.updateData { currentSettings: AppSettings ->
            currentSettings.toBuilder()
                .setUpdateIntervalMs(interval)
                .build()
        }
    }

    override suspend fun updateDesiredSpeed(speed: Float) {
        appSettingsDataStore.updateData { currentSettings: AppSettings ->
            currentSettings.toBuilder()
                .setSimulatedSpeedMps(speed)
                .build()
        }
    }

    // Implementasi untuk Settings Flows yang belum ada
    override val isRandomPositionEnabled: Flow<Boolean> = appSettingsDataStore.data
        .map { settings: AppSettings -> settings.isRandomPositionEnabled }
        .catch { exception: Throwable ->
            Timber.e(exception, "Error reading random position enabled")
            emit(false)
        }

    override val accuracyLevel: Flow<Float> = appSettingsDataStore.data
        .map { settings: AppSettings -> settings.accuracyLevelSetting }
        .catch { exception: Throwable ->
            Timber.e(exception, "Error reading accuracy level")
            emit(10.0f)
        }

    override val randomRange: Flow<Int> = appSettingsDataStore.data
        .map { settings: AppSettings -> settings.randomPositionRangeMeters }
        .catch { exception: Throwable ->
            Timber.e(exception, "Error reading random range")
            emit(100)
        }

    override val updateIntervalMs: Flow<Long> = appSettingsDataStore.data
        .map { settings: AppSettings -> settings.updateIntervalMs }
        .catch { exception: Throwable ->
            Timber.e(exception, "Error reading update interval")
            emit(1000L)
        }

    override val desiredSpeed: Flow<Float> = appSettingsDataStore.data
        .map { settings: AppSettings -> settings.simulatedSpeedMps }
        .catch { exception: Throwable ->
            Timber.e(exception, "Error reading desired speed")
            emit(0.0f)
        }

    // StateFlow implementations
    override val isRandomPositionEnabledState: StateFlow<Boolean> = isRandomPositionEnabled
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    override val accuracyLevelState: StateFlow<Float> = accuracyLevel
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = 10.0f
        )

    override val randomRangeState: StateFlow<Int> = randomRange
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = 100
        )

    override val updateIntervalMsState: StateFlow<Long> = updateIntervalMs
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = 1000L
        )

    override val desiredSpeedState: StateFlow<Float> = desiredSpeed
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = 0.0f
        )
}