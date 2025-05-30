package com.roxgps.ui.viewmodel

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roxgps.R
import com.roxgps.helper.ILocationHelper
import com.roxgps.repository.SearchRepository
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

/**
 * ViewModel untuk mengelola state dan logika terkait tampilan peta.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationHelper: ILocationHelper,
    private val searchRepository: SearchRepository,
    private val prefManager: PrefManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // === Map State ===
    private val _mapCenter = MutableStateFlow<Location?>(null)
    val mapCenter: StateFlow<Location?> = _mapCenter.asStateFlow()

    private val _zoomLevel = MutableStateFlow(DEFAULT_ZOOM)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _isFollowingUser = MutableStateFlow(true)
    val isFollowingUser: StateFlow<Boolean> = _isFollowingUser.asStateFlow()

    private val _isMapReady = MutableStateFlow(false)
    val isMapReady: StateFlow<Boolean> = _isMapReady.asStateFlow()

    // === Map Style ===
    val mapType: StateFlow<Int> = prefManager.mapType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_MAP_TYPE)

    // === User Location ===
    val currentLocation: StateFlow<Location?> = locationHelper.currentFakeLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // === Search Results ===
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    // === Events ===
    private val _mapEvent = MutableSharedFlow<MapEvent>()
    val mapEvent: SharedFlow<MapEvent> = _mapEvent.asSharedFlow()

    // === Map Operations ===


    /**
     * Inisialisasi peta
     */
    fun initializeMap() {
        viewModelScope.launch {
            try {
                _isMapReady.value = true
                // Muat lokasi terakhir jika ada
                locationHelper.getLastKnownLocation()?.let { location ->
                    _mapCenter.value = location
                }
            } catch (e: Exception) {
                Timber.e(e, "Error initializing map")
                _mapEvent.emit(MapEvent.ShowError(context.getString(R.string.map_init_error)))
            }
        }
    }

    /**
     * Update lokasi tengah peta
     */
    fun updateMapCenter(location: Location) {
        viewModelScope.launch {
            _mapCenter.value = location
        }
    }

    /**
     * Update level zoom peta
     */
    fun updateZoomLevel(zoom: Float) {
        viewModelScope.launch {
            _zoomLevel.value = zoom
        }
    }

    /**
     * Toggle mode mengikuti lokasi user
     */
    fun toggleFollowUser() {
        viewModelScope.launch {
            _isFollowingUser.value = !_isFollowingUser.value
            if (_isFollowingUser.value) {
                currentLocation.value?.let { location ->
                    _mapCenter.value = location
                    _mapEvent.emit(MapEvent.AnimateToLocation(location))
                }
            }
        }
    }

    /**
     * Ubah tipe peta
     */
    fun setMapType(type: Int) {
        viewModelScope.launch {
            try {
                prefManager.setMapType(type)
                _mapEvent.emit(MapEvent.MapTypeChanged(type))
            } catch (e: Exception) {
                Timber.e(e, "Error changing map type")
                _mapEvent.emit(MapEvent.ShowError(context.getString(R.string.map_type_change_error)))
            }
        }
    }

    /**
     * Cari lokasi berdasarkan query
     */
    fun searchLocation(query: String) {
        viewModelScope.launch {
            try {
                searchRepository.searchAddress(query)
            } catch (e: Exception) {
                Timber.e(e, "Error searching location")
                _mapEvent.emit(MapEvent.ShowError(context.getString(R.string.search_error)))
            }
        }
    }

    /**
     * Pilih lokasi dari hasil pencarian
     */
    fun selectSearchResult(result: SearchResult) {
        viewModelScope.launch {
            val location = Location("search").apply {
                latitude = result.latitude
                longitude = result.longitude
            }
            _mapCenter.value = location
            _isFollowingUser.value = false
            _mapEvent.emit(MapEvent.AnimateToLocation(location))
        }
    }

    /**
     * Bersihkan hasil pencarian
     */
    fun clearSearchResults() {
        viewModelScope.launch {
            _searchResults.value = emptyList()
        }
    }

    /**
     * Update status map ready
     */
    fun setMapReady(ready: Boolean) {
        viewModelScope.launch {
            _isMapReady.value = ready
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("MapViewModel cleared")
    }

    companion object {
        private const val DEFAULT_ZOOM = 15f
        private const val DEFAULT_MAP_TYPE = 1 // Normal map type
    }
}

// === State Classes ===

/**
 * Data class untuk menyimpan hasil pencarian lokasi
 */
data class SearchResult(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Sealed class untuk event-event yang terjadi di peta
 */
sealed class MapEvent {
    data class ShowError(val message: String) : MapEvent()
    data class AnimateToLocation(val location: Location) : MapEvent()
    data class MapTypeChanged(val type: Int) : MapEvent()
    object ClearSearch : MapEvent()
}