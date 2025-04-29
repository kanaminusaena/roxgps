package io.github.jqssun.gpssetter.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import io.github.jqssun.gpssetter.receiver.NotificationActionReceiver
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
import androidx.core.app.NotificationCompat

@AndroidEntryPoint
abstract class BaseMapActivity : AppCompatActivity() {

    // Latitude property, must be initialized before use
    protected var lat by Delegates.notNull<Double>()
    // Longitude property, must be initialized before use
    protected var lon by Delegates.notNull<Double>()
    // ViewModel for main activity logic
    protected val viewModel by viewModels<MainViewModel>()
    // View binding for activity layout
    protected val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    // MaterialAlertDialogBuilder for various dialogs
    protected lateinit var alertDialog: MaterialAlertDialogBuilder
    // Reference to current AlertDialog, for dismissing
    protected lateinit var dialog: AlertDialog
    // Holds update info if available
    protected val update by lazy { viewModel.getAvailableUpdate() }
    // Notifications channel utility class
    private val notificationsChannel = NotificationsChannel
    // Adapter for favorite locations list
    private var favListAdapter: FavListAdapter = FavListAdapter()
    // Dialog for Xposed warning
    private var xposedDialog: AlertDialog? = null
    // For retrieving device location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // Permission request code
    private val PERMISSION_ID = 42
    // Elevation overlay for header styling
    private val elevationOverlayProvider by lazy { ElevationOverlayProvider(this) }
    // Header background color with proper elevation
    private val headerBackground by lazy {
        elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(
            resources.getDimension(R.dimen.bottom_sheet_elevation)
        )
    }

