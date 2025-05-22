package com.roxgps.utils

// Removed androidx.core.content.edit as it's for SharedPreferences

// DataStore imports
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roxgps.BuildConfig
import com.roxgps.module.util.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Define the DataStore instance as an extension property on Context
// This is the standard and recommended way to create a DataStore instance
// that can be easily accessed and injected using Hilt.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    // Use BuildConfig.APPLICATION_ID as prefix for the name to make it unique per app
    name = "${BuildConfig.APPLICATION_ID}_prefs"
)

/**
 * Manages application preferences using Jetpack DataStore.
 * Exposes preferences as StateFlows for reactive observation.
 */
@Singleton // Scope Singleton as this is a central preference manager
class PrefManager @Inject constructor(
    @ApplicationContext private val context: Context,
    // Use applicationScope for launching long-running tasks like collecting from DataStore in init
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    // DataStore instance accessed via the extension property defined above
    private val dataStore = context.dataStore

    // Preference Keys defined in a companion object
    // These keys are used to read and write hook to DataStore
    private companion object {
        val START = booleanPreferencesKey("start")
        val LATITUDE = floatPreferencesKey("latitude")
        val LONGITUDE = floatPreferencesKey("longitude")
        val HOOKED_SYSTEM = booleanPreferencesKey("system_hooked")
        val RANDOM_POSITION = booleanPreferencesKey("random_position")
        // Assuming accuracy_level is stored as a String based on EditTextPreference usage
        val ACCURACY_SETTING = stringPreferencesKey("accuracy_level")
        val SPEED_SETTING = floatPreferencesKey("speed_setting") // Kecepatan (Float)
        val BEARING_SETTING = floatPreferencesKey("bearing_setting") // Arah Hadap (Float)
        val ALTITUDE_SETTING = doublePreferencesKey("altitude_setting") // Ketinggian (Double)
        val PROVIDER_SETTING = stringPreferencesKey("provider_setting") // Provider (String)
        val MAP_TYPE = intPreferencesKey("map_type")
        // Assuming dark_theme is stored as an Int based on DropDownPreference usage
        val DARK_THEME = intPreferencesKey("dark_theme")
        val DISABLE_UPDATE = booleanPreferencesKey("update_disabled")
        val ENABLE_JOYSTICK = booleanPreferencesKey("joystick_enabled")

    }

    // StateFlows to expose preferences reactively to other parts of the app (ViewModel, UI)
    // Initial values are set to common defaults, but will be immediately updated by the collect block in init.
    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted.asStateFlow()

    private val _latitude = MutableStateFlow(0f)
    val latitude: StateFlow<Float> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(0f)
    val longitude: StateFlow<Float> = _longitude.asStateFlow()

    private val _isSystemHooked = MutableStateFlow(false)
    val isSystemHooked: StateFlow<Boolean> = _isSystemHooked.asStateFlow()

    private val _isRandomPosition = MutableStateFlow(false)
    val isRandomPosition: StateFlow<Boolean> = _isRandomPosition.asStateFlow()

    // Accuracy level StateFlow (String)
    private val _accuracyLevel = MutableStateFlow("10") // Default value as String
    val accuracyLevel: StateFlow<String> = _accuracyLevel.asStateFlow()

    // Map type StateFlow (Int)
    private val _mapType = MutableStateFlow(1) // Default value 1 based on common map types
    val mapType: StateFlow<Int> = _mapType.asStateFlow()

    // Dark theme StateFlow (Int)
    private val _darkTheme = MutableStateFlow(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // Default value
    val darkTheme: StateFlow<Int> = _darkTheme.asStateFlow()

    private val _isUpdateDisabled = MutableStateFlow(false)
    val isUpdateDisabled: StateFlow<Boolean> = _isUpdateDisabled.asStateFlow()

    private val _isJoystickEnabled = MutableStateFlow(false)
    val isJoystickEnabled: StateFlow<Boolean> = _isJoystickEnabled.asStateFlow()
    // --- StateFlows Baru ---
    private val _speed = MutableStateFlow(0.0f) // Default kecepatan 0
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _bearing = MutableStateFlow(0.0f) // Default bearing 0
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val _altitude = MutableStateFlow(0.0) // Default altitude 0
    val altitude: StateFlow<Double> = _altitude.asStateFlow()

    private val _provider = MutableStateFlow("gps") // Default provider gps
    val provider: StateFlow<String> = _provider.asStateFlow()
    // ----------------------

    init {
        // Launch a coroutine on the application scope to collect initial and subsequent
        // preference changes from DataStore. This keeps the StateFlows updated automatically.
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data.collect { preferences ->
                // Update StateFlows whenever DataStore changes
                // Use the Elvis operator (?:) with appropriate default values for each type
                _isStarted.value = preferences[START] == true
                _latitude.value = preferences[LATITUDE] ?: 0f
                _longitude.value = preferences[LONGITUDE] ?: 0f
                _isSystemHooked.value = preferences[HOOKED_SYSTEM] == true
                _isRandomPosition.value = preferences[RANDOM_POSITION] == true
                _accuracyLevel.value = preferences[ACCURACY_SETTING] ?: "10" // Default String
                _mapType.value = preferences[MAP_TYPE] ?: 1 // Default Int
                _darkTheme.value = preferences[DARK_THEME] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM // Default Int
                _isUpdateDisabled.value = preferences[DISABLE_UPDATE] == true // Correct handling for Boolean
                _isJoystickEnabled.value = preferences[ENABLE_JOYSTICK] == true
                _speed.value = preferences[SPEED_SETTING] ?: 0.0f
                _bearing.value = preferences[BEARING_SETTING] ?: 0.0f
                _altitude.value = preferences[ALTITUDE_SETTING] ?: 0.0
                _provider.value = preferences[PROVIDER_SETTING] ?: "gps"
            }
        }
    }

    // Suspend functions to write preferences to DataStore.
    // These functions are suspend because DataStore write operations are asynchronous.
    // They should NOT manually update the StateFlows (_isStarted.value = value, etc.),
    // as the collect block in the init function handles updating StateFlows reactively
    // based on changes in DataStore.

    /**
     * Sets the 'start' preference value.
     * @param value The boolean value to set.
     */
    suspend fun setStarted(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[START] = value
        }
    }

    /**
     * Sets the 'latitude' and 'longitude' preference values.
     * @param lat The float value for latitude.
     * @param lng The float value for longitude.
     */
    suspend fun setLocation(lat: Float, lng: Float) {
        dataStore.edit { preferences ->
            preferences[LATITUDE] = lat
            preferences[LONGITUDE] = lng
        }
    }

    /**
     * Sets the 'system_hooked' preference value.
     * @param value The boolean value to set.
     */
    suspend fun setSystemHooked(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[HOOKED_SYSTEM] = value
        }
    }

    /**
     * Sets the 'random_position' preference value.
     * @param value The boolean value to set.
     */
    suspend fun setRandomPosition(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[RANDOM_POSITION] = value
        }
    }

    /**
     * Sets the 'accuracy_level' preference value.
     * @param value The string value for accuracy level.
     */
    suspend fun setAccuracyLevel(value: String) { // Parameter type is String
        dataStore.edit { preferences ->
            preferences[ACCURACY_SETTING] = value
        }
    }

    /**
     * Sets the 'map_type' preference value.
     * @param value The integer value for map type.
     */
    suspend fun setMapType(value: Int) {
        dataStore.edit { preferences ->
            preferences[MAP_TYPE] = value
        }
    }

    /**
     * Sets the 'dark_theme' preference value.
     * @param value The integer value for dark theme mode.
     */
    suspend fun setDarkTheme(value: Int) { // Parameter type is Int
        dataStore.edit { preferences ->
            preferences[DARK_THEME] = value
        }
    }

    /**
     * Sets the 'update_disabled' preference value.
     * @param value The boolean value to set.
     */
    suspend fun setUpdateDisabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[DISABLE_UPDATE] = value
        }
    }

    /**
     * Sets the 'joystick_enabled' preference value.
     * @param value The boolean value to set.
     */
    suspend fun setJoystickEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_JOYSTICK] = value
        }
    }

    /** Sets the 'speed' preference value. */
    suspend fun setSpeed(value: Float) {
        dataStore.edit { preferences -> preferences[SPEED_SETTING] = value }
    }

    /** Sets the 'bearing' preference value. */
    suspend fun setBearing(value: Float) {
        dataStore.edit { preferences -> preferences[BEARING_SETTING] = value }
    }

    /** Sets the 'altitude' preference value. */
    suspend fun setAltitude(value: Double) {
        dataStore.edit { preferences -> preferences[ALTITUDE_SETTING] = value }
    }

    /** Sets the 'provider' preference value. */
    suspend fun setProvider(value: String) {
        dataStore.edit { preferences -> preferences[PROVIDER_SETTING] = value }
    }

    /**
     * Update multiple preferences atomically using DataStore.
     * This function is suspend because DataStore write operations are asynchronous.
 //    * @param start The boolean value for 'start'.
 //    * @param la The double value for latitude.
 //    * @param ln The double value for longitude.
     */
    /*suspend fun update(start: Boolean, la: Double, ln: Double) {
        dataStore.edit { preferences ->
            preferences[START] = start
            // Convert Double to Float for Float preferences key
            preferences[LATITUDE] = la.toFloat()
            preferences[LONGITUDE] = ln.toFloat()
        }
        // StateFlows will be updated automatically by the collect block
    }*/

    // Removed the old SharedPreferences object 'pref'

    // Removed the old direct access properties (val isStarted, val lat, etc.)
    // The new way to access preference values is by observing the corresponding StateFlows
    // (e.g., observing PrefManager.isStarted, PrefManager.latitude, etc.)
}
