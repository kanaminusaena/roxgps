// File: com/roxgps/repository/SettingsRepositoryImpl.kt
package com.roxgps.repository

import androidx.datastore.core.DataStore
import com.roxgps.datastore.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================
// SettingsRepositoryImpl (Menggunakan Proto DataStore)
// =====================================================================

@Singleton // Ini adalah Singleton Repository
class SettingsRepositoryImpl @Inject constructor(
    // --- PERBAIKAN: INJEKSI DataStore<AppSettings> secara langsung ---
    appSettingsDataStore: DataStore<AppSettings>,
    // --- PERBAIKAN: INJEKSI CoroutineScope yang sudah disediakan di Hilt ---
    repositoryScope: CoroutineScope // Injeksi scope dari DataStoreModule.kt
) : SettingsRepository { // Implementasikan Interface SettingsRepository Anda di sini

    // Sekarang, 'dataStore' adalah properti yang diinjeksi, bukan diambil dari Context
    private val dataStore: DataStore<AppSettings> = appSettingsDataStore

    // =====================================================================
    // Membaca Setting (Mengembalikan Flow)
    // =====================================================================

    override val appSettingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading AppSettings from DataStore. Emitting default instance.")
                emit(AppSettings.getDefaultInstance())
            } else {
                throw exception
            }
        }
    // Opsional: .distinctUntilChanged() jika Anda ingin menghindari pemancaran ulang objek AppSettings yang sama persis
    // .distinctUntilChanged()


    // =====================================================================
    // Mengupdate Setting (Menggunakan updateData)
    // =====================================================================

    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings): AppSettings {
        return dataStore.updateData(transform)
    }

    override suspend fun updateIsRandomPositionEnabled(isEnabled: Boolean) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder().setIsRandomPositionEnabled(isEnabled).build()
        }
    }

    override suspend fun updateAccuracyLevel(accuracy: Float) {
        dataStore.updateData { currentSettings ->
            // --- PERBAIKAN: setAccuracyLevel seharusnya setAccuracyLevelSetting ---
            currentSettings.toBuilder().setAccuracyLevelSetting(accuracy).build()
            // Catatan: Jika accuracyLevelSetting di proto adalah string, pastikan konversinya benar (Float ke String)
            // Atau ubah tipe di proto menjadi float jika memang seharusnya float.
        }
    }

    // Implementasi untuk 'val isRandomPositionEnabled: Flow<Boolean>'
    override val isRandomPositionEnabled: Flow<Boolean> = dataStore.data
        .map { settings ->
            settings.isRandomPositionEnabled
        }
        .catch { exception ->
            Timber.e(exception, "Error mapping isRandomPositionEnabled. Emitting default.")
            emit(AppSettings.getDefaultInstance().isRandomPositionEnabled)
        }

    // Implementasi untuk 'val accuracyLevel: Flow<Float>'
    override val accuracyLevel: Flow<Float> = dataStore.data
        .map { settings ->
            // --- PERBAIKAN: getter harusnya getAccuracyLevelSetting ---
            settings.accuracyLevelSetting
            // Pastikan konversi dari String ke Float aman.
            // Atau ubah tipe di proto menjadi float jika memang seharusnya float.
        }
        .catch { exception ->
            Timber.e(exception, "Error mapping accuracyLevel. Emitting default.")
            emit(AppSettings.getDefaultInstance().accuracyLevelSetting)
        }

    // Implementasi untuk 'val randomRange: Flow<Int>'
    override val randomRange: Flow<Int> = dataStore.data
        .map { settings ->
            // --- PERBAIKAN: getter harusnya getRandomPositionRangeMeters ---
            settings.randomPositionRangeMeters
        }
        .catch { exception ->
            Timber.e(exception, "Error mapping randomRange. Emitting default.")
            emit(AppSettings.getDefaultInstance().randomPositionRangeMeters)
        }

    // Implementasi untuk 'val updateIntervalMs: Flow<Long>'
    override val updateIntervalMs: Flow<Long> = dataStore.data
        .map { settings -> settings.updateIntervalMs }
        .catch { exception ->
            Timber.e(exception, "Error mapping updateIntervalMs. Emitting default.")
            emit(AppSettings.getDefaultInstance().updateIntervalMs)
        }

    // Implementasi untuk 'val desiredSpeed: Flow<Float>'
    override val desiredSpeed: Flow<Float> = dataStore.data
        .map { settings ->
            // --- PERBAIKAN: getter harusnya getSimulatedSpeedMps ---
            settings.simulatedSpeedMps
        }
        .catch { exception ->
            Timber.e(exception, "Error mapping desiredSpeed. Emitting default.")
            emit(AppSettings.getDefaultInstance().simulatedSpeedMps)
        }

    // Implementasi untuk 'suspend fun updateDesiredSpeed(speed: Float)'
    override suspend fun updateDesiredSpeed(speed: Float) {
        dataStore.updateData { currentSettings ->
            // --- PERBAIKAN: setDesiredSpeed seharusnya setSimulatedSpeedMps ---
            currentSettings.toBuilder().setSimulatedSpeedMps(speed).build()
        }
    }

    override suspend fun updateRandomRange(range: Int) {
        dataStore.updateData { currentSettings ->
            // --- PERBAIKAN: setRandomRange seharusnya setRandomPositionRangeMeters ---
            currentSettings.toBuilder().setRandomPositionRangeMeters(range).build()
        }
    }

    override suspend fun updateUpdateIntervalMs(interval: Long) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder().setUpdateIntervalMs(interval).build()
        }
    }

    override val isRandomPositionEnabledState: StateFlow<Boolean> = isRandomPositionEnabled
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.getDefaultInstance().isRandomPositionEnabled
        )

    override val accuracyLevelState: StateFlow<Float> = accuracyLevel
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.getDefaultInstance().accuracyLevelSetting // Perbaikan konversi
        )

    override val randomRangeState: StateFlow<Int> = randomRange
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.getDefaultInstance().randomPositionRangeMeters
        )

    override val updateIntervalMsState: StateFlow<Long> = updateIntervalMs
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.getDefaultInstance().updateIntervalMs
        )

    override val desiredSpeedState: StateFlow<Float> = desiredSpeed
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.getDefaultInstance().simulatedSpeedMps
        )
}