    // BroadcastReceiver for notification stop action
    private val stopActionReceiver = object : BroadcastReceiver() {
        /**
         * Handles the stop action broadcast for the notification.
         */
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationsChannel.ACTION_STOP) {
                performStopButtonClick()
            }
        }
    }

    /**
     * Returns the current activity instance as BaseMapActivity.
     */
    protected abstract fun getActivityInstance(): BaseMapActivity

    /**
     * Returns true if the map currently has a marker placed.
     */
    protected abstract fun hasMarker(): Boolean

    /**
     * Initializes the map view and related logic.
     */
    protected abstract fun initializeMap()

    /**
     * Sets up and binds all UI buttons.
     */
    protected abstract fun setupButtons()

    /**
     * Moves the map view to a new location if requested.
     * @param moveNewLocation whether to move to the new location
     */
    protected abstract fun moveMapToNewLocation(moveNewLocation: Boolean)

    /**
     * Initialization logic for the activity, including UI and listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        initializeMap()
        checkModuleEnabled()
        checkUpdates()
        setupNavView()
        setupButtons()
        setupDrawer()
        checkNotifPermission()
        if (PrefManager.isJoystickEnabled) {
            startService(Intent(this, JoystickService::class.java))
        }
        registerReceiver(stopActionReceiver, IntentFilter(NotificationsChannel.ACTION_STOP))
    }

    /**
     * Called when activity resumes; checks module state and notification permissions.
     */
    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState()
        checkNotifPermission()
    }

    /**
     * Cleanup logic for the activity, including unregistering receivers.
     */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stopActionReceiver)
    }

    /**
     * Programmatically triggers the stop button click.
     */
    fun performStopButtonClick() {
        binding.stopButton.performClick()
    }

    /**
     * Checks and requests notification permissions if required by Android version.
     */
    private fun checkNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_ID
                )
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Enable Notifications")
                .setMessage("This app requires notifications for optimal functionality. Please enable notifications in the settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                }
                .setNegativeButton("Done", null)
                .show()
        }
    }

    /**
     * Sets up the navigation drawer and its toggle behavior.
     */
    private fun setupDrawer() {
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val mDrawerToggle = object : ActionBarDrawerToggle(
            this,
            binding.container,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            override fun onDrawerClosed(view: View) = invalidateOptionsMenu()
            override fun onDrawerOpened(drawerView: View) = invalidateOptionsMenu()
        }
        binding.container.addDrawerListener(mDrawerToggle)
    }

    /**
     * Sets up the navigation view, including listeners and search logic.
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
     * Observes if the Xposed module is enabled and shows a dialog if not.
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
     * Shows the about dialog with app information.
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
     * Shows a dialog to add a favorite location.
     */
    protected fun addFavoriteDialog() {
        alertDialog = MaterialAlertDialogBuilder(this).apply {
            val view = layoutInflater.inflate(R.layout.dialog, null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
            setTitle(getString(R.string.add_fav_dialog_title))
            setPositiveButton(getString(R.string.dialog_button_add)) { _, _ ->
                val s = editText.text.toString()
                if (hasMarker()) {
                    showToast(getString(R.string.location_not_select))
                } else {
                    viewModel.storeFavorite(s, lat, lon)
                    viewModel.response.observe(getActivityInstance()) {
                        if (it == (-1).toLong()) showToast(getString(R.string.cant_save))
                        else showToast(getString(R.string.save))
                    }
                }
            }
            setView(view)
            show()
        }
    }

    /**
     * Opens the favorite list dialog and sets up its callbacks.
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
     * Fetches the latest favorite locations and updates the adapter.
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
     * Checks for available updates and triggers update dialog if present.
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
     * Shows the update dialog and starts download if update is available.
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
     * Returns a Flow for searching an address, sending progress and result/failure.
     * @param address The address or coordinates string to search.
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
     * Shows a persistent notification with a stop action.
     * @param address The address to display in the notification.
     */
    protected fun showStartNotification(address: String) {
        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationsChannel.ACTION_STOP
        }
        val stopPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationsChannel.showNotification(this) {
            it.setSmallIcon(R.drawable.ic_stop)
            it.setContentTitle(getString(R.string.location_set))
            it.setContentText(address)
            it.setAutoCancel(true)
            it.setOngoing(true)
            it.setCategory(Notification.CATEGORY_EVENT)
            it.priority = NotificationCompat.PRIORITY_HIGH
            it.addAction(
                R.drawable.ic_stop,
                getString(R.string.stop),
                stopPendingIntent
            )
        }
    }

    /**
     * Cancels all notifications for this app.
     */
    protected fun cancelNotification() {
        notificationsChannel.cancelAllNotifications(this)
    }

    /**
     * Displays a Snackbar that prompts the user to enable location services.
     */
    private fun handleLocationError() {
        Snackbar.make(binding.root, "Location services are disabled.", Snackbar.LENGTH_LONG)
            .setAction("Enable") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .show()
    }

    /**
     * Requests the last known location and moves the map if successful.
     */
    @SuppressLint("MissingPermission")
    protected fun getLastLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                fusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location: Location? = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        lat = location.latitude
                        lon = location.longitude
                        moveMapToNewLocation(true)
                    }
                }.addOnFailureListener {
                    handleLocationError()
                }
            } else {
                handleLocationError()
            }
        } else {
            requestPermissions()
        }
    }

    /**
     * Requests a new location update if the last known location is unavailable.
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
     * Callback for location updates, updates lat/lon when received.
     */
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation ?: return
            lat = mLastLocation.latitude
            lon = mLastLocation.longitude
        }
    }

    /**
     * Returns true if either GPS or network location provider is enabled.
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Returns true if location permissions are granted.
     */
    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests location permissions from the user.
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
     * Handles the result of a permission request.
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
     * Displays a short toast message.
     * @param message The message to show.
     */
    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Checks whether the device is connected to a network.
     * @return true if connected, false otherwise.
     */
    protected fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return activeNetwork.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }
}

/**
 * Represents progress or result of a search operation.
 */
sealed class SearchProgress {
    /** Indicates that searching is in progress. */
    object Progress : SearchProgress()
    /** Indicates that search completed with coordinates. */
    data class Complete(val lat: Double, val lon: Double) : SearchProgress()
    /** Indicates that search failed with an error. */
    data class Fail(val error: String?) : SearchProgress()
}
```
