package com.roxgps.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.roxgps.R
import com.roxgps.helper.AbiHelper
import com.roxgps.helper.PermissionHelper
import com.roxgps.helper.PermissionResultListener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnboardingPermissionViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val abiHelper: AbiHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel(), PermissionResultListener {

    private val _permissionStates = MutableLiveData<List<PermissionItemState>>()
    val permissionStates: LiveData<List<PermissionItemState>> = _permissionStates

    // Variabel launcher kini tidak diperlukan di ViewModel
    // Karena ViewModel hanya akan memanggil metode di PermissionHelper
    // yang sudah memiliki launcher yang diinisialisasi oleh Activity.

    init {
        // Panggil updatePermissionStates saat ViewModel dibuat
        // untuk menginisialisasi status awal dan memperbarui segera.
        updatePermissionStatesFromSystem()
    }

    // Metode setLaunchers tidak lagi diperlukan di ViewModel
    // karena launcher diatur langsung di BaseMapActivity.
    // Hapus method ini jika tidak ada lagi kode yang memanggilnya.
    /*
    fun setLaunchers(
        singleLauncherFn: ((String) -> Unit),
        multipleLauncherFn: ((Array<String>) -> Unit)
    ) {
        // Logika proxy launcher dihapus
        Timber.w("setLaunchers in OnboardingPermissionViewModel is deprecated/not needed with current PermissionHelper design.")
    }
    */

    /**
     * Menginisialisasi dan memperbarui status izin dari sistem Android.
     * Ini harus dipanggil saat ViewModel pertama kali dibuat dan saat ON_RESUME.
     */
    fun updatePermissionStatesFromSystem() {
        val packageName = appContext.packageName
        val currentStates = mutableListOf<PermissionItemState>()

        // 1. Item untuk Validasi ABI
        currentStates.add(
            PermissionItemState(
                permissionName = "Validate ABI",
                titleResId = R.string.validate_abi_title,
                descriptionResId = R.string.validate_abi_desc,
                isRequired = true,
                isGranted = abiHelper.isDeviceAbiSupported(),
                icon = null // Pastikan Anda memiliki icon yang relevan jika ini bukan null
            )
        )

        // 2. Item untuk Izin Lokasi (ACCESS_FINE_LOCATION & ACCESS_COARSE_LOCATION)
        // CheckLocationPermissions() di PermissionHelper Anda sudah mengembalikan true jika keduanya granted
        currentStates.add(
            PermissionItemState(
                permissionName = Manifest.permission.ACCESS_FINE_LOCATION, // Cukup salah satu untuk representasi
                titleResId = R.string.location_permission_title,
                descriptionResId = R.string.location_permission_desc,
                isRequired = true,
                isGranted = permissionHelper.checkLocationPermissions(),
                settingsIntent = PermissionItemState.appSettingsIntent(packageName)
            )
        )

        // 3. Item untuk Izin Lokasi Latar Belakang (ACCESS_BACKGROUND_LOCATION)
        // Karena minSdk 30, ini selalu relevan
        currentStates.add(
            PermissionItemState(
                permissionName = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                titleResId = R.string.background_location_permission_title,
                descriptionResId = R.string.background_location_permission_desc,
                isRequired = false, // Umumnya opsional, sesuaikan jika Anda ingin ini wajib
                isGranted = permissionHelper.checkBackgroundLocationPermission(),
                settingsIntent = PermissionItemState.appSettingsIntent(packageName)
            )
        )

        // 4. Item untuk Izin Notifikasi (POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentStates.add(
                PermissionItemState(
                    permissionName = Manifest.permission.POST_NOTIFICATIONS,
                    titleResId = R.string.notification_permission_title,
                    descriptionResId = R.string.notification_permission_desc,
                    isRequired = false, // Notifikasi umumnya opsional
                    isGranted = permissionHelper.checkNotificationPermission(),
                    settingsIntent = PermissionItemState.notificationSettingsIntent(packageName)
                )
            )
        } else {
            // Untuk Android 11 (minSdk 30) hingga Android 12L (API 32), POST_NOTIFICATIONS tidak diperlukan sebagai runtime permission
            currentStates.add(
                PermissionItemState(
                    permissionName = "Notifications (Pre-Tiramisu)", // Nama internal/informatif
                    titleResId = R.string.notification_permission_title,
                    descriptionResId = R.string.notification_permission_desc,
                    isRequired = false,
                    isGranted = true // Anggap granted karena tidak ada dialog runtime untuk ini
                )
            )
        }

        _permissionStates.value = currentStates.map { it.copy() }
        Timber.d("Updated permission states: ${_permissionStates.value}")
    }


    /**
     * Menangani aksi ketika pengguna mengklik item izin di UI.
     * Akan meminta izin atau mengarahkan ke pengaturan.
     */
    fun handlePermissionAction(state: PermissionItemState) {
        when (state.permissionName) {
            "Validate ABI" -> {
                Timber.d("ABI validation is an informational status. No direct action for 'Validate ABI' besides showing info.")
                // Mungkin tampilkan dialog info lebih lanjut di sini jika ABI tidak didukung
            }
            // Penanganan untuk izin Android runtime
            else -> {
                if (state.isGranted) {
                    Timber.d("${state.permissionName} already granted, attempting to open settings if available.")
                    state.settingsIntent?.let { intent ->
                        runCatching {
                            appContext.startActivity(intent)
                        }.onFailure { e ->
                            Timber.e(e, "Failed to open settings for ${state.permissionName}")
                            // Mungkin tampilkan Toast jika tidak bisa membuka settings
                        }
                    }
                } else {
                    Timber.d("Requesting permission: ${state.permissionName}")
                    when (state.permissionName) {
                        Manifest.permission.POST_NOTIFICATIONS -> {
                            // Cek versi SDK tetap di sini karena POST_NOTIFICATIONS hanya ada di API 33+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                // Panggil requestNotificationPermission tanpa listener
                                permissionHelper.requestNotificationPermission()
                            } else {
                                Timber.d("Notification permission (POST_NOTIFICATIONS) not applicable as runtime permission for this Android version.")
                                // Untuk API < 33, arahkan ke pengaturan notifikasi aplikasi secara langsung
                                // Ini bisa ditangani oleh PermissionHelper.openAppNotificationSettings() jika diinginkan
                                state.settingsIntent?.let { intent ->
                                    runCatching {
                                        appContext.startActivity(intent)
                                    }.onFailure { e ->
                                        Timber.e(e, "Failed to open settings for notifications (Pre-Tiramisu)")
                                    }
                                }
                            }
                        }
                        Manifest.permission.ACCESS_FINE_LOCATION -> {
                            // Minta lokasi depan, BACKGROUND_LOCATION akan diminta terpisah jika diperlukan
                            // Ini akan meminta FINE dan COARSE.
                            permissionHelper.requestLocationPermissions(includeBackground = false)
                        }
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION -> {
                            // Meminta izin lokasi latar belakang secara terpisah
                            permissionHelper.requestSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                        // Untuk izin Android runtime tunggal lainnya yang tidak secara eksplisit di atas
                        else -> {
                            if (state.permissionName.startsWith("android.permission.")) {
                                permissionHelper.requestSinglePermission(state.permissionName)
                            } else {
                                Timber.w("No specific runtime request logic for custom/informational permission: ${state.permissionName}. It might be handled by settings intent.")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Callback dari PermissionResultListener ketika satu izin diberikan/ditolak.
     */
    override fun onPermissionResult(permission: String, isGranted: Boolean) {
        Timber.d("ViewModel: onPermissionResult for $permission: $isGranted")
        updatePermissionStatesFromSystem() // Selalu perbarui semua status
    }

    /**
     * Callback dari PermissionResultListener ketika banyak izin diberikan/ditolak.
     */
    override fun onPermissionsResult(permissions: Map<String, Boolean>) {
        Timber.d("ViewModel: onPermissionsResult: $permissions")
        updatePermissionStatesFromSystem() // Selalu perbarui semua status
        val allRequiredGranted = _permissionStates.value?.filter { it.isRequired }?.all { it.isGranted } == true
        if (allRequiredGranted) {
            Timber.d("All required permissions are now granted! Onboarding can proceed.")
        }
    }

    /**
     * Meminta semua izin Android runtime yang wajib dan belum diberikan.
     */
    fun requestAllRequiredPermissions() {
        val requiredRuntimePermissionsToRequest = _permissionStates.value
            ?.filter {
                it.isRequired &&
                        !it.isGranted &&
                        it.permissionName.startsWith("android.permission.") // Hanya izin Android runtime
            }
            ?.map { it.permissionName }
            ?.toTypedArray() ?: emptyArray()

        if (requiredRuntimePermissionsToRequest.isNotEmpty()) {
            permissionHelper.requestMultiplePermissions(requiredRuntimePermissionsToRequest)
        } else {
            Timber.d("No required Android runtime permissions to request.")
            updatePermissionStatesFromSystem()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("OnboardingPermissionViewModel cleared.")
    }
}