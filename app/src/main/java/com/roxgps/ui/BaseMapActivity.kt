package com.roxgps.ui // Pastikan package ini sesuai

// =====================================================================
// Import Library BaseMapActivity (Umum - Setelah Final Refactoring Observasi & Dialog Umum)
// =====================================================================

import android.Manifest // Untuk permission
import android.content.Context // Untuk Context
import android.content.Intent // Untuk Intent
import android.content.IntentFilter // Untuk IntentFilter BroadcastReceiver Notifikasi
import android.content.pm.PackageManager // Untuk PackageManager
import android.location.Location // Untuk objek Location standar
import android.os.Bundle // Untuk Bundle
import android.util.Log // Untuk Log
import android.view.View // Untuk View
import android.view.LayoutInflater // Import LayoutInflater (akan dibutuhkan untuk dialogHelper)
import androidx.appcompat.app.AppCompatActivity // Base Activity
import androidx.appcompat.app.AlertDialog // Untuk AlertDialog (Dialogs yang dikelola Base)
import androidx.core.app.ActivityCompat // Untuk cek permission
import androidx.core.view.WindowCompat // Untuk WindowCompat (Edge-to-edge)
import androidx.fragment.app.FragmentActivity // Jika pakai FragmentActivity (jarang jika extends AppCompatActivity)
import androidx.lifecycle.lifecycleScope // Untuk Coroutine Scope Activity
import androidx.lifecycle.repeatOnLifecycle // Untuk RepeatOnLifecycle
import androidx.lifecycle.Lifecycle // Import Lifecycle State
import androidx.viewbinding.ViewBinding // Untuk ViewBinding
import android.view.inputmethod.EditorInfo // Untuk search bar action
import android.view.inputmethod.InputMethodManager // Untuk sembunyikan keyboard
import android.widget.TextView // Untuk akses TextView di dialog (jika UI dialog di-manage di Base)

// Import Hilt
import dagger.hilt.android.AndroidEntryPoint // Anotasi Hilt untuk Activity

// Import resource, binding, viewmodel, utils, dll.
import com.roxgps.R // Resource ID
import com.roxgps.databinding.ActivityMapBinding // Binding View Utama (Layout sama di kedua flavor)
import com.roxgps.ui.viewmodel.MainViewModel // ViewModel Utama (Harus disediakan oleh Hilt)
import com.roxgps.utils.NotificationsChannel // Utility Notifikasi Channel (Disediakan Hilt)
import com.roxgps.utils.PrefManager // Preference Manager (Disediakan Hilt)
import com.roxgps.utils.FileLogger // Untuk Log File (Pastikan ini object)
import com.roxgps.utils.ext.showToast // Extension function Toast (Menggunakan Context Activity)
import kotlinx.coroutines.flow.collectLatest // Untuk collect Flow (lebih aman dari collect)
import kotlinx.coroutines.launch // Untuk launch coroutine
import javax.inject.Inject // Untuk Dependency Injection Hilt

// Import Helper (Disediakan Hilt)
import com.roxgps.helper.PermissionHelper // PermissionHelper (Disediakan Hilt)
import com.roxgps.helper.DialogHelper // DialogHelper (Disediakan Hilt)
// SearchHelper tidak dipanggil langsung lagi dari Activity, tapi dari ViewModel/Repository
// import com.roxgps.helper.SearchHelper

// Import Sealed Class State dari Repository/Helper BARU
import com.roxgps.helper.SearchProgress // <-- Import Sealed Class SearchProgress dari SearchHelper/Repository
import com.roxgps.repository.DownloadRepository.DownloadState // Import Sealed Class DownloadState dari Repository Download

// Model Data (jika digunakan untuk dialog update)
import com.roxgps.update.YourUpdateModel // Asumsi model data untuk update

// Import Material Components yang mungkin dibutuhkan di Base (misal ProgressBar dialog)
import com.google.android.material.progressindicator.LinearProgressIndicator // ProgressBar Material


// =====================================================================
// BaseMapActivity (Abstract)
// =====================================================================

// Anotasi Hilt agar dependency bisa di-inject ke Activity ini
@AndroidEntryPoint
// Base Activity untuk Map (Umum untuk Google Maps dan MapLibre)
// Mengelola logika umum yang SAMA di kedua flavor (ViewModel, Binding, Permissions dasar, Notifikasi dasar)
// dan mendefinisikan kontrak (method abstract) untuk logika yang BEDA per flavor (inisialisasi map, dll).
abstract class BaseMapActivity : AppCompatActivity() {

