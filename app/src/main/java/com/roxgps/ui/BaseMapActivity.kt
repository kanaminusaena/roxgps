package com.roxgps.ui

// --- IMPORTS YANG DIBUTUHKAN OLEH KODE YANG TERSISA DI ACTIVITY ---
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location // Masih perlu untuk tipe data di listener
import android.os.Bundle
import android.os.Looper // Mungkin masih perlu
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText // Jika custom dialog/searchbar XML di-inflate di sini
import android.widget.TextView // Jika custom dialog XML di-inflate di sini
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton // Jika custom dialog XML di-inflate di sini
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager // Jika layout manager dibikin di sini
import androidx.recyclerview.widget.RecyclerView // Jika custom dialog XML di-inflate di sini
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Jika dialog custom UI dibikin di sini
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.progressindicator.LinearProgressIndicator // Jika custom dialog XML di-inflate di sini
import dagger.hilt.android.AndroidEntryPoint
import com.roxgps.BuildConfig
import com.roxgps.R // Penting untuk resources
// Import adapter dan model jika masih dipake di listener dialog atau di Activity langsung
import com.roxgps.adapter.FavListAdapter // Jika adapter dipegang di Activity
import com.roxgps.data.model.Favorite // Jika model Favorite dipegang/dipakai di Activity
import com.roxgps.databinding.ActivityMapBinding // View Binding
import com.roxgps.ui.viewmodel.MainViewModel // ViewModel
import com.roxgps.utils.JoystickService // Jika service di-start dari sini
import com.roxgps.utils.PrefManager // Jika dipakai di sini
import com.roxgps.utils.ext.* // Jika ada extension functions yang tersisa dan dipakai
// --- IMPORTS HELPER DAN UTILITY BARU ---
import com.roxgps.helper.LocationHelper
import com.roxgps.helper.LocationListener // Listener untuk LocationHelper
import com.roxgps.helper.DialogHelper
import com.roxgps.helper.NotificationHelper
import com.roxgps.helper.SearchHelper
import com.roxgps.helper.SearchProgress // Sealed class dari SearchHelper
import com.roxgps.utils.NetworkUtils // Utility untuk cek koneksi
import com.roxgps.utils.NotificationsChannel // Objek singleton channel notifikasi
// ... import lain ...
import kotlinx.coroutines.* // Import coroutines
import kotlinx.coroutines.channels.awaitClose // Mungkin masih perlu kalau ada callbackFlow di sisa kode
import kotlinx.coroutines.flow.callbackFlow // Mungkin masih perlu kalau ada callbackFlow di sisa kode
import kotlinx.coroutines.flow.collect // Untuk collect Flow
import kotlinx.coroutines.flow.Flow // Untuk tipe data Flow
import java.io.IOException // Untuk exception handling
import java.util.regex.Matcher // Jika regex masih dipakai di sisa kode
import java.util.regex.Pattern // Jika regex masih dipakai di sisa kode
import kotlin.properties.Delegates // Untuk Delegated Properties (by notNull, by lazy)
// ... import notifikasi dan snackbar ...
import androidx.core.app.NotificationManagerCompat
import com.roxgps.receiver.NotificationActionReceiver // Receiver class (meskipun objeknya di helper)
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import com.google.android.material.snackbar.Snackbar // Untuk Snackbar
import android.app.PendingIntent // Untuk PendingIntent

// Catatan: Pastikan semua import yang dibutuhkan oleh kode di bawah ini sudah tercantum.
// Import tambahan mungkin diperlukan tergantung implementasi detail helper atau fungsi yang tersisa.


@AndroidEntryPoint // Anotasi Hilt
// --- IMPLEMENT INTERFACE LISTENER DARI HELPER YANG DIPAKAI ---
abstract class BaseMapActivity: AppCompatActivity(), LocationListener { // Implement LocationListener
// Implement listener lain jika ada helper lain yang butuh komunikasi balik pake listener
// , SomeOtherHelper.SomeOtherListener
// ------------------------------------------------------------

    // --- PROPERTI-PROPERTI YANG MASIH DI ACTIVITY (UI STATE, VIEW MODEL, BINDING, DSB) ---
    protected var lat by Delegates.notNull<Double>() // State lat/lon (bisa dipertimbangkan ke ViewModel)
    protected var lon by Delegates.notNull<Double>() // State lat/lon (bisa dipertimbangkan ke ViewModel)
    protected val viewModel by viewModels<MainViewModel>() // ViewModel (penting)
    protected val binding by lazy { ActivityMapBinding.inflate(layoutInflater) } // View Binding (penting)
    protected val update by lazy { viewModel.getAvailableUpdate() } // Data update (observable dari ViewModel)

