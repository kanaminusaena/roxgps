package io.github.jqssun.gpssetter.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.jqssun.gpssetter.BuildConfig
import io.github.jqssun.gpssetter.R
import io.github.jqssun.gpssetter.adapter.FavListAdapter
import io.github.jqssun.gpssetter.databinding.ActivityMapBinding
import io.github.jqssun.gpssetter.service.LocationService
import io.github.jqssun.gpssetter.ui.viewmodel.MainViewModel
import io.github.jqssun.gpssetter.utils.JoystickService
import io.github.jqssun.gpssetter.utils.NotificationsChannel
import io.github.jqssun.gpssetter.utils.PrefManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.regex.Pattern
import kotlin.properties.Delegates
import io.github.jqssun.gpssetter.utils.FileLogger
import android.net.Uri
import io.github.jqssun.gpssetter.utils.StoragePermissionChecker

@AndroidEntryPoint
abstract class BaseMapActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BaseMapActivity"
        private const val PERMISSION_ID = 42
        const val STORAGE_PERMISSION_REQUEST = 100
    }
    // Properti Latitude, harus diinisialisasi sebelum digunakan
    protected var lat by Delegates.notNull<Double>()
    // Properti Longitude, harus diinisialisasi sebelum digunakan
    protected var lon by Delegates.notNull<Double>()
    // ViewModel untuk logika aktivitas utama
    protected val viewModel by viewModels<MainViewModel>()
    // View binding untuk layout aktivitas
    protected val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    // MaterialAlertDialogBuilder untuk berbagai dialog
    protected lateinit var alertDialog: MaterialAlertDialogBuilder
    // Referensi ke AlertDialog saat ini, untuk menghilangkan
    protected lateinit var dialog: AlertDialog
    // Menyimpan info pembaruan jika tersedia
    protected val update by lazy { viewModel.getAvailableUpdate() }
    // Adapter untuk daftar lokasi favorit
    private var favListAdapter: FavListAdapter = FavListAdapter()
    // Dialog untuk peringatan Xposed
    private var xposedDialog: AlertDialog? = null
    // Untuk mengambil lokasi perangkat
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // Kode permintaan izin
    
    // Overlay elevasi untuk styling header
    private val elevationOverlayProvider by lazy { ElevationOverlayProvider(this) }
    // Warna latar belakang header dengan elevasi yang sesuai
    private val headerBackground by lazy {
        elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(
            resources.getDimension(R.dimen.bottom_sheet_elevation)
        )
    }

    /**
     * Mengembalikan instance aktivitas saat ini sebagai BaseMapActivity.
     */
    protected abstract fun getActivityInstance(): BaseMapActivity

    /**
     * Mengembalikan true jika peta saat ini memiliki penanda yang ditempatkan.
     */
    protected abstract fun hasMarker(): Boolean

    /**
     * Menginisialisasi tampilan peta dan logika terkait.
     */
    protected abstract fun initializeMap()

    /**
     * Menyiapkan dan mengikat semua tombol UI.
     */
    protected abstract fun setupButtons()

    /**
     * Memindahkan tampilan peta ke lokasi baru jika diminta.
     * @param moveNewLocation apakah akan pindah ke lokasi baru
     */
    protected abstract fun moveMapToNewLocation(moveNewLocation: Boolean)
    
    private lateinit var storagePermissionChecker: StoragePermissionChecker

    /**
     * Logika inisialisasi untuk aktivitas, termasuk UI dan pendengar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileLogger.log("Memulai onCreate", TAG, "I")
        try {
            setupStoragePermission()
            enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContentView(binding.root)
            setSupportActionBar(binding.toolbar)

            // Inisialisasi komponen hanya jika izin sudah diberikan
            if (storagePermissionChecker.hasStoragePermission()) {
                initializeComponents()
            } else {
                requestStoragePermission()
            }
        } catch (e: Exception) {
            FileLogger.log("Error dalam onCreate: ${e.message}", TAG, "E")
        }
    }
    
    private fun initializeComponents() {
        FileLogger.log("Menginisialisasi komponen", TAG, "D")
        initializeMap()
        checkModuleEnabled()
        checkUpdates()
        setupNavView()
        setupButtons()
        setupDrawer()
        checkNotifPermission()
        
        if (PrefManager.isJoystickEnabled) {
            FileLogger.log("Memulai JoystickService", TAG, "D")
            startService(Intent(this, JoystickService::class.java))
        }
    }
    
    private fun setupStoragePermission() {
        storagePermissionChecker = StoragePermissionChecker(this)
        FileLogger.log("Storage permission checker diinisialisasi", TAG, "D")
    }

    private fun requestStoragePermission() {
        storagePermissionChecker.requestStoragePermission { granted ->
            if (granted) {
                FileLogger.log("Izin penyimpanan diberikan, melanjutkan inisialisasi", TAG, "I")
                initializeComponents()
            } else {
                FileLogger.log("Izin penyimpanan ditolak", TAG, "W")
                showStoragePermissionError()
            }
        }
    }

    private fun showStoragePermissionError() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_permission_error_title)
            .setMessage(R.string.storage_permission_error_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                requestStorageAccess()
            }
            .setNegativeButton(R.string.exit) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    // Fungsi untuk meminta izin storage
    private fun requestStorageAccess() {
        try {
            FileLogger.log("Memulai permintaan akses storage", TAG, "I")
            // Untuk Android 11 ke atas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                // Ini akan memanggil onActivityResult
                startActivityForResult(intent, STORAGE_PERMISSION_REQUEST)
            }
        } catch (e: Exception) {
            FileLogger.log("Error saat meminta akses storage: ${e.message}", TAG, "E")
        }
    }
    
    // Ini akan dipanggil otomatis setelah startActivityForResult selesai
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FileLogger.log("Menerima hasil activity dengan requestCode: $requestCode", TAG, "D")
        
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST -> {
                // Forward ke StoragePermissionChecker
                storagePermissionChecker.onActivityResult(requestCode)
                
                // Cek hasil
                if (storagePermissionChecker.hasStoragePermission()) {
                    FileLogger.log("Izin storage diberikan, melanjutkan inisialisasi", TAG, "I")
                    initializeComponents()
                } else {
                    FileLogger.log("Izin storage ditolak", TAG, "W")
                    showStoragePermissionError()
                }
            }
        }
    }


    /**
     * Dipanggil saat aktivitas dilanjutkan; memeriksa status modul dan izin notifikasi.
     */
    override fun onResume() {
        super.onResume()
        FileLogger.log("Activity onResume", TAG, "D")
        viewModel.updateXposedState()
        checkNotifPermission()
        
        // Cek ulang izin saat kembali dari pengaturan
        if (!storagePermissionChecker.hasStoragePermission()) {
            requestStoragePermission()
        } else {
            viewModel.updateXposedState()
            checkNotifPermission()
        }
    }

    /**
     * Secara programatis memicu klik tombol berhenti (tidak diperlukan lagi dengan service).
     */
    fun performStopButtonClick() {
        binding.stopButton.performClick()
    }

    /**
     * Memeriksa dan meminta izin notifikasi jika diperlukan oleh versi Android.
     */
    private fun checkNotifPermission() {
        FileLogger.log("Memeriksa izin notifikasi", TAG, "D")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                FileLogger.log("Meminta izin notifikasi untuk Android 13+", TAG, "I")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_ID
                )
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            FileLogger.log("Notifikasi tidak diaktifkan, menampilkan dialog", TAG, "W")
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.enable_notifications)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    FileLogger.log("Membuka pengaturan notifikasi", TAG, "I")
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
                .setNegativeButton(R.string.done, null)
                .show()
        }
    }

    /**
     * Menyiapkan navigation drawer dan perilaku toggle-nya.
     */
    private fun setupDrawer() {
        FileLogger.log("Menyiapkan navigation drawer", TAG, "D")
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val mDrawerToggle = createDrawerToggle()
        binding.container.addDrawerListener(mDrawerToggle)
    }

    private fun createDrawerToggle(): ActionBarDrawerToggle {
        return object : ActionBarDrawerToggle(
            this,
            binding.container,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            override fun onDrawerClosed(view: View) {
                FileLogger.log("Drawer ditutup", TAG, "D")
                invalidateOptionsMenu()
            }

            override fun onDrawerOpened(drawerView: View) {
                FileLogger.log("Drawer dibuka", TAG, "D")
                invalidateOptionsMenu()
            }
        }
    }

    /**
     * Menyiapkan tampilan navigasi, termasuk pendengar dan logika pencarian.
     */
    private fun setupNavView() {
        binding.mapContainer.map.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int = insets.systemWindowInsetTop
            binding.navView.setPadding(0, topInset, 0, 0)
            insets.consumeSystemWindowInsets()
        }

        val progress = binding.search.searchProgress
        binding.search.searchBox.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (isNetworkConnected()) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val getInput = v.text.toString()
                        if (getInput.isNotEmpty()) {
                            getSearchAddress(getInput).collect { result ->
                                when (result) {
                                    is SearchProgress.Progress -> progress.visibility = View.VISIBLE
                                    is SearchProgress.Complete -> {
                                        progress.visibility = View.GONE
                                        lat = result.lat
                                        lon = result.lon
                                        moveMapToNewLocation(true)
                                    }
                                    is SearchProgress.Fail -> {
                                        progress.visibility = View.GONE
                                        showToast(result.error ?: "")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    showToast(getString(R.string.no_internet))
                }
                true
            } else {
                false
            }
        }

        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.get_favorite -> openFavoriteListDialog()
                R.id.settings -> startActivity(Intent(this, ActivitySettings::class.java))
                R.id.about -> aboutDialog()
            }
            binding.container.closeDrawer(GravityCompat.START)
            true
        }
    }

    /**
     * Mengamati apakah modul Xposed diaktifkan dan menampilkan dialog jika tidak.
     */
    private fun checkModuleEnabled() {
        viewModel.isXposed.observe(this) { isXposed ->
            if (!isXposed) {
                xposedDialog?.dismiss()
                xposedDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.error_xposed_module_missing)
                    .setMessage(R.string.error_xposed_module_missing_desc)
                    .setCancelable(true)
                    .show()
            }
        }
    }

    /**
     * Menampilkan dialog tentang dengan informasi aplikasi.
     */
    protected fun aboutDialog() {
        alertDialog = MaterialAlertDialogBuilder(this)
        layoutInflater.inflate(R.layout.about, null).apply {
            findViewById<TextView>(R.id.design_about_title).text = getString(R.string.app_name)
            findViewById<TextView>(R.id.design_about_version).text = BuildConfig.VERSION_NAME
            findViewById<TextView>(R.id.design_about_info).text = getString(R.string.about_info)
        }.run {
            alertDialog.setView(this)
            alertDialog.show()
        }
    }

    /**
     * Menampilkan dialog untuk menambahkan lokasi favorit.
     */
    protected fun addFavoriteDialog() {
        FileLogger.log("Menampilkan dialog tambah favorit", TAG, "D")
        alertDialog = MaterialAlertDialogBuilder(this).apply {
            val view = layoutInflater.inflate(R.layout.dialog, null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
            
            setTitle(getString(R.string.add_fav_dialog_title))
            setPositiveButton(getString(R.string.dialog_button_add)) { _, _ ->
                handleFavoriteAddition(editText.text.toString())
            }
            setView(view)
            show()
        }
    }
    
    private fun handleFavoriteAddition(locationName: String) {
        if (hasMarker()) {
            FileLogger.log("Gagal menambah favorit: Lokasi belum dipilih", TAG, "W")
            showToast(getString(R.string.location_not_select))
        } else {
            FileLogger.log("Menyimpan lokasi favorit: $locationName ($lat, $lon)", TAG, "I")
            viewModel.storeFavorite(locationName, lat, lon)
            observeFavoriteResponse()
        }
    }

    private fun observeFavoriteResponse() {
        viewModel.response.observe(getActivityInstance()) { response ->
            if (response == -1L) {
                FileLogger.log("Gagal menyimpan lokasi favorit", TAG, "E")
                showToast(getString(R.string.cant_save))
            } else {
                FileLogger.log("Lokasi favorit berhasil disimpan", TAG, "I")
                showToast(getString(R.string.save))
            }
        }
    }


    /**
     * Membuka dialog daftar favorit dan menyiapkan callback-nya.
     */
    private fun openFavoriteListDialog() {
        getAllUpdatedFavList()
        alertDialog = MaterialAlertDialogBuilder(this)
        alertDialog.setTitle(getString(R.string.favorites))
        val view = layoutInflater.inflate(R.layout.fav, null)
        val rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(this)
        rcv.adapter = favListAdapter
        favListAdapter.onItemClick = {
            lat = it.lat ?: lat
            lon = it.lng ?: lon
            moveMapToNewLocation(true)
            if (::dialog.isInitialized && dialog.isShowing) dialog.dismiss()
        }
        favListAdapter.onItemDelete = {
            viewModel.deleteFavorite(it)
        }
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()
    }

    /**
     * Mengambil lokasi favorit terbaru dan memperbarui adapter.
     */
    private fun getAllUpdatedFavList() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.doGetUserDetails()
                viewModel.allFavList.collect {
                    favListAdapter.submitList(it)
                }
            }
        }
    }

    /**
     * Memeriksa pembaruan yang tersedia dan memicu dialog pembaruan jika ada.
     */
    private fun checkUpdates() {
        lifecycleScope.launchWhenResumed {
            viewModel.update.collect {
                if (it != null) {
                    updateDialog()
                }
            }
        }
    }

    /**
     * Menampilkan dialog pembaruan dan memulai pengunduhan jika pembaruan tersedia.
     */
    private fun updateDialog() {
        alertDialog = MaterialAlertDialogBuilder(this)
        alertDialog.setTitle(R.string.update_available)
        alertDialog.setMessage(update?.changelog)
        alertDialog.setPositiveButton(getString(R.string.update_button)) { _, _ ->
            MaterialAlertDialogBuilder(this).apply {
                val view = layoutInflater.inflate(R.layout.update_dialog, null)
                val progress = view.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                val cancel = view.findViewById<AppCompatButton>(R.id.update_download_cancel)
                setView(view)
                cancel.setOnClickListener {
                    viewModel.cancelDownload(getActivityInstance())
                    dialog.dismiss()
                }
                lifecycleScope.launch {
                    viewModel.downloadState.collect {
                        when (it) {
                            is MainViewModel.State.Downloading -> {
                                if (it.progress > 0) {
                                    progress.isIndeterminate = false
                                    progress.progress = it.progress
                                }
                            }
                            is MainViewModel.State.Done -> {
                                viewModel.openPackageInstaller(getActivityInstance(), it.fileUri)
                                viewModel.clearUpdate()
                                dialog.dismiss()
                            }
                            is MainViewModel.State.Failed -> {
                                Toast.makeText(
                                    getActivityInstance(),
                                    R.string.bs_update_download_failed,
                                    Toast.LENGTH_LONG
                                ).show()
                                dialog.dismiss()
                            }
                            else -> {}
                        }
                    }
                }
                update?.let { u ->
                    viewModel.startDownload(getActivityInstance(), u)
                } ?: run {
                    dialog.dismiss()
                }
            }.run {
                dialog = create()
                dialog.show()
            }
        }
        dialog = alertDialog.create()
        dialog.show()
    }

    /**
     * Mengembalikan Flow untuk mencari alamat, mengirimkan progress dan hasil/kegagalan.
     * @param address Alamat atau string koordinat untuk dicari.
     */
    private suspend fun getSearchAddress(address: String) = callbackFlow {
        withContext(Dispatchers.IO) {
            trySend(SearchProgress.Progress)
            val matcher = Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)
            if (matcher.matches()) {
                delay(3000)
                trySend(SearchProgress.Complete(
                    matcher.group().split(",")[0].toDouble(),
                    matcher.group().split(",")[1].toDouble()
                ))
            } else {
                val geocoder = Geocoder(getActivityInstance())
                try {
                    val addressList: List<Address>? = geocoder.getFromLocationName(address, 3)
                    addressList?.let {
                        if (it.size == 1) {
                            trySend(SearchProgress.Complete(it[0].latitude, it[0].longitude))
                        } else {
                            trySend(SearchProgress.Fail(getString(R.string.address_not_found)))
                        }
                    }
                } catch (io: IOException) {
                    trySend(SearchProgress.Fail(getString(R.string.no_internet)))
                }
            }
        }
        awaitClose { this.cancel() }
    }

    /**
     * Menampilkan notifikasi persisten dengan tindakan berhenti melalui ForegroundService.
     * @param address Alamat untuk ditampilkan di notifikasi.
     */
    protected fun showStartNotification(address: String) {
        try {
            val intent = Intent(this, LocationService::class.java).apply {
                putExtra(LocationService.EXTRA_ADDRESS, address)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            FileLogger.log("Service lokasi dimulai dengan alamat: $address", TAG, "I")
        } catch (e: Exception) {
            FileLogger.log("Error saat memulai service: ${e.message}", TAG, "E")
            Toast.makeText(this, "Gagal memulai service lokasi", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Menghentikan ForegroundService dan menyembunyikan notifikasi.
     */
    protected fun cancelNotification() {
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = NotificationsChannel.ACTION_STOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(stopIntent)
        } else {
            startService(stopIntent)
        }
    }

    /**
     * Menampilkan Snackbar yang meminta pengguna untuk mengaktifkan layanan lokasi.
     */
    private fun handleLocationError() {
        Snackbar.make(binding.root, "Layanan lokasi dinonaktifkan.", Snackbar.LENGTH_LONG)
            .setAction("Aktifkan") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .show()
    }

    /**
     * Meminta lokasi terakhir yang diketahui dan memindahkan peta jika berhasil.
     */
    @SuppressLint("MissingPermission")
    protected fun getLastLocation() {
        FileLogger.log("Meminta lokasi terakhir", TAG, "D")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        when {
            !checkPermissions() -> {
                FileLogger.log("Izin lokasi tidak diberikan", TAG, "W")
                requestPermissions()
            }
            !isLocationEnabled() -> {
                FileLogger.log("Layanan lokasi tidak aktif", TAG, "W")
                handleLocationError()
            }
            else -> {
                requestLastLocation()
            }
        }
    }
    
    private fun requestLastLocation() {
        FileLogger.log("Meminta pembaruan lokasi", TAG, "D")
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    FileLogger.log("Lokasi diterima: ${it.latitude}, ${it.longitude}", TAG, "I")
                    lat = it.latitude
                    lon = it.longitude
                    moveMapToNewLocation(true)
                } ?: run {
                    FileLogger.log("Lokasi null, meminta pembaruan baru", TAG, "W")
                    requestNewLocationData()
                }
            }
            .addOnFailureListener { e ->
                FileLogger.log("Gagal mendapatkan lokasi: ${e.message}", TAG, "E")
                handleLocationError()
            }
    }

    /**
     * Meminta pembaruan lokasi baru jika lokasi terakhir yang diketahui tidak tersedia.
     */
    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 0
            fastestInterval = 0
            numUpdates = 1
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    /**
     * Callback untuk pembaruan lokasi, memperbarui lat/lon saat diterima.
     */
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation ?: return
            lat = mLastLocation.latitude
            lon = mLastLocation.longitude
        }
    }

    /**
     * Mengembalikan true jika penyedia lokasi GPS atau jaringan diaktifkan.
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Mengembalikan true jika izin lokasi diberikan.
     */
    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Meminta izin lokasi dari pengguna.
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_ID
        )
    }

    /**
     * Menangani hasil permintaan izin.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            getLastLocation()
        }
    }

    /**
     * Menampilkan pesan toast singkat.
     * @param message Pesan untuk ditampilkan.
     */
    protected fun showToast(message: String) {
        FileLogger.log("Menampilkan toast: $message", TAG, "D")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Memeriksa apakah perangkat terhubung ke jaringan.
     * @return true jika terhubung, false jika tidak.
     */
    protected fun isNetworkConnected(): Boolean {
        val isConnected = checkNetworkConnectivity()
        FileLogger.log("Status koneksi jaringan: $isConnected", TAG, "D")
        return isConnected
    }
    
    private fun checkNetworkConnectivity(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val activeNetwork = connectivityManager.getNetworkCapabilities(network)
            activeNetwork?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }
}

/**
 * Mewakili progress atau hasil dari operasi pencarian.
 */
sealed class SearchProgress {
    /** Menunjukkan bahwa pencarian sedang berlangsung. */
    object Progress : SearchProgress()
    /** Menunjukkan bahwa pencarian selesai dengan koordinat. */
    data class Complete(val lat: Double, val lon: Double) : SearchProgress()
    /** Menunjukkan bahwa pencarian gagal dengan error. */
    data class Fail(val error: String?) : SearchProgress()
}