    // Tag untuk logging di Base Activity
    private val TAG = "BaseMapActivity"

    // =====================================================================
    // Properti Umum (Di-inject atau Diinisialisasi di Base)
    // =====================================================================

    // View Binding untuk layout utama activity. Akses view melalui properti ini.
    // Akan diinisialisasi di onCreate. Protected agar bisa diakses di class turunan.
    protected lateinit var binding: ActivityMapBinding

    // ViewModel utama untuk berbagi data dan logika bisnis.
    // Di-inject oleh Hilt. Protected agar bisa diakses di class turunan.
    @Inject // Injeksi ViewModel. ViewModel harus disediakan Hilt.
    protected lateinit var viewModel: MainViewModel

    // Preference Manager untuk menyimpan setting.
    // Di-inject oleh Hilt. Protected agar bisa diakses di class turunan.
    @Inject // Injeksi PrefManager (pastikan PrefManager adalah class @Singleton dan disediakan Hilt)
    protected lateinit var prefManager: PrefManager // Gunakan PrefManager versi class @Singleton yang sudah kita revisi.

    // Helper untuk mengelola channel notifikasi dan ID.
    // Di-inject oleh Hilt. Protected agar bisa diakses di class turunan.
    @Inject // Injeksi NotificationsChannel (pastikan NotificationsChannel disediakan Hilt)
    protected lateinit var notificationsChannel: NotificationsChannel

    // Helper untuk mengelola permission.
    // Di-inject oleh Hilt. Protected agar bisa diakses di class turunan.
    // PermissionHelper butuh Context/Activity, pastikan disediakan Hilt di ActivityComponent.
    @Inject // <-- Injeksi PermissionHelper
    protected lateinit var permissionHelper: PermissionHelper

    // Helper untuk menampilkan dialog-dialog umum.
    // Di-inject oleh Hilt. Protected agar bisa diakses di class turunan.
    // DialogHelper butuh Context/Activity, pastikan disediakan Hilt di ActivityComponent.
    @Inject // <-- Injeksi DialogHelper
    protected lateinit var dialogHelper: DialogHelper

    // SearchHelper tidak di-inject di Base lagi, karena logic search di ViewModel/Repository
    // @Inject // <-- Injeksi SearchHelper
    // protected lateinit var searchHelper: SearchHelper

    // Request code untuk permission. Bisa dideklarasikan di Base.
    protected val PERMISSION_ID = 42

    // Variabel untuk menyimpan lat/lon yang sedang ditampilkan atau dipilih.
    // Di-update dari ViewModel atau interaksi map.
    protected var lat: Double = 0.0 // Default
    protected var lon: Double = 0.0 // Default

    // Dialog Download Progress (di-manage di Base karena UI dialognya sama)
    private var downloadProgressDialog: AlertDialog? = null
    // Dialog Xposed Missing (di-manage di Base karena UI dialognya sama)
    private var xposedDialog: AlertDialog? = null

    // Dialog Token Gojek (di-manage di Base karena UI dialognya sama, tapi konten dari parameter)
    private var gojekTokenDialog: AlertDialog? = null


    // =====================================================================
    // Metode Lifecycle Activity (Umum)
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // @AndroidEntryPoint memastikan injeksi Hilt selesai SEBELUM super.onCreate().
        super.onCreate(savedInstanceState)
        // Mengaktifkan Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false) // Perbaikan nama method
        // Menginisialisasi View Binding
        binding = ActivityMapBinding.inflate(layoutInflater) // Layout sama di kedua flavor
        setContentView(binding.root) // Set root view dari binding

        // Inisialisasi status awal lat/lon dari PrefManager
         lat = prefManager.getLat.value ?: 0.0 // Ambil nilai awal dari StateFlow/LiveData ViewModel
         lon = prefManager.getLng.value ?: 0.0 // Ambil nilai awal dari StateFlow/LiveData ViewModel


        // Inisialisasi map (method abstract, implementasi di class turunan)
        initializeMap()

        // Setup Nav Drawer (method umum)
        setupNavView()

        // Setup Button Listeners (method abstract, implementasi di class turunan)
        setupButtons()

        // Mengamati ViewModel untuk data umum atau perubahan status yang perlu ditangani di Base
        // Observasi StateFlow/LiveData DILAKUKAN DI SINI
        observeViewModelState()
        observeViewModelEvents()


