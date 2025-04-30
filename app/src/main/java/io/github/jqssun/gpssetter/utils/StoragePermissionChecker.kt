package io.github.jqssun.gpssetter.utils

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import io.github.jqssun.gpssetter.R

class StoragePermissionChecker(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "StoragePermissionChecker"
        const val STORAGE_PERMISSION_REQUEST = 100
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyStoragePermission()
        }
    }

    private fun hasLegacyStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStoragePermission(onResult: (Boolean) -> Unit) {
        FileLogger.log("Meminta izin penyimpanan", TAG, "I")
        
        when {
            hasStoragePermission() -> {
                FileLogger.log("Izin penyimpanan sudah diberikan", TAG, "I")
                onResult(true)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                requestAndroid11StoragePermission()
                // Result will be handled in onActivityResult
            }
            else -> {
                requestLegacyStoragePermission(onResult)
            }
        }
    }

    private fun requestAndroid11StoragePermission() {
        try {
            FileLogger.log("Meminta izin MANAGE_EXTERNAL_STORAGE", TAG, "D")
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, STORAGE_PERMISSION_REQUEST)
        } catch (e: Exception) {
            FileLogger.log("Error saat meminta izin: ${e.message}", TAG, "E")
        }
    }

    private fun requestLegacyStoragePermission(onResult: (Boolean) -> Unit) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            STORAGE_PERMISSION_REQUEST
        )
    }

    fun onActivityResult(requestCode: Int) {
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            val hasPermission = hasStoragePermission()
            FileLogger.log(
                "Hasil permintaan izin: ${if (hasPermission) "Diberikan" else "Ditolak"}",
                TAG,
                if (hasPermission) "I" else "W"
            )
        }
    }
}