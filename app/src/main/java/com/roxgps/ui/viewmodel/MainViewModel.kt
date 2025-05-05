package com.roxgps.ui.viewmodel

// =====================================================================
// Import Library MainViewModel
// =====================================================================

// Hapus import yang tadinya dipakai untuk logic DownloadManager & Receiver di ViewModel
// import android.app.DownloadManager // <-- DIHAPUS
// import android.content.BroadcastReceiver // <-- DIHAPUS
// import android.content.Intent // <-- DIHAPUS
// import android.content.IntentFilter // <-- DIHAPUS
// import android.database.ContentObserver // <-- DIHAPUS
// import android.database.Cursor // <-- DIHAPUS
import android.net.Uri // Masih perlu untuk event install
// import android.os.Handler // <-- DIHAPUS
// import android.os.Looper // <-- DIHAPUS
// import androidx.core.content.FileProvider // <-- DIHAPUS
import android.location.Location // Untuk objek Location (jika masih dipakai, misal di setSearchedLocation)


import androidx.lifecycle.LiveData // Untuk LiveData (jika masih dipakai, misal untuk response simple)
import androidx.lifecycle.MutableLiveData // Untuk MutableLiveData
import androidx.lifecycle.ViewModel // Base ViewModel
import androidx.lifecycle.viewModelScope // Scope Coroutine ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel // Anotasi Hilt ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext // Qualifier Context Aplikasi
import com.roxgps.BuildConfig // Untuk BuildConfig (jika masih dipakai, jarang di ViewModel yang rapi)
import com.roxgps.R // Untuk resource string (jika masih dipakai, misal untuk event message)
import com.roxgps.repository.FavoriteRepository // <-- Inject Repository Favorit yang sudah diperbaiki
import com.roxgps.repository.DownloadRepository // <-- Inject Repository Download BARU
import com.roxgps.repository.SearchRepository // <-- Inject Repository Search BARU
import com.roxgps.repository.HookStatusRepository // <-- Import Repository Hook Status BARU
import com.roxgps.repository.TokenRepository // <-- IMPORT Repository Token BARU (Setelah Ganti Nama)
import com.roxgps.room.Favorite // Import Entity Favorite
// import com.roxgps.update.UpdateChecker // <-- DIHAPUS, logicnya di DownloadRepository
import com.roxgps.utils.PrefManager // Inject PrefManager
// Hapus import extension functions yang langsung interaksi UI atau threading low-level
// import com.roxgps.utils.ext.onIO // <-- DIHAPUS
// import com.roxgps.utils.ext.onMain // <-- DIHAPUS
// import com.roxgps.utils.ext.showToast // <-- DIHAPUS
import kotlinx.coroutines.Dispatchers // Untuk Dispatchers (jika masih perlu manual, jarang di ViewModel yg rapi)
import kotlinx.coroutines.flow.* // Untuk Flow API (StateFlow, SharedFlow, etc.)
import kotlinx.coroutines.launch // Untuk Coroutine Scope
// import kotlinx.coroutines.withContext // <-- DIHAPUS
import timber.log.Timber // Untuk logging
// import java.io.File // <-- DIHAPUS
import javax.inject.Inject // Untuk Dependency Injection
// import kotlin.math.roundToInt // <-- DIHAPUS

// Import Sealed Class State dari Repository/Helper BARU
import com.roxgps.repository.DownloadRepository.DownloadState // <-- Import dari Repository Download
import com.roxgps.helper.SearchProgress // <-- Import Sealed Class SearchProgress

// Asumsi data class YourUpdateModel ada di file terpisah di package com.roxgps.update
import com.roxgps.update.YourUpdateModel


