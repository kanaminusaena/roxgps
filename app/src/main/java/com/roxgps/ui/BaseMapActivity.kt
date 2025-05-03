package com.roxgps.ui // Pastikan package ini sesuai

// --- IMPORTS YANG DIBUTUHKAN OLEH KODE DI BASE ACTIVITY ---
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import com.roxgps.BuildConfig
import com.roxgps.R
import com.roxgps.adapter.FavListAdapter
import com.roxgps.room.Favorite // Mengimpor Favorite dari package ROOM, sesuai keputusan kita
import com.roxgps.databinding.ActivityMapBinding // View Binding
import com.roxgps.ui.viewmodel.MainViewModel
import com.roxgps.utils.JoystickService
import com.roxgps.utils.PrefManager
import com.roxgps.utils.ext.* // Mengimpor semua extension functions
import com.roxgps.helper.LocationHelper
import com.roxgps.helper.LocationListener
import com.roxgps.helper.DialogHelper
import com.roxgps.helper.NotificationHelper
import com.roxgps.helper.SearchHelper
import com.roxgps.helper.SearchProgress // Sealed class dari SearchHelper
import com.roxgps.utils.NetworkUtils
import com.roxgps.utils.NotificationsChannel // Objek singleton channel notifikasi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow // Untuk tipe data StateFlow
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.properties.Delegates
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.snackbar.Snackbar
import android.app.PendingIntent // Untuk PendingIntent
import android.content.BroadcastReceiver // Untuk BroadcastReceiver (jika perlu di Base, tapi sudah pindah ke Helper)
import android.content.IntentFilter // Untuk IntentFilter (jika perlu di Base)


@AndroidEntryPoint // Anotasi Hilt
// BaseMapActivity mengimplementasikan LocationListener
abstract class BaseMapActivity: AppCompatActivity(), LocationListener {

    // --- PROPERTI-PROPERTI STATE DAN VIEWMODEL ---
    // Menggunakan Delegates.notNull untuk properti yang diinisialisasi nanti
    protected var lat by Delegates.notNull<Double>()
    protected var lon by Delegates.notNull<Double>()
    protected val viewModel by viewModels<MainViewModel>() // ViewModel di-inject oleh Hilt
    protected val binding by lazy { ActivityMapBinding.inflate(layoutInflater) } // View Binding
    // update dari ViewModel diobservasi di checkUpdates()
    // private val update by lazy { viewModel.getAvailableUpdate() } // Tidak perlu properti, langsung observe

    private val notificationsChannel = NotificationsChannel // Objek Singleton Channel Notifikasi
    // Adapter untuk list favorit, dipegang di Activity/Base
    private var favListAdapter: FavListAdapter = FavListAdapter()
    // Referensi dialog Xposed, agar bisa di-dismiss dari luar fungsi pembuatnya
    private var xposedDialog: AlertDialog? = null

    private val elevationOverlayProvider by lazy { ElevationOverlayProvider(this) }
    private val headerBackground by lazy {
        elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(
            resources.getDimension(R.dimen.bottom_sheet_elevation)
        )
    }
     // Properti 'receiver' tidak ada di sini, BroadcastReceiver objek ada di NotificationHelper


    // --- INSTANSIASI HELPER-HELPER (DIDEKLARASI PROTECTED AGAR BISA DIAKSES SUBCLASS) ---
    // Mengubah akses dari private menjadi protected
    protected lateinit var locationHelper: LocationHelper
    protected lateinit var dialogHelper: DialogHelper
    protected lateinit var notificationHelper: NotificationHelper
    protected lateinit var searchHelper: SearchHelper
    // Tambahkan helper lain jika ada
    // protected lateinit var someOtherHelper: SomeOtherHelper
    // -----------------------------------------------------------------------------------


    // --- FUNGSI ABSTRACT YANG HARUS DIIMPLEMENTASIKAN OLEH SUBCLASS (MapActivity) ---
    protected abstract fun hasMarker(): Boolean // Cek apakah ada marker di map
    protected abstract fun initializeMap() // Inisialisasi Map API spesifik
    protected abstract fun setupButtons() // Setup Tombol-tombol UI
    protected abstract fun moveMapToNewLocation(moveNewLocation: Boolean) // Pindah Map ke koordinat
    // Fungsi abstract untuk logika inti Stop Proses, dipanggil dari lambda notifikasi atau tombol UI
    protected abstract fun stopProcess()
    // ------------------------------------------------------------------------------