        // Menangani Intent (jika Activity diluncurkan dengan data/action tertentu)
        handleIntent(intent)
    }

    // Tidak perlu override onStart, onResume, onPause, onStop, onSaveInstanceState, onLowMemory, onDestroy di Base
    // jika menggunakan SupportMapFragment di layout dan tidak ada logic spesifik Base.
    // FragmentManager akan mengelola lifecycle SupportMapFragment.

    // override fun onResume() {
    //     super.onResume()
         // Pastikan helper yang butuh lifecycle resume/pause didaftarkan/dilepas di class turunan MapActivity
         // atau pindahkan logic registrasi/deregistrasi helper yang butuh Context ke method yang tepat di class turunan
    // }

    // override fun onPause() {
    //     super.onPause()
         // notificationHelper.unregisterReceiver(this) // Contoh unregister jika didaftarkan di Base
    // }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update Intent Activity
        handleIntent(intent) // Panggil handler intent baru
    }

    // override fun onDestroy() {
    //     super.onDestroy()
         // Cleanup resources jika ada di Base
    // }


    // =====================================================================
    // Metode Umum (SAMA di Kedua Flavor)
    // Mengamati StateFlow/LiveData dari ViewModel
    // =====================================================================

    // Mengamati LiveData/StateFlow dari ViewModel untuk update UI umum atau status
    // LOGIKA KOLEKSI FLOW DARI VIEWMODEL ADA DI SINI!
    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Amati hanya saat Activity STARTED
                // Kumpulkan semua StateFlow/LiveData.collectLatest di dalam block ini

                // --- Mengamati State Hook Status & Error dari ViewModel ---
                 viewModel.isModuleHooked.collectLatest { isHooked ->
                     Log.d(TAG, "Xposed Hook Status updated: $isHooked")
                     // Logika menampilkan dialog Xposed missing berdasarkan status hooked DAN error
                     val shouldShowXposedDialog = !isHooked || viewModel.lastHookError.value != null
                     // Tidak panggil method abstract lagi, Base yang manage dialognya
                     if (shouldShowXposedDialog) showXposedDialog() else dismissXposedDialog()
                 }

                 viewModel.lastHookError.collectLatest { errorMsg ->
                      Log.e(TAG, "Xposed Hook Error updated: $errorMsg")
                     // Logika menampilkan dialog Xposed missing berdasarkan status hooked DAN error
                     val shouldShowXposedDialog = !viewModel.isModuleHooked.value || errorMsg != null
                     // Tidak panggil method abstract lagi, Base yang manage dialognya
                      if (shouldShowXposedDialog) showXposedDialog() else dismissXposedDialog()

                     // Opsi: Tampilkan pesan error di UI selain dialog Xposed
                     // if (errorMsg != null) showToast("Hook Error: $errorMsg") // Akan ditangani oleh showToastEvent
                 }

                // =====================================================================
                // Mengamati Token Gojek dari ViewModel (BARU)
                // =====================================================================
                 viewModel.gojekToken.collectLatest { token ->
                     Log.d(TAG, "Gojek Token updated (first 5 chars): ${token?.take(5)}...")
                     // Panggil method abstract di turunan untuk menampilkan token di UI (misal di dialog atau TextView)
                     // showGojekToken(token) // <-- Method abstract ini dihapus dari Base
                      // Jika UI Token dikelola di Base (misal di TextView)
                      // binding.textViewGojekToken.text = if (token != null) "Token: ${token.take(10)}..." else "Token: N/A"
                     // Jika ditampilkan di dialog yang dikelola di Base
                     // Saat token berubah, tidak otomatis tampilkan dialog, hanya simpan.
                     // Dialog hanya muncul saat menu diklik. Data diambil saat dialog muncul.
                 }

                // =====================================================================
                // Mengamati State & Progress Update (dari ViewModel/DownloadRepository)
                // =====================================================================
                // Mengamati informasi update yang tersedia
                 viewModel.updateInfo.collectLatest { updateInfo ->
                      Log.d(TAG, "Update Info State updated: $updateInfo")
                     if (updateInfo != null) {
                         showUpdateAvailableDialog(updateInfo) // <-- Panggil method abstract di turunan
                     } else {
                         // Dismiss dialog update utama jika info null (tidak ada update atau sudah di-clear)
                         // dialogHelper.dismissUpdateDialog() // Jika DialogHelper punya method ini
                     }
                 }

                 // Mengamati status dan progress download
                 viewModel.downloadState.collectLatest { state ->
                      Log.d(TAG, "Download State updated: $state")
                     // Update UI progress download (misal ProgressBar, Text status) berdasarkan state ini
                     when (state) {
                         is DownloadState.Idle -> {
                              downloadProgressDialog?.dismiss() // Sembunyikan dialog jika state Idle
                              downloadProgressDialog = null
                         }
                         is DownloadState.Downloading -> {
                             // Tampilkan dialog progress jika belum tampil
                              if (downloadProgressDialog == null || !downloadProgressDialog!!.isShowing) {
                                  downloadProgressDialog = dialogHelper.showDownloadProgressDialog(layoutInflater) { // Perbaikan: pass layoutInflater
                                       viewModel.cancelDownload() // Panggil ViewModel saat dialog di-cancel
                                  }
                             }
                             // Update progress di dialog
                             downloadProgressDialog?.let { dialogInstance ->
                                 val progressIndicator = dialogInstance.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                                 val progressText = dialogInstance.findViewById<TextView>(R.id.update_download_progress_text)
                                 if (progressIndicator != null && progressText != null) { // Cek null safety
                                     if (state.progress >= 0) {
                                         progressIndicator.isIndeterminate = false
                                         progressIndicator.progress = state.progress
                                         progressText.text = "${state.progress}%"
                                     } else {
                                         progressIndicator.isIndeterminate = true // Progress tidak diketahui (indeterminate)
                                         progressText.text = "Downloading..."
                                     }
                                 }
                             }
                         }
                         is DownloadState.Done -> {
                             downloadProgressDialog?.dismiss() // Sembunyikan dialog
                              downloadProgressDialog = null
                             // Trigger instalasi APK via SharedFlow event ViewModel
                             // ViewModel harus emit installAppEvent dengan Uri file APK yang di-download
                              // Ini akan ditangani oleh observeViewModelEvents()
                         }
                         is DownloadState.Failed -> {
                             downloadProgressDialog?.dismiss() // Sembunyikan dialog
                              downloadProgressDialog = null
                              // Error message bisa diambil dari state jika ada (sesuaikan DownloadState)
                              // viewModel.showToastEvent.emit(state.errorMessage ?: getString(R.string.bs_update_download_failed)) // Emit Toast via event
                         }
                          is DownloadState.Cancelled -> {
                             downloadProgressDialog?.dismiss() // Sembunyikan dialog
                             downloadProgressDialog = null
                              // viewModel.showToastEvent.emit("Download dibatalkan.") // Emit Toast via event
                         }
                     }
                 }

                 // =====================================================================
                 // Mengamati State & Progress Search (dari ViewModel/SearchRepository)
                 // ViewModel mengekspos searchResult StateFlow dari SearchRepository.
                 // =====================================================================
                 viewModel.searchResult.collectLatest { state ->
                     Log.d(TAG, "Search progress state updated: $state")
                     // Update UI search (misal ProgressBar, daftar hasil, pindah map) berdasarkan state ini
                     when(state) {
                          is SearchProgress.Idle -> {
                              binding.search.searchProgress.visibility = View.GONE
                               // dismissPartialSearchResultsDialog() // Asumsi dialog ini di-manage di Base
                          }
                          is SearchProgress.Progress -> {
                              binding.search.searchProgress.visibility = View.VISIBLE
                               // dismissPartialSearchResultsDialog() // Asumsi dialog ini di-manage di Base
                          }
                          is SearchProgress.Complete -> {
                              binding.search.searchProgress.visibility = View.GONE
                               // dismissPartialSearchResultsDialog() // Asumsi dialog ini di-manage di Base
                               val searchLocation = Location("search").apply {
                                   latitude = state.lat
                                   longitude = state.lon
                               }
                               // Update lat/lon di Base MapActivity
                               this@BaseMapActivity.lat = searchLocation.latitude
                               this@BaseMapActivity.lon = searchLocation.longitude
                               // Pindah map ke lokasi hasil search. Implementasi di turunan.
                               moveMapToNewLocation(searchLocation, true) // <-- Panggil method abstract di turunan
                                // Panggil ViewModel untuk menyimpan lokasi hasil search (jika user confirm)
                                // atau kalau hasil Complete dari SearchProgress selalu lokasi final yang dipilih
                               viewModel.setSearchedLocation(searchLocation) // ViewModel menyimpan hasil search
                                // TODO: Tampilkan daftar hasil search jika searchProgress juga punya list (saat ini hanya 1 hasil di Complete)
                          }
                         is SearchProgress.PartialResult -> {
                             binding.search.searchProgress.visibility = View.GONE
                              if (state.results.isNotEmpty()) {
                                   // Tampilkan dialog hasil parsial search. Implementasi di turunan atau di Base.
                                   // Jika DialogHelper menampilkan dialog, simpan referensi di sini jika perlu dismiss.
                                   // partialSearchResultsDialog = dialogHelper.showPartialSearchResultsDialog(
                                   dialogHelper.showPartialSearchResultsDialog( // Asumsi DialogHelper manage dialog life
                                       context = this@BaseMapActivity, // Gunakan context Activity Base
                                       results = state.results,
                                       onResultSelected = { selectedItem ->
                                           // User memilih salah satu hasil parsial
                                           val selectedLocation = Location("partial_search").apply {
                                                latitude = selectedItem.lat
                                                longitude = selectedItem.lon
                                           }
                                            // Update lat/lon di Base MapActivity
                                            this@BaseMapActivity.lat = selectedLocation.latitude
                                            this@BaseMapActivity.lon = selectedLocation.longitude
                                           // Pindah map ke lokasi terpilih
                                           moveMapToNewLocation(selectedLocation, true) // <-- Panggil method abstract di turunan
                                            // Panggil ViewModel untuk menyimpan lokasi terpilih
                                            viewModel.setSearchedLocation(selectedLocation)
                                            // Update text search bar dengan alamat yang dipilih
                                            binding.search.searchBox.setText(selectedItem.address)
                                            // Reset state search di ViewModel setelah terpilih
                                            viewModel.resetSearchState()
                                           // Dismiss dialog hasil parsial jika di-manage di sini
                                           // partialSearchResultsDialog?.dismiss()
                                       }
                                   )
                              } else {
                                  // Tidak ada hasil parsial, reset state dan beri feedback
                                  viewModel.resetSearchState()
                                  viewModel.showToastEvent.emit(getString(R.string.address_not_found)) // Emit Toast via event
                              }

                         }
                         is SearchProgress.NoResultFound -> {
                              binding.search.searchProgress.visibility = View.GONE
                               // dismissPartialSearchResultsDialog() // Asumsi dialog ini di-manage di Base
                              viewModel.showToastEvent.emit(getString(R.string.address_not_found)) // Emit Toast via event
                         }
                        is SearchProgress.Fail -> {
                            binding.search.searchProgress.visibility = View.GONE
                             // dismissPartialSearchResultsDialog() // Asumsi dialog ini di-manage di Base
                             viewModel.showToastEvent.emit(state.error ?: getString(R.string.address_not_found)) // Emit Toast via event
                             Timber.e("Search failed: ${state.error}")
                        }
                    }
                 }


                // --- Mengamati State Utama (dari PrefManager/ViewModel) ---
                // Contoh: Mengamati isStarted status dari ViewModel (yang diambil dari PrefManager)
                 viewModel.isStarted.collectLatest { isStarted ->
                      Log.d(TAG, "isStarted status updated: $isStarted")
                     // Update UI umum di Base atau panggil method abstract di turunan jika perlu berdasarkan status START
                     // Contoh: ubah warna tombol START/STOP di toolbar jika ada di Base
                      // Method abstract untuk update UI tombol Start/Stop bisa ditambahkan jika perlu
                      // updateStartStopButtonUI(isStarted) // <-- Method abstract opsional
                 }

                // Mengamati lat/lon dari ViewModel (dari PrefManager)
                 viewModel.getLat.collectLatest { latitude ->
                      Log.d(TAG, "Latitude updated: $latitude")
                     this@BaseMapActivity.lat = latitude // Update properti lat di Base
                     // Mungkin perlu memicu update UI yang tergantung lat di sini,
                     // tapi biasanya update UI map tergantung pada panggilan moveMapToNewLocation.
                 }

                 viewModel.getLng.collectLatest { longitude ->
                      Log.d(TAG, "Longitude updated: $longitude")
                     this@BaseMapActivity.lon = longitude // Update properti lon di Base
                     // Mungkin perlu memicu update UI yang tergantung lon di sini
                 }

                // Mengamati mapType dari ViewModel (dari PrefManager)
                // viewModel.mapType.collectLatest { type -> /* Update UI map type */ }


            } // Akhir repeatOnLifecycle
        }
    }


    // Method untuk mengumpulkan semua observer SharedFlow events dari ViewModel
    // LOGIKA KOLEKSI FLOW DARI VIEWMODEL ADA DI SINI!
    private fun observeViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Kumpulkan semua SharedFlow.collectLatest di dalam block ini

                // --- Mengamati Event Toast dari ViewModel ---
                viewModel.showToastEvent.collectLatest { message ->
                    Log.d(TAG, "Show Toast Event: $message")
                    showToast(message) // Menggunakan extension function Activity
                }

                // --- Mengamati Event Install APK dari ViewModel ---
                viewModel.installAppEvent.collectLatest { fileUri ->
                    Log.d(TAG, "Install app event received with URI: $fileUri")
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        // putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true) // Mungkin butuh permission REQUEST_INSTALL_PACKAGES
                    }
                    if (packageManager.resolveActivity(installIntent, 0) != null) {
                         runCatching {
                              startActivity(installIntent)
                         }.onFailure { error ->
                             Log.e(TAG, "Failed to start package installer activity", error)
                              // showToast("Failed to open package installer.") // Akan ditangani oleh showToastEvent dari ViewModel
                         }
                    } else {
                         Log.e(TAG, "No activity found to handle package install Intent")
                         // showToast("No app found to install package.") // Akan ditangani oleh showToastEvent dari ViewModel
                    }
                }

                // --- Mengamati Event Tampilkan/Sembunyikan Dialog Xposed dari ViewModel ---
                // ViewModel emit Boolean, Base Activity menangani dialognya.
                 viewModel.showXposedDialogEvent.collectLatest { isShow ->
                     Log.d(TAG, "Show Xposed dialog event received: $isShow")
                     // Base Activity yang mengelola dialog Xposed Missing.
                     // Method abstract showXposedMissingDialog di turunan sudah dihapus.
                     if (isShow) showXposedDialog() else dismissXposedDialog()
                 }
            }
        }
    }


    // Menangani Intent saat Activity diluncurkan atau ada new Intent
    protected open fun handleIntent(intent: Intent?) {
         Log.d(TAG, "handleIntent() called with action: ${intent?.action}")
        // Default implementation (do nothing), bisa di-override oleh class turunan
        // Berguna jika Activity bisa diluncurkan dengan action/data tertentu (misal dari notifikasi STOP)
        // Contoh:
         if (intent?.action == NotificationsChannel.ACTION_STOP) {
             Log.d(TAG, "Received STOP action intent. Performing stop button click.")
            // Panggil logic stop (method abstract) jika Intent action STOP diterima
             onStopButtonClicked() // <-- Memanggil method abstract di turunan
             // showToast("Fake GPS Dihentikan") // Opsional feedback UI, bisa di ViewModel emit Toast event
         }
    }


    // Setup Navigation Drawer (Umum)
    private fun setupNavView() {
         Log.d(TAG, "Setting up Navigation View.")
        // Logic setup Navigation Drawer dan listener item menunya
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_fav -> {
                    showFavoriteListDialog() // <-- Panggil method abstract di turunan
                }
                R.id.nav_about -> {
                    showAboutDialog() // <-- Panggil method abstract di turunan
                }
                R.id.nav_update -> {
                    // Pemicu cek update DARI ViewModel
                    viewModel.checkForUpdates() // <-- Panggil method di ViewModel (yang delegasi ke Repo)
                    // Hasil cek update akan diamati dari viewModel.updateInfo StateFlow
                }
                 R.id.nav_clear_log -> { // Contoh menu item Clear Log
                    FileLogger.clearAllLogs(this) // Panggil method static di FileLogger
                     // showToast("Log files cleared") // Akan ditangani oleh showToastEvent dari ViewModel jika ViewModel emit event setelah ini
                 }
                 R.id.nav_view_log_folder -> { // Contoh menu item View Log Folder
                     val logPath = FileLogger.getLogFolderPath(this)
                     // showToast("Log path: $logPath") // Akan ditangani oleh showToastEvent dari ViewModel jika ViewModel emit event
                 }
                 // TODO: Tambahkan menu item lain jika ada (misal menu untuk menampilkan token Gojek)
                 R.id.nav_gojek_token -> { // Contoh menu item Tampilkan Token Gojek
                      Log.d(TAG, "Gojek Token menu item clicked.")
                     // Panggil method abstract di turunan untuk menampilkan dialog token Gojek
                     // Data token diambil dari viewModel.gojekToken.value
                     // showGojekTokenDialog(viewModel.gojekToken.value) // Method abstract ini dihapus dari Base
                     // Panggil dialogHelper langsung dari Base karena dialog dikelola di sini
                     showGojekTokenDialog(viewModel.gojekToken.value) // <-- Panggil method umum di Base
                 }
                 R.id.nav_exit -> { // Contoh menu item Exit aplikasi
                      finishAndRemoveTask() // Menutup Activity dan menghapus task
                 }
            }
            // Tutup drawer setelah item diklik
            binding.drawerLayout.closeDrawer(binding.navView)
            true
        }

        // Setup toggle button untuk membuka/menutup drawer (jika menggunakan ActionBarDrawerToggle)
        // Setup listener tombol menu custom di toolbar/UI jika ada
        binding.toolbar.menuButton.setOnClickListener { // Asumsi id tombol menu di toolbar adalah menuButton
            binding.drawerLayout.openDrawer(binding.navView) // Buka drawer
        }

        // Setup search bar listener (jika ada search bar di layout)
        binding.search.searchBox.setOnEditorActionListener { v, actionId, event -> // Asumsi id EditText search di toolbar adalah searchBox
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                if (query.isNotEmpty()) {
                    // Panggil ViewModel untuk memulai search
                     viewModel.searchAddress(query) // <-- Panggil method search di ViewModel (yang delegasi ke Repo/Helper)
                     // Hasil search akan diamati dari viewModel.searchResult StateFlow
                 }
                 // Sembunyikan keyboard setelah search
                 val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                 imm.hideSoftInputFromWindow(v.windowToken, 0)

                true // Mengonsumsi event
            } else {
                false // Tidak mengonsumsi event
            }
        }
        // MapActivity ini akan mengamati Flow searchResult dari ViewModel di observeViewModelState().
    }

    // Implementasi onRequestPermissionsResult di Base jika Base mengelola permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
         Log.d(TAG, "onRequestPermissionsResult received for requestCode: $requestCode")
        // Delegasikan hasil permission ke PermissionHelper
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults,
            onLocationPermissionGranted = {
                Log.d(TAG, "Location permission granted callback from PermissionHelper.")
                // Callback jika permission lokasi diberikan
                // Panggil method abstract atau ViewModel untuk melanjutkan proses lokasi (misal getLastLocation)
                // Setelah permission diberikan, baru coba ambil lokasi terakhir
                getLastLocation() // <-- Memanggil method abstract di turunan untuk dapat lokasi (mungkin akan panggil LocationHelper)
            },
            onLocationPermissionDenied = {
                Log.d(TAG, "Location permission denied callback from PermissionHelper.")
                // showToast("Location permission denied. Features may be limited.") // Akan ditangani oleh showToastEvent dari ViewModel
                // Opsi: Tampilkan dialog edukasi via DialogHelper
            }
            // Callback lain jika PermissionHelper menangani permission selain lokasi
            // Misal: onPermissionsResult (Map<String, Boolean>) jika PermissionHelper menangani multiple non-lokasi permission request
        )
    }


    // =====================================================================
    // Metode Umum untuk Mengelola Dialog (Dikendalikan dari Observasi ViewModel di Base)
    // =====================================================================
    // Menampilkan dialog Xposed Missing (Dipanggil dari observeViewModelState)
    private fun showXposedDialog() {
         if (xposedDialog == null || !xposedDialog!!.isShowing) {
             Log.d(TAG, "Showing Xposed Missing Dialog.")
             xposedDialog = dialogHelper.showXposedMissingDialog(this) // Gunakan context Activity Base
         }
    }

    // Menyembunyikan dialog Xposed Missing (Dipanggil dari observeViewModelState)
    private fun dismissXposedDialog() {
         Log.d(TAG, "Dismissing Xposed Missing Dialog.")
         xposedDialog?.dismiss()
         xposedDialog = null
    }

    // Menampilkan dialog Token Gojek (Dipanggil dari Nav Drawer menu item)
    private fun showGojekTokenDialog(token: String?) {
         Log.d(TAG, "Showing Gojek Token Dialog.")
         // Dismiss dialog lama jika ada
         gojekTokenDialog?.dismiss()
         // Buat dan tampilkan dialog baru menggunakan DialogHelper
         gojekTokenDialog = dialogHelper.showGojekTokenDialog(this, token) // Gunakan context Activity Base
    }


    // =====================================================================
    // Metode Abstract (Harus Diimplementasikan di Class Turunan)
    // Dipanggil dari Base Activity saat ViewModel state/event berubah atau UI berinteraksi.
    // Menggunakan Inject Helper/ViewModel di implementasinya
    // =====================================================================

    // Metode terkait Map UI (Spesifik per Library Map)
    abstract fun initializeMap() // Inisialisasi Map (GoogleMap vs MapLibreMap)
    abstract fun hasMarker(): Boolean // Cek apakah ada marker (Google Marker vs MapLibre Marker)
    // moveMapToNewLocation: Dipanggil dari Base (saat search complete, dll) atau dari turunan (saat klik map, dll).
    // Menerima Location standar Android, implementasi di turunan akan konversi ke LatLng spesifik flavor.
    abstract fun moveMapToNewLocation(location: Location, moveNewLocation: Boolean = true) // Gerakkan kamera map ke lokasi baru


    // Metode terkait UI/Button (Setup listener spesifik UI di Activity turunan)
    abstract fun setupButtons() // Menyiapkan listener untuk tombol-tombol UI (misal START/STOP, GetLocation)

    // Metode terkait Aksi User (Logika setelah tombol Stop diklik)
    // Dipanggil dari handleIntent() atau dari setupButtons() di class turunan (via performStopButtonClick).
    // Implementasi di turunan akan update ViewModel state.
    abstract fun onStopButtonClicked() // Logic saat tombol Stop diklik (akan update ViewModel state)

    // Metode terkait Dialogs (Logika menampilkan dialog spesifik UI/Konten)
    // Implementasi di MapActivity turunan akan memanggil DialogHelper yang sudah di-inject di Base.
    // Perlu pass LayoutInflater ke method DialogHelper jika dialog butuh custom layout.
    abstract fun addFavoriteDialog() // Tampilkan dialog tambah favorit (akan panggil dialogHelper.showAddFavoriteDialog(layoutInflater, ...))
    abstract fun showFavoriteListDialog() // Tampilkan dialog daftar favorit (akan panggil dialogHelper.showFavoriteListDialog(this, ...))
    abstract fun showAboutDialog() // Tampilkan dialog about (akan panggil dialogHelper.showAboutDialog(this, ...))
    abstract fun showUpdateAvailableDialog(updateInfo: YourUpdateModel) // Tampilkan dialog update (terima data update dari ViewModel, panggil dialogHelper.showUpdateDialog(this, ...))
    // Method abstract showXposedMissingDialog(isShow: Boolean) DIHAPUS karena dialog Xposed di-manage di Base.
    // Method abstract showGojekToken(token: String?) DIHAPUS karena dialog Token Gojek di-manage di Base.


    // Metode terkait Lokasi (Logika mendapatkan lokasi awal/baru dan permission)
    // Implementasi di MapActivity turunan akan memanggil ILocationHelper yang di-inject di turunan.
    @SuppressLint("MissingPermission") // Suppress karena permission cek ada di implementasi turunan/Helper
    abstract fun getLastLocation() // Minta lokasi terakhir atau update baru (akan panggil locationHelper.getLastLocation(...) atau locationHelper.requestLocationUpdates(...))

    // Metode terkait Error Handling (Menampilkan UI error lokasi)
    // Implementasi di MapActivity turunan akan menampilkan UI error (misal Snackbar).
    // Dipanggil dari LocationListener.onLocationError di MapActivity turunan atau dari ViewModel.
    abstract fun handleLocationError() // Tampilkan UI error terkait lokasi (misal Snackbar Enable Location)

    // Metode terkait Notifikasi (Menampilkan/Membatalkan notifikasi)
    // Implementasi di MapActivity turunan akan memanggil NotificationHelper yang sudah di-inject di Base.
    abstract fun showStartNotification(address: String) // Tampilkan notifikasi start (akan panggil notificationsChannel.showNotification(...))
    abstract fun cancelNotification() // Batalkan notifikasi (akan panggil notificationsChannel.cancelNotification(...))

    // Opsi: Method Abstract lain jika ada logic umum yang perlu di-override per flavor.

}