// =====================================================================
// ViewModel Utama Aplikasi (Setelah Refactoring & Integrasi Token)
// Bertanggung jawab mengatur state UI dan delegasi tugas ke layer data/bisnis (Repository/Helper).
// =====================================================================
@HiltViewModel
class MainViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository, // Inject FavoriteRepository (sudah diperbaiki)
    private val prefManger: PrefManager, // Inject PrefManager
    private val downloadRepository: DownloadRepository, // <-- Inject Repository Download BARU
    private val searchRepository: SearchRepository, // <-- Inject Repository Search BARU
    private val hookStatusRepository: HookStatusRepository, // <-- Import Repository Hook Status BARU
    @Inject private val tokenRepository: TokenRepository, // <-- INJECT Repository Token BARU (Setelah Ganti Nama)
    @ApplicationContext private val context: Context // Context Aplikasi, gunakan HANYA untuk yang butuh (misal resources string untuk event message)
) : ViewModel() {

    // =====================================================================
    // State UI Utama (Diekspos dari PrefManager/Repository)
    // ViewModel hanya meneruskan Flow/LiveData dari sumbernya.
    // =====================================================================
    val getLat  = prefManger.getLat // Diekspos dari PrefManager (asumsi PrefManager expose Flow/LiveData)
    val getLng  = prefManger.getLng // Diekspos dari PrefManager
    val isStarted = prefManger.isStarted // Diekspos dari PrefManager
    val mapType = prefManger.mapType // Diekspos dari PrefManager

    // =====================================================================
    // Data/State Favorit (Diekspos dari FavoriteRepository)
    // ViewModel hanya meneruskan Flow dari Repository.
    // =====================================================================
    // StateFlow allFavList langsung mengambil dari Repository
    val allFavList : StateFlow<List<Favorite>> =  favoriteRepository.getAllFavorites // <-- Ambil Flow dari Repository
        .stateIn( // Konversi Flow menjadi StateFlow di dalam viewModelScope
             viewModelScope,
             SharingStarted.WhileSubscribed(5000), // Atur strategi sharing
             emptyList() // Nilai awal
        )

    // _response LiveData: Jika FavoriteRepository.saveFavorite mengembalikan ID Long, bisa post ke sini.
    // Jika tidak mengembalikan Long (unit), LiveData ini bisa dihapus atau diganti event sukses/gagal.
    private val _response = MutableLiveData<Long>() // LiveData untuk respons (opsional, tergantung return Repo.saveFavorite)
    val response: LiveData<Long> = _response // Diekspos sebagai LiveData (opsional)

    // =====================================================================
    // State Hook Status & Error (Diekspos dari HookStatusRepository)
    // ViewModel hanya meneruskan StateFlow dari Repository ini.
    // =====================================================================
    val isModuleHooked: StateFlow<Boolean> = hookStatusRepository.isModuleHooked // <-- Amati StateFlow dari Repository Hook Status
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000), // Gunakan strategi sharing yang sesuai
            false // Nilai awal (asumsi default Repository adalah false)
        )

    val lastHookError: StateFlow<String?> = hookStatusRepository.lastHookError // <-- Amati StateFlow dari Repository Hook Status
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null // Nilai awal (asumsi default Repository adalah null)
        )

    // =====================================================================
    // State Token Gojek (Diekspos dari TokenRepository)
    // ViewModel hanya meneruskan StateFlow dari Repository ini.
    // =====================================================================
    val gojekToken: StateFlow<String?> = tokenRepository.gojekToken // <-- Amati StateFlow dari Repository Token BARU
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null // Nilai awal (asumsi default Repository adalah null)
        )


    // =====================================================================
    // State & Metode untuk Update & Download (DELEGASI ke DownloadRepository)
    // ViewModel hanya memanggil method di Repository dan mengamati StateFlow-nya.
    // =====================================================================

    // StateFlow untuk informasi update yang tersedia (Diekspos dari DownloadRepository)
    val updateInfo: StateFlow<YourUpdateModel?> = downloadRepository.updateInfo // <-- Amati StateFlow dari DownloadRepository
         .map { updateCheckerUpdate -> // Map UpdateChecker.Update? ke YourUpdateModel? jika tipe beda
              // Lakukan mapping jika formatnya beda, jika tipenya sudah YourUpdateModel, map ini tidak perlu
              updateCheckerUpdate as YourUpdateModel? // Contoh casting sederhana jika tipe sama
         }
         .stateIn(
             viewModelScope,
             SharingStarted.WhileSubscribed(5000),
             null
         )

     // StateFlow untuk status dan progress proses download (Diekspos dari DownloadRepository)
    val downloadState: StateFlow<DownloadState> = downloadRepository.downloadState // <-- Amati StateFlow dari DownloadRepository
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DownloadState.Idle // Ambil nilai awal dari sealed class DownloadState
        )

    // getAvailableUpdate: Mengakses StateFlow updateInfo yang sudah diamati
    fun getAvailableUpdate(): YourUpdateModel? {
        return updateInfo.value // <-- Ambil value dari StateFlow ViewModel (yang mengamati Repo)
    }

    // clearUpdate: Delegasi tugas ke DownloadRepository
    fun clearUpdate() {
        viewModelScope.launch { downloadRepository.clearUpdateInfo() } // <-- Panggil method di DownloadRepository
    }

    // startDownload: Delegasi tugas ke DownloadRepository
    fun startDownload(update: YourUpdateModel) { // Parameter YourUpdateModel (sesuai yang diekspos updateInfo)
        // Cek state dari StateFlow ViewModel (yang mengamati Repository)
        if(downloadState.value is DownloadState.Idle) {
             // Delegasikan tugas start download ke Repository Download
             viewModelScope.launch {
                 downloadRepository.startDownload(update.assetUrl, update.assetName) // <-- Panggil method di DownloadRepository
             }
        } else {
             Timber.d("Download already in progress or state is not Idle.")
            // Optional: Emit event Toast atau log kalau download sudah berjalan
             viewModelScope.launch { _showToastEvent.emit("Download is already in progress.") }
        }
    }

    // cancelDownload: Delegasi tugas ke DownloadRepository
    fun cancelDownload() { // Tidak perlu Context parameter lagi
        // Delegasikan tugas cancel download ke Repository Download
        viewModelScope.launch { downloadRepository.cancelDownload() } // <-- Panggil method di Repository
    }

     // resetDownloadState: Delegasi tugas ke DownloadRepository
     fun resetDownloadState() { // Tidak perlu Context parameter lagi
          viewModelScope.launch { downloadRepository.resetDownloadState() } // <-- Panggil method di Repository
     }

    // checkUpdates: Method untuk memicu cek update (Dipanggil dari UI Nav Drawer)
     fun checkForUpdates() {
          Timber.d("MainViewModel: checkForUpdates() triggered.")
          viewModelScope.launch {
              downloadRepository.checkForUpdates() // <-- Delegasikan panggilan ke Repository Download
          }
     }


    // =====================================================================
    // State & Metode untuk Search (DELEGASI ke SearchRepository)
    // ViewModel hanya memanggil method di Repository dan mengamati StateFlow<SearchProgress>.
    // =====================================================================

    // StateFlow untuk hasil search (Diekspos dari SearchRepository)
    val searchResult: StateFlow<SearchProgress> = searchRepository.searchState // <-- Amati StateFlow dari SearchRepository
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SearchProgress.Idle // Ambil nilai awal dari sealed class SearchProgress
        )

    // searchAddress: Delegasi tugas ke SearchRepository
    fun searchAddress(query: String) {
         Timber.d("MainViewModel: searchAddress() triggered with query: $query")
         // Method ini hanya memicu proses search di Repository.
         // Hasilnya (termasuk progress dan error) diamati dari StateFlow<SearchProgress> searchResult di atas.
         viewModelScope.launch {
             searchRepository.searchAddress(query) // <-- Panggil method di SearchRepository
         }
    }

    // triggerReverseGeocoding: Delegasi tugas ke SearchRepository
    fun triggerReverseGeocoding(lat: Double, lon: Double) {
         Timber.d("MainViewModel: triggerReverseGeocoding() triggered for $lat, $lon")
         viewModelScope.launch {
             searchRepository.getAddressFromLatLng(lat, lon) // <-- Panggil method di SearchRepository
         }
    }

     // resetSearchState: Delegasi tugas ke SearchRepository
     fun resetSearchState() {
          Timber.d("MainViewModel: resetSearchState() triggered.")
          viewModelScope.launch { searchRepository.resetSearchState() } // <-- Panggil method di SearchRepository
     }

    // setSearchedLocation: Method ini bisa terima Location dari Activity (hasil Complete dari SearchProgress)
    // Lakukan sesuatu, misal update lat/lon utama di PrefManager atau emit ke StateFlow lokasi terpilih
    fun setSearchedLocation(location: Location) {
        Timber.d("MainViewModel: setSearchedLocation() triggered for ${location.latitude}, ${location.longitude}")
        // Update lat/lon di PrefManager (delegasi ke PrefManager)
        update(false, location.latitude, location.longitude) // Menggunakan method update di ViewModel yang memanggil PrefManager
        // Optional: Emit lokasi terpilih ke StateFlow lain jika ada (_selectedLocation)
        // viewModelScope.launch { _selectedLocation.emit(location) }
    }


    // =====================================================================
    // Metode untuk Interaksi Favorit (Delegasi ke FavoriteRepository)
    // =====================================================================

    // deleteFavorite: Delegasi tugas ke FavoriteRepository
    fun deleteFavorite(favorite: Favorite) {
        Timber.d("MainViewModel: deleteFavorite() triggered for ${favorite.address}")
        viewModelScope.launch { // Gunakan Scope ViewModel
            favoriteRepository.deleteFavorite(favorite) // <-- Delegasi ke Repository!
            // Optional: Emit event Toast sukses dari sini jika perlu
            // _showToastEvent.emit("Favorite deleted")
        }
    }

    // storeFavorite: Delegasi tugas ke FavoriteRepository (sudah diperbaiki)
    fun storeFavorite(
        address: String,
        lat: Double,
        lon: Double
    ) {
        Timber.d("MainViewModel: storeFavorite() triggered for $address, $lat, $lon")
        viewModelScope.launch { // Gunakan Scope ViewModel (Repository handle threading)
            runCatching { // Gunakan runCatching untuk handle Exception dari Repository (misal slot penuh)
                favoriteRepository.saveFavorite(address, lat, lon) // <-- DELEGASI KE REPOSITORY!
                // Setelah Repository selesai menyimpan (kalau sukses):
                _showToastEvent.emit(context.getString(R.string.save)) // Emit event Toast sukses
            }.onFailure { error ->
                 // Kalau terjadi error di Repository (misal Exception slot penuh)
                 _showToastEvent.emit(context.getString(R.string.cant_save) + ": ${error.message}") // Emit event Toast error
                 Timber.e(error, "Failed to save favorite") // Log error
            }
        }
    }


    // =====================================================================
    // Event Satu Kali (Diekspos ke UI untuk Aksi)
    // ViewModel emit event ini, Activity collect & melakukan aksi UI.
    // =====================================================================

    // Event untuk menampilkan Toast (Activity akan amati ini)
    private val _showToastEvent = MutableSharedFlow<String>() // SharedFlow untuk event
    val showToastEvent = _showToastEvent.asSharedFlow() // Diekspos sebagai SharedFlow

    // Event untuk membuka Package Installer APK (Activity akan amati ini)
    private val _installAppEvent = MutableSharedFlow<Uri>() // SharedFlow untuk event
    val installAppEvent = _installAppEvent.asSharedFlow() // Diekspos sebagai SharedFlow

    // Event untuk menampilkan dialog (Jika ada dialog yang triggernya dari ViewModel, misal Xposed missing)
    // private val _showDialogEvent = MutableSharedFlow<DialogType>() // Contoh dengan sealed class DialogType
    // val showDialogEvent = _showDialogEvent.asSharedFlow()


    // =====================================================================
    // Logic untuk Update Xposed State (State ini SEKARANG datang dari HookStatusRepository)
    // isXposed LiveData bisa dipertahankan jika ada UI lain yang mengamati.
    // =====================================================================
    // isXposed LiveData: State untuk status Xposed (Jika relevan ke UI).
    // Sekarang bisa diambil dari isModuleHooked StateFlow dari HookStatusRepository.
    // LiveData ini bisa dihapus atau dijadikan adapter dari StateFlow ke LiveData.
    // Contoh adapter:
    // val isXposed: LiveData<Boolean> = isModuleHooked.asLiveData() // Butuh dependency androidx.lifecycle:lifecycle-livedata-ktx
    // Atau pertahankan MutableLiveData jika logic status Xposed juga datang dari sumber lain di ViewModel
    // val isXposed = MutableLiveData<Boolean>(true) // Jika status Xposed juga bisa di-set dari ViewModel sendiri

    // Method untuk menunjukkan/menyembunyikan dialog Xposed Missing (Dipanggil dari Activity)
    // Event ini sekarang bisa dipicu berdasarkan isModuleHooked atau lastHookError StateFlow.
     private val _showXposedDialogEvent = MutableSharedFlow<Boolean>() // True = tampil, False = sembunyi
     val showXposedDialogEvent = _showXposedDialogEvent.asSharedFlow()

     // Metode ini bisa dipanggil Activity untuk memberi tahu ViewModel agar tampilkan dialog Xposed (misal user klik info status hook)
     fun setShowXposedDialog(isShow: Boolean) {
          Timber.d("MainViewModel: setShowXposedDialog() triggered with $isShow")
          viewModelScope.launch { _showXposedDialogEvent.emit(isShow) } // Emit event
     }

     // Optional: Metode di ViewModel untuk memicu dialog berdasarkan state HookStatusRepository
     // fun checkAndShowXposedStatusDialog() {
     //     if (!isModuleHooked.value || lastHookError.value != null) {
     //         viewModelScope.launch { _showXposedDialogEvent.emit(true) } // Tampilkan dialog
     //     } else {
     //         viewModelScope.launch { _showXposedDialogEvent.emit(false) } // Sembunyikan dialog (jika sedang tampil)
     //     }
     // }


    // =====================================================================
    // Initialization (Dipanggil saat ViewModel dibuat)
    // =====================================================================
    init {
        Timber.d("MainViewModel created")
        // Memicu cek update awal saat ViewModel dibuat
         viewModelScope.launch {
             downloadRepository.checkForUpdates() // <-- Panggil method di DownloadRepository
         }
        // Mengamati StateFlow/LiveData dari Repository sudah diinisialisasi langsung di deklarasi properti.
        // Initial state searchResult, downloadState, updateInfo, isModuleHooked, lastHookError
        // sudah otomatis Idle/nilai default dari Repository.

        // Memicu pengambilan token Gojek saat ViewModel dibuat
        fetchGojekToken() // <-- Panggil method untuk fetch token Gojek dari Repository Token
    }

    // =====================================================================
    // Cleanup (Dipanggil saat ViewModel dihapus/clear)
    // =====================================================================
    override fun onCleared() {
        super.onCleared()
        Timber.d("MainViewModel cleared")
        // Clean up jika ada resource yang di-manage manual
    }

    // Catatan: Metode update(isStarting, lat, lng) dipanggil dari setSearchedLocation,
    // memastikan lat/lng utama di PrefManager terupdate setelah search.
}