    // --- FUNGSI PROTECTED UNTUK MENGAKSES FUNGSI HELPER DARI SUBCLASS ---
    // Mengubah akses dari private menjadi protected
    protected fun requestLocation() {
        locationHelper.requestLastKnownLocation() // Memanggil fungsi helper
    }

    protected fun showStartNotification(address: String) {
        notificationHelper.showStartNotification(address) // Memanggil fungsi helper
    }

    protected fun cancelNotification() {
        notificationHelper.cancelNotification() // Memanggil fungsi helper
    }

    // Fungsi untuk mengecek permission lokasi melalui helper
    protected fun checkLocationPermissions(): Boolean {
        return locationHelper.checkLocationPermissions() // Memanggil fungsi helper
    }

    // Fungsi untuk menghentikan update lokasi melalui helper
    protected fun stopLocationUpdates() {
        locationHelper.stopLocationUpdates() // Memanggil fungsi helper
    }
    // ---------------------------------------------------------------------


    // --- LIFECYCLE ACTIVITY ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup Edge-to-Edge dan Window Flags
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Set Content View menggunakan View Binding
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)

        // --- INISIALISASI HELPER-HELPER SETELAH setContentView ---
        locationHelper = LocationHelper(this, this) // Context, LocationListener (Activity itu sendiri)
        dialogHelper = DialogHelper(this, layoutInflater) // Context, LayoutInflater
        // Context, objek Channel Notifikasi, dan lambda aksi Stop
        // Lambda aksi Stop memanggil fungsi abstract stopProcess()
        notificationHelper = NotificationHelper(this, notificationsChannel) {
            stopProcess() // Panggil fungsi abstract stopProcess()
        }
        searchHelper = SearchHelper(this) // Context
        // Inisialisasi helper lain jika ada
        // someOtherHelper = SomeOtherHelper(this, ...)
        // ------------------------------------------------------

        // --- PANGGILAN FUNGSI-FUNGSI SETUP AWAL ACTIVITY ---
        initializeMap() // Fungsi abstract (di MapActivity)
        checkModuleEnabled() // Cek Xposed
        checkUpdates() // Cek Update
        setupNavView() // Setup NavView Listeners
        setupButtons() // Fungsi abstract (di MapActivity)
        setupDrawer() // Setup Drawer Toggle
        checkNotifPermission() // Cek Permission Notifikasi

        // Cek PrefManager dan start Service
        if (PrefManager.isJoystickEnabled){
            startService(Intent(this, JoystickService::class.java))
        }

        // --- REGISTER RECEIVER NOTIFIKASI VIA HELPER ---
        notificationHelper.registerReceiver()
        // -----------------------------------------------

        // --- PANGGIL FUNGSI-FUNGSI OBSERVASI DATA DARI VIEWMODEL ---
        observeFavoriteResponse() // Observasi hasil simpan favorit
        // checkUpdates() sudah mengobservasi viewModel.update
        // Observasi list favorit dari ViewModel menggunakan Flow dan lifecycleScope
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                 //viewModel.doGetUserDetails() // Panggil load awal list favorit jika perlu di sini atau di ViewModel init
                 viewModel.allFavList.collect { list -> // Observasi StateFlow<List<Favorite>>
                    favListAdapter.submitList(list) // Update adapter list favorit
                 }
            }
        }
        // --------------------------------------------------------

        // Optional: Panggil request lokasi awal pas onCreate/onResume
        // requestLocation() // Panggil fungsi protected yang baru
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState() // Update state Xposed di ViewModel
        checkNotifPermission() // Cek Permission Notifikasi (dipanggil lagi setiap resume)
        // Optional: Panggil request lokasi di resume
        // requestLocation() // Panggil fungsi protected yang baru
    }

    override fun onDestroy() {
        super.onDestroy()
        // --- UNREGISTER RECEIVER VIA HELPER ---
        notificationHelper.unregisterReceiver()
        // ------------------------------------

        // --- STOP LOCATION UPDATES VIA HELPER ---
        stopLocationUpdates() // Panggil fungsi protected yang baru
        // --------------------------------------

        // Dismiss Xposed dialog kalau masih tampil saat Activity hancur
        xposedDialog?.dismiss()

        // Cleanup resources lain jika ada
    }

    // --- IMPLEMENTASI INTERFACE LOCATIONLISTENER DARI LOCATIONHELPER ---
    // Kode ini dijalankan saat LocationHelper berhasil mendapatkan lokasi
    override fun onLocationResult(location: Location) {
        lat = location.latitude // Update state lat di Activity
        lon = location.longitude // Update state lon di Activity
        moveMapToNewLocation(true) // Panggil fungsi abstract untuk update map
        // println("Lokasi diperbarui dari helper: Latitude = $lat, Longitude = $lon")
        // showToast("Lokasi diperbarui!") // Membutuhkan fungsi showToast (extension function)
    }

    // Kode ini dijalankan saat LocationHelper melaporkan error lokasi
    override fun onLocationError(message: String) {
        // Panggil fungsi UI error handling
        handleLocationError() // Menampilkan Snackbar error lokasi
        // Opsional: Tampilkan pesan error dari helper
        // showToast("Error Lokasi: $message") // Membutuhkan fungsi showToast
    }

    // Kode ini dijalankan saat izin lokasi diberikan setelah diminta
    override fun onPermissionGranted() {
        showToast("Izin lokasi diberikan!") // Membutuhkan fungsi showToast
        // Optional: Langsung coba request lokasi lagi setelah izin dikasih
        // requestLocation() // Panggil fungsi protected yang baru
    }

    // Kode ini dijalankan saat izin lokasi ditolak oleh pengguna
    override fun onPermissionDenied() {
        showToast("Izin lokasi ditolak. Fitur lokasi tidak dapat digunakan.") // Membutuhkan fungsi showToast
        // Opsional: Disable tombol atau fitur yang butuh lokasi
    }
    // ----------------------------------------------------------------


    // --- FUNGSI UI ERROR HANDLING (Dipanggil dari LocationListener) ---
    private fun handleLocationError() {
         // Menggunakan binding.root untuk view Snackbar
         Snackbar.make(binding.root, getString(R.string.location_service_disabled), Snackbar.LENGTH_LONG) // Menggunakan resource string
            .setAction(getString(R.string.activate)) { // Menggunakan resource string
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .show()
         // Catatan: Layout 'activity_map' harus punya string "location_service_disabled" dan "activate"
         // Tambahkan di strings.xml: <string name="location_service_disabled">Layanan lokasi dinonaktifkan.</string>
         // Tambahkan di strings.xml: <string name="activate">Aktifkan</string>
    }
    // ---------------------------------------------------------------


    // --- LOGIC YANG DIPICU DARI CALLBACK DIALOGHELPER ATAU LISTENER LAIN ---

    // Logic Add Favorite (Dipanggil dari listener tombol Add Favorite di subclass)
     protected fun addFavoriteAction() { // Mengubah akses dari private menjadi protected
         // Panggil dialogHelper untuk menampilkan dialog input nama favorit
         dialogHelper.showAddFavoriteDialog { inputText ->
             // Logic yang dijalankan saat tombol "Add" di dialog diklik
             // Cek hasMarker() (fungsi abstract)
             if (!hasMarker()){ // Logika asli: kalau BELUM ada marker (true) -> tampil toast, kalau SUDAH ada marker (false) -> simpan. Diubah agar sesuai.
                 showToast(getString(R.string.location_not_select)) // Membutuhkan fungsi showToast, resource string
             }else{
                 // Panggil ViewModel untuk menyimpan favorit
                 // lat dan lon di sini seharusnya sudah terinisialisasi karena onLocationResult sudah dipanggil sebelum marker muncul
                 viewModel.storeFavorite(inputText, lat, lon)
                 // Observasi hasil simpan dilakukan di observeFavoriteResponse()
             }
         }
         // Observasi hasil simpan favorit dilakukan di fungsi terpisah (observeFavoriteResponse)
     }

    // Logic Observasi Hasil Simpan Favorit (Dipanggil di onCreate)
     private fun observeFavoriteResponse() {
         // Gunakan this@BaseMapActivity untuk Scope observasi LiveData
         viewModel.response.observe(this@BaseMapActivity) { result ->
             if (result == (-1).toLong()) {
                 showToast(getString(R.string.cant_save)) // Membutuhkan fungsi showToast, resource string
             } else {
                 showToast(getString(R.string.save)) // Membutuhkan fungsi showToast, resource string
             }
         }
         // Catatan: Pastikan strings.xml punya string "cant_save" dan "save"
         // Tambahkan di strings.xml: <string name="cant_save">Gagal menyimpan favorit.</string>
         // Tambahkan di strings.xml: <string name="save">Favorit tersimpan!</string>
     }

    // Logic Update Check (Dipanggil di onCreate)
    // Mengamati viewModel.update dan viewModel.downloadState, menampilkan dialog update jika tersedia
     private fun checkUpdates(){
        lifecycleScope.launch { // Launch coroutine di lifecycleScope
            // Menggunakan repeatOnLifecycle untuk mengumpulkan Flow dengan aman
             lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                 viewModel.update.collect{ updateInfo -> // updateInfo adalah data update dari ViewModel (mungkin nullable GitHubRelease?)
                     if (updateInfo != null){ // Cek jika ada info update
                         // Panggil dialogHelper untuk menampilkan dialog update
                         val updateDialogInstance = dialogHelper.showUpdateDialog(
                             updateInfo = updateInfo.changelog, // Pass changelog ke dialog (asumsi updateInfo punya properti changelog)
                             onCancelClicked = {
                                 // Logic saat tombol Cancel di dialog diklik: panggil ViewModel untuk cancel download
                                 viewModel.cancelDownload(this@BaseMapActivity) // Pass context
                             }
                             // Jika logic progress/state download di helper, bisa pass Flow/Scope ke helper
                             // updateState = viewModel.downloadState,
                             // lifecycleScope = this@BaseMapActivity.lifecycleScope // Pass lifecycleScope Activity
                         )

                         // Logic menampilkan progress dan dismiss dialog berdasarkan state download dari ViewModel
                         // Observable ini perlu di dalam lifecycleScope.launch di onCreate atau fungsi terpisah
                         // yang dipanggil dengan scope.
                         // Karena sudah di dalam scope collect update, kita bisa langsung observe downloadState di sini
                         // Ini butuh referensi ke UI progress indicator di dalam dialog.
                         // Referensi dialogInstance bisa disimpan dan dicari view-nya, atau pass view ke helper.
                         // Contoh: Menggunakan dialogView dari layout update_dialog

                         // Karena DialogHelper sudah dibuat dan mengembalikan instance dialog,
                         // kita bisa observe downloadState di sini dan update dialog.
                         // Atau, logic observe downloadState juga bisa dipanggil di onCreate
                         // di luar scope collect update, tapi tetap di dalam lifecycleScope.
                         // Mari kita buat observasi downloadState terpisah untuk kejelasan.
                     }
                 }
             }
        }

        // Observasi downloadState secara terpisah di onCreate atau scope terpisah
        lifecycleScope.launch {
             lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                 viewModel.downloadState.collect { state ->
                     when (state) {
                         is MainViewModel.State.Downloading -> {
                             // Cari progress indicator di dialog (butuh referensi dialogView/Instance)
                             // Ini adalah contoh cara update UI dialog dari Activity
                             // val progressIndicator = updateDialogInstance?.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                             // progressIndicator?.apply {
                             //    isIndeterminate = false
                             //    progress = state.progress
                             // }
                         }
                         is MainViewModel.State.Done -> {
                             // Dismiss dialog jika masih tampil
                             // updateDialogInstance?.dismiss()
                             showToast(getString(R.string.bs_update_download_completed)) // Membutuhkan resource string
                             viewModel.openPackageInstaller(this@BaseMapActivity, state.fileUri) // Membutuhkan fungsi openPackageInstaller di ViewModel
                             viewModel.clearUpdate() // Membutuhkan fungsi clearUpdate di ViewModel
                         }
                         is MainViewModel.State.Failed -> {
                             // Dismiss dialog jika masih tampil
                             // updateDialogInstance?.dismiss()
                             showToast(getString(R.string.bs_update_download_failed)) // Membutuhkan resource string
                         }
                         else -> {} // State Idle, tidak perlu aksi khusus
                     }
                 }
             }
        }
        // Catatan: Pastikan strings.xml punya string "bs_update_download_completed" dan "bs_update_download_failed"
     }

     // Optional: Fungsi untuk menampilkan Toast
     // Jika ini dulunya extension function, pastikan file extension tersebut diimport
     protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
     }

    // get Activity Instance - Fungsi ini tidak lagi dibutuhkan karena Activity sudah di-pass via constructor helper
    // @Deprecated("Pass Activity Context directly to helpers") // Bisa tambahkan anotasi Deprecated
    // override fun getActivityInstance(): BaseMapActivity { // Hapus implementasi fungsi ini
    //    return this
    // }


}
