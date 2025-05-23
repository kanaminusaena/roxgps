package com.roxgps.helper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject

/**
 * Interface untuk callback hasil request permission.
 *
 * @author loserkidz
 * @since 2025-05-23 13:44:55
 */
interface PermissionResultListener {
    fun onPermissionResult(permission: String, isGranted: Boolean)
    fun onPermissionsResult(permissions: Map<String, Boolean>)
}

/**
 * Helper class untuk mengelola permission dan settings di level Activity.
 * Menangani semua permission checking, requesting, dan settings navigation.
 *
 * @author loserkidz
 * @since 2025-05-23 13:44:55
 */
@ActivityScoped
class PermissionHelper @Inject constructor(
    @ActivityContext private val context: Context
) {
    companion object {
        private const val TAG = "PermissionHelper"

        // Permission groups
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        private val BACKGROUND_LOCATION_PERMISSION =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                emptyArray()
            }

        private val NOTIFICATION_PERMISSION =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
    }

    private val activity: ComponentActivity
        get() = context as ComponentActivity

    private var activePermissionResultListener: PermissionResultListener? = null
    private var lastRequestedPermission: String? = null

    // === Permission Checking Methods ===

    /**
     * Cek apakah permission lokasi sudah diberikan
     */
    fun checkLocationPermissions(): Boolean {
        val fineLocation = checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasLocationPermissions = fineLocation && coarseLocation

        Timber.d("$TAG: Location permissions check - Fine: $fineLocation, Coarse: $coarseLocation")
        return hasLocationPermissions
    }

    /**
     * Cek apakah permission background location sudah diberikan
     */
    fun checkBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }
    }

    /**
     * Cek apakah permission notifikasi sudah diberikan
     */
    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Cek status permission spesifik
     */
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // === Permission Request Methods ===

    private val requestSinglePermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val lastUsedListener = activePermissionResultListener
            lastRequestedPermission?.let { permission ->
                lastUsedListener?.onPermissionResult(permission, isGranted)
                Timber.d("$TAG: Single permission result - $permission: $isGranted")
            }
            resetState()
        }

    private val requestMultiplePermissionsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            activePermissionResultListener?.onPermissionsResult(permissions)
            Timber.d("$TAG: Multiple permissions result: $permissions")
            resetState()
        }

    private fun resetState() {
        activePermissionResultListener = null
        lastRequestedPermission = null
    }

    /**
     * Request single permission
     */
    fun requestPermission(permission: String, listener: PermissionResultListener) {
        Timber.d("$TAG: Requesting single permission: $permission")
        activePermissionResultListener = listener
        lastRequestedPermission = permission
        requestSinglePermissionLauncher.launch(permission)
    }

    /**
     * Request multiple permissions
     */
    fun requestPermissions(permissions: Array<String>, listener: PermissionResultListener) {
        Timber.d("$TAG: Requesting multiple permissions: ${permissions.joinToString()}")
        activePermissionResultListener = listener
        requestMultiplePermissionsLauncher.launch(permissions)
    }

    /**
     * Request location permissions (termasuk background jika diperlukan)
     */
    fun requestLocationPermissions(listener: PermissionResultListener, includeBackground: Boolean = false) {
        val permissions = if (includeBackground) {
            LOCATION_PERMISSIONS + BACKGROUND_LOCATION_PERMISSION
        } else {
            LOCATION_PERMISSIONS
        }
        requestPermissions(permissions, listener)
    }

    /**
     * Request notification permission
     */
    fun requestNotificationPermission(listener: PermissionResultListener? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listener?.let {
                requestPermission(Manifest.permission.POST_NOTIFICATIONS, it)
            } ?: requestSinglePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings()
        }
    }

    // === Settings Navigation Methods ===

    /**
     * Buka settings lokasi device
     */
    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Timber.i("$TAG: Opened location settings")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to open location settings")
        }
    }

    /**
     * Buka settings permission aplikasi
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Timber.i("$TAG: Opened app settings")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to open app settings")
        }
    }

    /**
     * Buka settings notifikasi aplikasi
     */
    private fun openAppNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Timber.i("$TAG: Opened notification settings")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to open notification settings")
        }
    }
}