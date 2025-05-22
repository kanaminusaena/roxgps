package com.roxgps.datastore

//import com.roxgps.datastore.AppSettings // Class utama
//import com.roxgps.datastore.AppSettingsOuterClass.AppSettings
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppSettingsParcelable(
    val isFakingStarted: Boolean = false,
    val currentLatitude: Float = 0.0f,
    val currentLongitude: Float = 0.0f,
    val isSystemHooked: Boolean = false,
    val isRandomPositionEnabled: Boolean = false,
    val accuracyLevelSetting: Float = 0.0f,
    val randomPositionRangeMeters: Int = 50,
    val updateIntervalMs: Long = 1000L,
    val simulatedSpeedMps: Float = 0.0f,
    val simulatedBearingDegrees: Float = 0.0f,
    val simulatedAltitudeMeters: Double = 0.0,
    val simulatedProviderName: String = "",
    val mapType: Int = 0,
    val darkThemeMode: Int = 0,
    val isJoystickControlEnabled: Boolean = false,
    val isUpdateCheckDisabled: Boolean = false
) : Parcelable

// Extension function untuk konversi dari parcelable ke proto menggunakan DSL
fun AppSettingsParcelable.toProto() = appSettings {
    isFakingStarted = this@toProto.isFakingStarted
    currentLatitude = this@toProto.currentLatitude
    currentLongitude = this@toProto.currentLongitude
    isSystemHooked = this@toProto.isSystemHooked
    isRandomPositionEnabled = this@toProto.isRandomPositionEnabled
    accuracyLevelSetting = this@toProto.accuracyLevelSetting
    randomPositionRangeMeters = this@toProto.randomPositionRangeMeters
    updateIntervalMs = this@toProto.updateIntervalMs
    simulatedSpeedMps = this@toProto.simulatedSpeedMps
    simulatedBearingDegrees = this@toProto.simulatedBearingDegrees
    simulatedAltitudeMeters = this@toProto.simulatedAltitudeMeters
    simulatedProviderName = this@toProto.simulatedProviderName
    mapType = this@toProto.mapType
    darkThemeMode = this@toProto.darkThemeMode
    isJoystickControlEnabled = this@toProto.isJoystickControlEnabled
    isUpdateCheckDisabled = this@toProto.isUpdateCheckDisabled
}
fun AppSettings.toParcelable() = AppSettingsParcelable(
    isFakingStarted = this.isFakingStarted,
    currentLatitude = this.currentLatitude,
    currentLongitude = this.currentLongitude,
    isSystemHooked = this.isSystemHooked,
    isRandomPositionEnabled = this.isRandomPositionEnabled,
    accuracyLevelSetting = this.accuracyLevelSetting,
    randomPositionRangeMeters = this.randomPositionRangeMeters,
    updateIntervalMs = this.updateIntervalMs,
    simulatedSpeedMps = this.simulatedSpeedMps,
    simulatedBearingDegrees = this.simulatedBearingDegrees,
    simulatedAltitudeMeters = this.simulatedAltitudeMeters,
    simulatedProviderName = this.simulatedProviderName,
    mapType = this.mapType,
    darkThemeMode = this.darkThemeMode,
    isJoystickControlEnabled = this.isJoystickControlEnabled,
    isUpdateCheckDisabled = this.isUpdateCheckDisabled
)