    private val notificationsChannel = NotificationsChannel // Objek Singleton Channel Notifikasi (tetap di sini)
    private var favListAdapter: FavListAdapter = FavListAdapter() // Adapter untuk list favorit (dipegang di Activity)
    private var xposedDialog: AlertDialog? = null // Referensi dialog Xposed (untuk dismiss)

    private val elevationOverlayProvider by lazy { ElevationOverlayProvider(this) } // Utility Material Design
    private val headerBackground by lazy { // Utility Material Design
        elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(
            resources.getDimension(R.dimen.bottom_sheet_elevation)
        )
    }

    // --- INSTANSIASI HELPER-HELPER YANG BARU DIBIKIN ---
    // Dideklarasi di sini, diinisialisasi di onCreate()
    private lateinit var locationHelper: LocationHelper
    private lateinit var dialogHelper: DialogHelper
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var searchHelper: SearchHelper
    // Jika ada helper lain, tambahkan di sini
    // private lateinit var someOtherHelper: SomeOtherHelper
    // ----------------------------------------------------

    // --- BROADCAST RECEIVER SUDAH PINDAH KE NOTIFICATIONHELPER ---
    // private val stopActionReceiver = object : BroadcastReceiver() { ... } // DIHAPUS
    // ----------------------------------------------------------

    // --- FUNGSI YANG DIPANGGIL OLEH LAMBDA DARI NOTIFICATIONHELPER ---
    // Ini adalah aksi yang dilakukan Activity saat tombol stop notifikasi diklik
    // Visibility diubah jadi public/internal agar bisa diakses lambda di helper
    fun performStopButtonClick() {
        binding.stopButton.performClick() // Masih simulasi klik tombol UI. Bisa diganti panggil stopProcessingLogic()
        // Saran: Identifikasi logic inti "stop", bikin fungsi private, panggil fungsi itu di sini & di listener klik tombol UI
        // stopProcessingLogic()
    }

