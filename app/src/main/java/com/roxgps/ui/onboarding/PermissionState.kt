package com.roxgps.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes

// Data class untuk merepresentasikan status setiap izin
data class PermissionItemState(
    val permissionName: String,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    val isRequired: Boolean,
    var isGranted: Boolean,
    val icon: Int? = null, // <<< Diubah ke Int untuk resource ID
    var settingsIntent: Intent? = null // Opsional: Intent untuk membuka pengaturan spesifik
) {
    // Helper untuk membuat Intent ke pengaturan aplikasi jika dibutuhkan
    companion object {
        fun appSettingsIntent(packageName: String): Intent {
            return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun locationSettingsIntent(): Intent {
            return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun notificationSettingsIntent(packageName: String): Intent {
            return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}