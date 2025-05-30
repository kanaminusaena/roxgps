package com.roxgps.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roxgps.R
import com.roxgps.ui.theme.RoxGPSTheme
import timber.log.Timber

// @AndroidEntryPoint jika Activity ini adalah Hilt-enabled
class OnboardingPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoxGPSTheme {
                OnboardingPermissionScreen(
                    onContinue = { /* TODO: Navigasi ke Home Screen */ Timber.d("Continue clicked") },
                    onGrantAll = { /* TODO: Trigger request all permissions */ Timber.d("Grant All clicked") }
                )
            }
        }
    }
}

@Composable
fun OnboardingPermissionScreen(
    onContinue: () -> Unit,
    onGrantAll: () -> Unit,
    viewModel: OnboardingPermissionViewModel = hiltViewModel() // Menggunakan Hilt ViewModel
) {
    // Observe state dari ViewModel
    val permissionStates by viewModel.permissionStates.observeAsState(initial = emptyList())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Untuk memungkinkan scrolling
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header Section
            Image(
                painter = painterResource(id = R.drawable.ic_app_icon_izanami), // Ganti dengan ikon aplikasi Anda
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.welcome_to_izanami), // "Welcome to DataBackup"
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_description_onboarding), // "Free and open-source data backup application"
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Required Permissions Section
            Text(
                text = stringResource(R.string.required_permissions_title), // "Required"
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            permissionStates.filter { it.isRequired }.forEach { permission ->
                PermissionItem(
                    permissionState = permission,
                    onSettingsClick = { state ->
                        viewModel.handlePermissionAction(state)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Optional Permissions Section
            Text(
                text = stringResource(R.string.optional_permissions_title), // "Optional"
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            permissionStates.filter { !it.isRequired }.forEach { permission ->
                PermissionItem(
                    permissionState = permission,
                    onSettingsClick = { state ->
                        viewModel.handlePermissionAction(state)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // Dorong tombol ke bawah

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onGrantAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.grant_all_button)) // "Grant all"
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    enabled = permissionStates.all { !it.isRequired || it.isGranted } // Enable hanya jika semua wajib sudah diberikan
                ) {
                    Text(stringResource(R.string.continue_button)) // "Continue"
                }
            }
        }
    }
}