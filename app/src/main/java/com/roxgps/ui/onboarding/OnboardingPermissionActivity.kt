// ui/onboarding/OnboardingPermissionActivity.kt
package com.roxgps.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// HAPUS import ini jika Anda menggunakan hiltViewModel: import androidx.lifecycle.viewmodel.compose.viewModel
import com.roxgps.ui.theme.RoxGPSTheme // Gunakan tema aplikasi Anda (saya perbaiki dari RoxGPSTheme ke RoxgpsTheme sesuai manifest Anda)
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import androidx.hilt.navigation.compose.hiltViewModel // Import hiltViewModel yang benar

// Penting: Anda juga perlu mengimpor ini untuk LocalLifecycleOwner dan DisposableEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


@AndroidEntryPoint
class OnboardingPermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("OnboardingPermissionActivity onCreate.")

        setContent {
            RoxgpsTheme { // Gunakan tema aplikasi Anda sesuai manifest
                // Ganti viewModel() dengan hiltViewModel()
                val onboardingViewModel: OnboardingPermissionViewModel = hiltViewModel()

                // Penting: Panggil updatePermissionStatesFromSystem() saat Activity ini resume
                // Ini memastikan UI di Composable merefleksikan status izin yang aktual.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            Timber.d("OnboardingPermissionActivity ON_RESUME: Updating permission states.")
                            onboardingViewModel.updatePermissionStatesFromSystem()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                OnboardingPermissionScreen(
                    viewModel = onboardingViewModel,
                    onAllPermissionsGranted = {
                        Timber.d("OnboardingPermissionScreen: All permissions granted. Setting RESULT_OK.")
                        setResult(RESULT_OK)
                        finish() // Tutup Activity ini
                    },
                    onBackClick = {
                        Timber.i("User pressed back on OnboardingPermissionActivity. Setting RESULT_CANCELED.")
                        setResult(RESULT_CANCELED)
                        finish() // Tutup Activity ini
                    }
                )
            }
        }
    }
}