    protected abstract fun hasMarker(): Boolean // Dipakai di addFavoriteAction (cek apakah ada marker di map)
    protected abstract fun initializeMap() // Inisialisasi Map (implementasi spesifik Map API)
    protected abstract fun setupButtons() // Setup Tombol (implementasi spesifik UI/logic tombol)
    protected abstract fun moveMapToNewLocation(moveNewLocation: Boolean) // Pindah Map ke koordinat (implementasi spesifik Map API)
    // ------------------------------------------------------------
    // --- FUNGSI ABSTRACT BARU UNTUK LOGIKA INTI STOP PROSES ---
    // Implementasi akan ada di subclass (MapActivity)
    protected abstract fun stopProcess()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup Edge-to-Edge dan Window Flags
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Panggil setContentView LANGSUNG, tidak di coroutine
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)

        // --- INISIALISASI SEMUA HELPER SETELAH setContentView ---
        locationHelper = LocationHelper(this, this) // Context, LocationListener (Activity itu sendiri)
        dialogHelper = DialogHelper(this, layoutInflater) // Context, LayoutInflater
        // Context, objek Channel Notifikasi, dan lambda aksi Stop
        // Lambda ini sekarang memanggil fungsi abstract stopProcess()
        notificationHelper = NotificationHelper(this, notificationsChannel) {
            stopProcess() // <-- Panggil fungsi abstract stopProcess()
        }
        searchHelper = SearchHelper(this) // Context
        // Inisialisasi helper lain jika ada
        // someOtherHelper = SomeOtherHelper(this, ...)
        // ------------------------------------------------------

        // --- PANGGILAN FUNGSI-FUNGSI SETUP AWAL ACTIVITY ---
        initializeMap() // Fungsi abstract - implementasi di subclass
        checkModuleEnabled() // Cek Xposed (panggil dialogHelper di dalamnya)
        checkUpdates() // Cek Update (panggil dialogHelper di dalamnya)
        setupNavView() // Setup NavView Listeners (panggil searchHelper & dialogHelper di dalamnya)
        setupButtons() // Fungsi abstract - implementasi di subclass
        setupDrawer() // Setup Drawer Toggle
        checkNotifPermission() // Cek Permission Notifikasi (masih di sini, perlu review pake DialogHelper?)

        // Cek PrefManager dan start Service (Utility? Bisa dipindah?)
        if (PrefManager.isJoystickEnabled){
            startService(Intent(this, JoystickService::class.java))
        }
        // --- REGISTER RECEIVER VIA HELPER ---
        notificationHelper.registerReceiver()
        // ----------------------------------
        // --- PANGGIL FUNGSI-FUNGSI OBSERVASI DATA DARI VIEWMODEL ---
        // Pastikan fungsi-fungsi ini dipanggil agar UI bereaksi terhadap perubahan data
        observeFavoriteResponse() // Observasi hasil simpan favorit
        // checkUpdates() juga mengandung observasi viewModel.update
        // getAllUpdatedFavList() // Jika perlu load awal dan observasi list favorit
        lifecycleScope.launch { // Contoh panggil load awal list favorit jika perlu
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){ // Collect safely
                 viewModel.doGetUserDetails() // Load data awal favorit (jika belum di ViewModel init)
                 viewModel.allFavList.collect { // Observasi list favorit
                    favListAdapter.submitList(it) // Update adapter list favorit (UI)
                 }
            }
        }
        // --------------------------------------------------------
        // Optional: Panggil request lokasi awal pas onCreate/onResume
        // locationHelper.requestLastKnownLocation()
    }

    // --- FUNGSI-FUNGSI SETUP UI ACTIVITY YANG TERSISA ---

    // Cek Permission Notifikasi (masih di sini, perlu review pake DialogHelper?)
    // Logic ini mencakup cek permission dan menampilkan dialog penjelasan jika perlu.
    // Dialog penjelasan sebaiknya menggunakan DialogHelper.
    private fun checkNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    // PERMISSION_ID // ID ini hanya untuk onRequestPermissionsResult (sudah dihapus untuk lokasi), bisa pake ID lain atau handle pake Activity Result API kalau mau
                    100 // Contoh ID permission notifikasi baru
                )
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            // --- GUNAKAN DIALOGHELPER UNTUK MENAMPILKAN DIALOG INI ---
            // Ganti kode MaterialAlertDialogBuilder(...) dengan panggilan ke dialogHelper
            dialogHelper.showAlertDialog( // Contoh pemanggilan fungsi di DialogHelper (perlu diimplement di DialogHelper)
                title = "Enable Notifications", // Hardcoded string? Sebaiknya dari R.string
                message = "This app requires notifications for optimal functionality. Please enable notifications in the settings.", // Hardcoded string? Sebaiknya dari R.string
                positiveButtonText = "Open Settings", // Hardcoded string? Sebaiknya dari R.string
                onPositiveButtonClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                },
                negativeButtonText = "Done", // Hardcoded string? Sebaiknya dari R.string
                onNegativeButtonClick = null // Null listener
            )
            // ----------------------------------------------------
        }
    }

    // Setup Drawer Toggle (masih di sini, ngurusin interaksi DrawerLayout sama Toolbar)
    private fun setupDrawer() {
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val mDrawerToggle = object : ActionBarDrawerToggle(
            this,
            binding.container,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
                invalidateOptionsMenu() // Refresh menu (kalau ada menu di toolbar)
            }

            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                invalidateOptionsMenu() // Refresh menu
            }
        }
        // Metode setDrawerListener sudah deprecated, gunakan addDrawerListener
        // binding.container.setDrawerListener(mDrawerToggle)
        binding.container.addDrawerListener(mDrawerToggle)
        mDrawerToggle.syncState() // Sinkronkan status toggle dengan drawer
    }

    // Setup NavView Listeners (masih di sini, ngurusin interaksi di dalam NavigationView)
    // Termasuk Search Bar Listener (panggil SearchHelper) dan Item Menu Listener (panggil DialogHelper/start Activity)
    private fun setupNavView() {
        // --- LOGIC INSETS (TETAP DI SINI) ---
        ViewCompat.setOnApplyWindowInsetsListener(binding.mapContainer.map) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navView.updatePadding(
                top = systemBarsInsets.top,
                bottom = systemBarsInsets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
        // ----------------------------------

        // --- LOGIC SEARCH BAR LISTENER (MEMANGGIL SEARCHHELPER DAN UPDATE UI) ---
        val progress = binding.search.searchProgress // Referensi ke UI progress
        binding.search.searchBox.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Panggil Utility cek koneksi
                if (NetworkUtils.isNetworkConnected(this)) { // Gunakan NetworkUtils
                    lifecycleScope.launch(Dispatchers.Main) { // Launch coroutine di Main thread
                        val getInput = v.text.toString()
                        if (getInput.isNotEmpty()){
                            // Panggil fungsi search dari SearchHelper dan collect Flow hasilnya
                            searchHelper.getSearchAddress(getInput)
                                .collect { result ->
                                    // Update UI atau State berdasarkan hasil dari helper
                                    when(result) {
                                        is SearchProgress.Progress -> {
                                            progress.visibility = View.VISIBLE
                                        }
                                        is SearchProgress.Complete -> {
                                            progress.visibility = View.GONE
                                            lat = result.lat // Update state lat/lon di Activity
                                            lon = result.lon // Update state lat/lon di Activity
                                            moveMapToNewLocation(true) // Panggil fungsi abstract update map
                                        }
                                        is SearchProgress.Fail -> {
                                            progress.visibility = View.GONE
                                            showToast(result.error!!) // Tampilkan Toast error
                                        }
                                    }
                                }
                        }
                    }
                } else {
                    showToast(getString(R.string.no_internet)) // Tampilkan Toast no internet
                }
                return@setOnEditorActionListener true // Consume event
            }
            return@setOnEditorActionListener false // Tidak consume event
        }
        // -----------------------------------------------------------------------


        // --- LOGIC NAVIGATION ITEM LISTENER (MEMANGGIL DIALOGHELPER ATAU START ACTIVITY) ---
        binding.navView.setNavigationItemSelectedListener {
            when(it.itemId){
                R.id.get_favorite -> {
                    // Panggil fungsi show dialog favorit dari DialogHelper
                    // Logic yang dilakukan setelah item dipilih atau dihapus ada di dalam lambda
                     dialogHelper.showFavoriteListDialog(
                         favList = viewModel.allFavList, // Pass data Flow favorit dari ViewModel
                         onItemClick = { favorite ->
                             // Logic saat item favorit diklik di dialog: update state dan update map
                             lat = favorite.lat!!
                             lon = favorite.lng!!
                             moveMapToNewLocation(true)
                             // Dialog dismissal bisa dihandle di dalam DialogHelper atau di sini
                             // Jika DialogHelper mengembalikan instance dialog, bisa didismiss di sini
                             // dialogInstanceDariFavList?.dismiss()
                         },
                         onItemDelete = { favorite ->
                             // Logic saat tombol delete di item favorit diklik: panggil ViewModel
                             viewModel.deleteFavorite(favorite)
                         }
                         // Jika DialogHelper perlu lifecycleScope buat collect Flow, pass di sini
                         // lifecycleScope = lifecycleScope
                     )
                    true // Consume event klik
                }
                R.id.settings -> {
                    // Start Activity Settings
                    startActivity(Intent(this,ActivitySettings::class.java))
                    true // Consume event klik
                }
                R.id.about -> {
                    // Panggil fungsi show about dialog dari DialogHelper
                    dialogHelper.showAboutDialog()
                    true // Consume event klik
                }
                else -> false // Untuk item menu lain yang tidak dihandle
            }
            binding.container.closeDrawer(GravityCompat.START) // Tutup drawer setelah klik item
            true // Return true untuk menandakan event sudah dihandle
        }
        // ----------------------------------------------------------------------------------
    }

    // Cek Xposed Module Enabled (masih di sini, panggil dialogHelper di dalamnya)
    // Logic ini mengamati state Xposed dari ViewModel dan menampilkan dialog jika modul tidak aktif.
    private fun checkModuleEnabled(){
        viewModel.isXposed.observe(this) { isXposed ->
            if (!isXposed) {
                xposedDialog?.dismiss() // Dismiss dialog sebelumnya jika ada
                // --- GUNAKAN DIALOGHELPER UNTUK MENAMPILKAN DIALOG INI ---
                // Ganti kode MaterialAlertDialogBuilder(...) dengan panggilan ke dialogHelper
                xposedDialog = dialogHelper.showAlertDialog( // Contoh pemanggilan fungsi di DialogHelper (perlu diimplement di DialogHelper)
                     title = getString(R.string.error_xposed_module_missing), // Gunakan string resources
                     message = getString(R.string.error_xposed_module_missing_desc), // Gunakan string resources
                     positiveButtonText = "OK", // Tombol OK
                     onPositiveButtonClick = null,
                     isCancelable = true // Bisa dicancel
                     // Helper harus mengembalikan instance AlertDialog agar bisa disimpan di xposedDialog
                 )
                // ----------------------------------------------------
            } else {
                 xposedDialog?.dismiss() // Dismiss dialog jika modul aktif
            }
        }
    }

    // Lifecycle onResume
    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState() // Update state Xposed di ViewModel
        checkNotifPermission() // Cek Permission Notifikasi (dipanggil lagi setiap resume)
        // Optional: Panggil request lokasi di resume
        // locationHelper.requestLastKnownLocation()
    }

    // Lifecycle onDestroy (Cleanup)
    override fun onDestroy() {
        super.onDestroy()
        // --- UNREGISTER RECEIVER VIA HELPER ---
        notificationHelper.unregisterReceiver()
        // ------------------------------------

        // --- STOP LOCATION UPDATES VIA HELPER ---
        locationHelper.stopLocationUpdates()
        // --------------------------------------

        // Dismiss Xposed dialog kalau masih tampil saat Activity hancur
        xposedDialog?.dismiss()

        // Cleanup resources lain jika ada
        // Tidak perlu dismiss dialog yang dibuat lokal di dalam fungsi, GC akan urus
    }

    // --- IMPLEMENTASI INTERFACE LOCATIONLISTENER DARI LOCATIONHELPER ---
    // Kode ini dijalankan saat LocationHelper berhasil mendapatkan lokasi
    override fun onLocationResult(location: Location) {
        lat = location.latitude // Update state lat di Activity
        lon = location.longitude // Update state lon di Activity
        moveMapToNewLocation(true) // Panggil fungsi abstract untuk update map
        // println("Lokasi diperbarui dari helper: Latitude = $lat, Longitude = $lon") // Komen debugging
        // showToast("Lokasi diperbarui!") // Contoh: Tampilkan Toast
    }

    // Kode ini dijalankan saat LocationHelper melaporkan error lokasi
    override fun onLocationError(message: String) {
        // Panggil fungsi UI error handling (tetap di Activity)
        handleLocationError() // Menampilkan Snackbar error lokasi
        // Opsional: Tampilkan pesan error dari helper
        // showToast("Error Lokasi: $message") // Membutuhkan fungsi showToast
    }

    // Kode ini dijalankan saat izin lokasi diberikan setelah diminta
    override fun onPermissionGranted() {
        showToast("Izin lokasi diberikan!") // Tampilkan Toast
        // Optional: Langsung coba request lokasi lagi setelah izin dikasih
        // locationHelper.requestLastKnownLocation()
    }

    // Kode ini dijalankan saat izin lokasi ditolak oleh pengguna
    override fun onPermissionDenied() {
        showToast("Izin lokasi ditolak. Fitur lokasi tidak dapat digunakan.") // Tampilkan Toast
        // Opsional: Disable tombol atau fitur yang butuh lokasi
    }
    // ----------------------------------------------------------------


    // --- FUNGSI UI ERROR HANDLING (Dipanggil dari LocationListener) ---
    // Fungsi ini tetap di Activity karena dia menampilkan UI (Snackbar)
    private fun handleLocationError() {
         Snackbar.make(binding.root, "Layanan lokasi dinonaktifkan.", Snackbar.LENGTH_LONG) // Gunakan binding.root untuk view
            .setAction("Aktifkan") {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .show()
    }
    // ---------------------------------------------------------------

    // --- LOGIC YANG DIPICU DARI CALLBACK DIALOGHELPER ATAU LISTENER LAIN ---

    // Logic Add Favorite (Dipanggil dari lambda DialogHelper.showAddFavoriteDialog)
     protected fun addFavoriteAction() { // Nama fungsi diubah biar lebih jelas ini aksi
         // Panggil dialogHelper untuk menampilkan dialog input nama favorit
         // Pass lambda yang berisi logic setelah input diterima
         dialogHelper.showAddFavoriteDialog { inputText ->
             // Logic yang dijalankan saat tombol "Add" di dialog diklik
             // Cek hasMarker() (fungsi abstract)
             if (hasMarker()){
                 showToast(getString(R.string.location_not_select))
             }else{
                 // Panggil ViewModel untuk menyimpan favorit
                 viewModel.storeFavorite(inputText, lat, lon)
                 // Observasi hasil simpan di observeFavoriteResponse()
             }
         }
         // Observasi hasil simpan favorit dilakukan di fungsi terpisah (observeFavoriteResponse)
     }

    // Logic Observasi Hasil Simpan Favorit (Dipanggil di onCreate)
     private fun observeFavoriteResponse() {
         viewModel.response.observe(this@BaseMapActivity) { result -> // Gunakan this@BaseMapActivity
             if (result == (-1).toLong()) {
                 showToast(getString(R.string.cant_save))
             } else {
                 showToast(getString(R.string.save))
             }
         }
     }

    // Logic Update Check (Dipanggil di onCreate)
    // Mengamati viewModel.update dan menampilkan dialog update jika tersedia
     private fun checkUpdates(){
        lifecycleScope.launchWhenResumed { // Observe saat Activity dalam keadaan Resumed
            viewModel.update.collect{ updateInfo -> // updateInfo adalah data update dari ViewModel
                if (updateInfo != null){
                    // Panggil dialogHelper untuk menampilkan dialog update
                    // Pass data updateInfo dan lambda untuk aksi Cancel
                    val updateDialogInstance = dialogHelper.showUpdateDialog(
                        updateInfo = updateInfo.changelog, // Pass changelog ke dialog
                        onCancelClicked = {
                             // Logic saat tombol Cancel di dialog diklik: panggil ViewModel untuk cancel download
                             viewModel.cancelDownload(this@BaseMapActivity) // Pass context
                             // Dismiss dialog bisa dihandle di callback download state
                             // updateDialogInstance?.dismiss() // Jika simpan referensi dialog
                         }
                        // Jika logic progress/state download di helper, pass viewModel.downloadState dan lifecycleScope
                        // updateState = viewModel.downloadState,
                        // lifecycleScope = lifecycleScope
                    )

                    // Jika logic progress/state download di Activity (atau ViewModel),
                    // Activity yang observe viewModel.downloadState dan update UI dialog
                    // Ini butuh referensi ke UI progress indicator di dalam dialog.
                    // Bisa simpan referensi dialogView atau dialogInstance dan cari view-nya.
                     lifecycleScope.launch {
                         viewModel.downloadState.collect { state ->
                             when (state) {
                                 is MainViewModel.State.Downloading -> {
                                     // Cari progress indicator di dialog dan update
                                     // updateDialogInstance?.findViewById<LinearProgressIndicator>(R.id.update_download_progress)?.apply {
                                     //    isIndeterminate = false
                                     //    progress = state.progress
                                     // }
                                 }
                                 is MainViewModel.State.Done -> {
                                     // dismiss dialog
                                     // updateDialogInstance?.dismiss()
                                     viewModel.openPackageInstaller(this@BaseMapActivity, state.fileUri)
                                     viewModel.clearUpdate()
                                 }
                                 is MainViewModel.State.Failed -> {
                                     // dismiss dialog
                                     // updateDialogInstance?.dismiss()
                                     showToast(getString(R.string.bs_update_download_failed))
                                 }
                                 else -> {} // State Idle
                             }
                         }
                     }
                }
            }
        }
    }
    // Logic Favorite List Item Click / Delete (Logic ini ada di dalam setupNavView listener untuk R.id.get_favorite)
    // Logic ini dipicu oleh lambda onItemClick dan onItemDelete dari dialogHelper.showFavoriteListDialog
    // ... (kode di setupNavView sudah menunjukkan ini) ...
    // ----------------------------------------------------------------------------------


    // --- FUNGSI UTILITY (SUDAH DIPINDAH) ---
    // fun isNetworkConnected(): Boolean { ... } // DIHAPUS, Panggil dari NetworkUtils.isNetworkConnected(this)
    // ------------------------------------

    // SearchProgress sealed class sudah dipindah ke SearchHelper
    // NotificationActionReceiver class (tetap ada sebagai file terpisah)

    // Catatan: Fungsi extension 'showToast' dan 'isNetworkConnected' (kalau aslinya extension)
    // perlu dipastikan bisa diakses dari Activity ini. Kalau dipindah ke file Utils,
    // pastikan file tersebut diimport dan fungsi/propertinya bisa diakses.
    // showToast() di kode asli adalah extension function (kemungkinan ada di ext/File.kt)
    // Kalau isNetworkConnected() di kode asli juga extension function,
    // panggilannya dari NetworkUtils.isNetworkConnected(this) mungkin perlu disesuaikan
    // kalau mau tetap jadi extension function di tempat baru.

}
