package com.roxgps.ui.viewmodel

import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roxgps.R
import com.roxgps.helper.SearchProgress
import com.roxgps.repository.FavoriteRepository
import com.roxgps.repository.HookStatusRepository
import com.roxgps.repository.LocationRepository
import com.roxgps.repository.SearchRepository
import com.roxgps.repository.TokenRepository
import com.roxgps.room.Favorite
import com.roxgps.utils.PrefManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val prefManager: PrefManager, // Injeksi PrefManager
    private val searchRepository: SearchRepository,
    private val hookStatusRepository: HookStatusRepository,
    private val tokenRepository: TokenRepository,
    private val locationRepository: LocationRepository, // Injeksi LocationRepository
    @ApplicationContext private val context: Context
) : ViewModel() {

    // --- State dari PrefManager (langsung dari DataStore) ---
    val isStarted: StateFlow<Boolean> = prefManager.isStarted.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    // Latitude dan longitude ini akan mencerminkan lokasi yang terakhir disimpan/disetel
    val latitude: StateFlow<Float> = prefManager.latitude.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f
    )
    val longitude: StateFlow<Float> = prefManager.longitude.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f
    )
    val isSystemHooked: StateFlow<Boolean> = prefManager.isSystemHooked.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val isRandomPosition: StateFlow<Boolean> = prefManager.isRandomPosition.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val accuracyLevel: StateFlow<String> = prefManager.accuracyLevel.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Normal"
    )
    val mapType: StateFlow<Int> = prefManager.mapType.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )
    val darkTheme: StateFlow<Int> = prefManager.darkTheme.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )
    val isUpdateDisabled: StateFlow<Boolean> = prefManager.isUpdateDisabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val isJoystickEnabled: StateFlow<Boolean> = prefManager.isJoystickEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val speed: StateFlow<Float> = prefManager.speed.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f
    )
    val bearing: StateFlow<Float> = prefManager.bearing.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f
    )
    val altitude: StateFlow<Double> = prefManager.altitude.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0
    )
    val provider: StateFlow<String> = prefManager.provider.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "gps"
    )
    // --- Akhir State dari PrefManager ---

    // _searchedLocation tetap ada untuk hasil pencarian eksplisit
    private val _searchedLocation = MutableStateFlow<Location?>(null)
    val searchedLocation: StateFlow<Location?> = _searchedLocation.asStateFlow()

    // StateFlow untuk lokasi perangkat saat ini (dari LocationRepository jika ILocationHelper memberikan update real)
    val currentActiveLocation: StateFlow<Location?> = locationRepository.currentActiveLocation.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    // Perbarui lokasi di PrefManager (yang akan diakses oleh Service dan UI)
    fun updateLocation(lat: Float, lng: Float) {
        Timber.d("MainViewModel: updateLocation($lat, $lng) triggered.")
        viewModelScope.launch {
            prefManager.setLocation(lat, lng) // Panggil PrefManager untuk update DataStore
        }
    }

    // Metode update state PrefManager lainnya
    fun updateStartedState(started: Boolean) {
        Timber.d("MainViewModel: updateStartedState($started) triggered.")
        viewModelScope.launch { prefManager.setStarted(started) }
    }
    fun updateSystemHooked(hooked: Boolean) { viewModelScope.launch { prefManager.setSystemHooked(hooked) } }
    fun updateRandomPosition(random: Boolean) { viewModelScope.launch { prefManager.setRandomPosition(random) } }
    fun updateAccuracyLevel(level: String) { viewModelScope.launch { prefManager.setAccuracyLevel(level) } }
    fun updateMapType(type: Int) { viewModelScope.launch { prefManager.setMapType(type) } }
    fun updateDarkTheme(dark: Int) { viewModelScope.launch { prefManager.setDarkTheme(dark) } }
    fun updateDisabled(disabled: Boolean) { viewModelScope.launch { prefManager.setUpdateDisabled(disabled) } }
    fun updateJoystickEnabled(enabled: Boolean) { viewModelScope.launch { prefManager.setJoystickEnabled(enabled) } }
    fun updateSpeed(value: Float) { viewModelScope.launch { prefManager.setSpeed(value) } }
    fun updateBearing(value: Float) { viewModelScope.launch { prefManager.setBearing(value) } }
    fun updateAltitude(value: Double) { viewModelScope.launch { prefManager.setAltitude(value) } }
    fun updateProvider(value: String) { viewModelScope.launch { prefManager.setProvider(value) } }

    val allFavList: StateFlow<List<Favorite>> =
        favoriteRepository.getAllFavorites
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    private val _response = MutableLiveData<Long>()
    val response: LiveData<Long> = _response

    val isModuleHooked: StateFlow<Boolean> =
        hookStatusRepository.isModuleHooked
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                false
            )

    val lastHookError: StateFlow<String?> =
        hookStatusRepository.lastHookError
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )

    val token: StateFlow<String?> =
        tokenRepository.token
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )

    fun fetchToken() {
        Timber.d("MainViewModel: fetchToken() triggered.")
        viewModelScope.launch {
            tokenRepository.fetchToken()
        }
    }

    val searchResult: StateFlow<SearchProgress> =
        searchRepository.searchState
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SearchProgress.Idle
            )

    fun searchAddress(query: String) {
        Timber.d("MainViewModel: searchAddress() triggered with query: $query")
        viewModelScope.launch {
            searchRepository.searchAddress(query)
        }
    }

    fun triggerReverseGeocoding(lat: Double, lon: Double) {
        Timber.d("MainViewModel: triggerReverseGeocoding() triggered for $lat, $lon")
        viewModelScope.launch {
            searchRepository.getAddressFromLatLng(
                lat,
                lon
            )
        }
    }

    fun resetSearchState() {
        Timber.d("MainViewModel: resetSearchState() triggered.")
        viewModelScope.launch { searchRepository.resetSearchState() }
    }

    // setSearchedLocation akan memperbarui _searchedLocation internal ViewModel
    // dan juga memperbarui lokasi yang disimpan di PrefManager via LocationRepository.
    fun setSearchedLocation(location: Location) {
        Timber.d("MainViewModel: setSearchedLocation() triggered for ${location.latitude}, ${location.longitude}")
        _searchedLocation.value = location // Perbarui StateFlow internal ViewModel
        viewModelScope.launch {
            // Perbarui lokasi persisten di PrefManager melalui LocationRepository
            locationRepository.updateSetLocation(location)
            Timber.d("MainViewModel: Location saved to PrefManager via LocationRepository.")
        }
    }

    fun deleteSingleFavorite(favorite: Favorite) {
        Timber.d("MainViewModel: deleteSingleFavorite() triggered for ${favorite.address}")
        viewModelScope.launch {
            favoriteRepository.deleteSingleFavorite(favorite)
            _showToastEvent.emit("Favorite deleted")
        }
    }

    fun storeFavorite(
        address: String,
        lat: Double,
        lon: Double
    ) {
        Timber.d("MainViewModel: storeFavorite() triggered for $address, $lat, $lon")
        viewModelScope.launch {
            runCatching {
                favoriteRepository.saveFavorite(address, lat, lon)
                _showToastEvent.emit(context.getString(R.string.save))
            }.onFailure { error ->
                _showToastEvent.emit(context.getString(R.string.cant_save) + ": ${error.message}")
                Timber.e(error, "Failed to save favorite")
            }
        }
    }

    private val _showToastEvent = MutableSharedFlow<String>()
    val showToastEvent: SharedFlow<String> =
        _showToastEvent.asSharedFlow()

    fun triggerShowToastEvent(message: String) {
        viewModelScope.launch {
            _showToastEvent.emit(message)
        }
    }

    private val _installAppEvent = MutableSharedFlow<Uri>()
    val installAppEvent = _installAppEvent.asSharedFlow()

    fun triggerInstallAppEvent(fileUri: Uri) {
        viewModelScope.launch {
            _installAppEvent.emit(fileUri)
        }
    }

    private val _showXposedDialogEvent = MutableSharedFlow<Boolean>()
    val showXposedDialogEvent = _showXposedDialogEvent.asSharedFlow()

    fun setShowXposedDialog(isShow: Boolean) {
        Timber.d("MainViewModel: setShowXposedDialog() triggered with $isShow")
        viewModelScope.launch { _showXposedDialogEvent.emit(isShow) }
    }

    init {
        Timber.d("MainViewModel created")
        fetchToken()

        // Mengamati searchResult dari SearchRepository
        viewModelScope.launch {
            searchResult.collect { state ->
                when (state) {
                    is SearchProgress.Complete -> {
                        val resultLat = state.lat
                        val resultLon = state.lon
                        val foundLocation = Location("search").apply {
                            latitude = resultLat
                            longitude = resultLon
                        }
                        // Memperbarui _searchedLocation di ViewModel dan juga PrefManager
                        setSearchedLocation(foundLocation) // Panggil metode ini
                        Timber.d("MainViewModel: searchResult Complete, updating _searchedLocation with Location object.")
                        _showToastEvent.emit("Search complete: ${resultLat}, $resultLon")
                    }
                    is SearchProgress.Error -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult Error, clearing _searchedLocation. Message: ${state.message}")
                        _showToastEvent.emit("Search failed: ${state.message}")
                    }
                    is SearchProgress.Fail -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult Fail, clearing _searchedLocation. Error: ${state.message}")
                        _showToastEvent.emit("Search failed: ${state.message ?: "Unknown error"}")
                    }
                    is SearchProgress.PartialResult -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult PartialResult, clearing _searchedLocation.")
                        _showToastEvent.emit("Multiple results found. Please select.")
                    }
                    SearchProgress.Idle -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult Idle, clearing _searchedLocation.")
                    }
                    SearchProgress.Progress -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult Progress, clearing _searchedLocation.")
                    }
                    SearchProgress.Loading -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult Loading, clearing _searchedLocation.")
                    }
                    is SearchProgress.NoResultFound -> {
                        _searchedLocation.value = null
                        Timber.d("MainViewModel: searchResult NoResultFound, clearing _searchedLocation.")
                        _showToastEvent.emit("No results found.")
                    }
                }
            }
        }

        // Opsional: Muat lokasi terakhir yang disetel saat ViewModel dibuat
        viewModelScope.launch {
            val lastSetLoc = locationRepository.getLastSetLocation()
            _searchedLocation.value = lastSetLoc
            Timber.d("MainViewModel: Initial _searchedLocation set from PrefManager: $lastSetLoc")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("MainViewModel cleared")
    }
}