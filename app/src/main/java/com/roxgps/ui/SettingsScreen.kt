package com.roxgps.ui // Sesuaikan dengan package UI Anda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.roxgps.R

// --- Komponen Composable untuk Layar Pengaturan ---

@OptIn(ExperimentalMaterial3Api::class) // Diperlukan untuk TopAppBar scroll behavior
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    tokenText: String, // String untuk token yang akan ditampilkan di bawah
    onBackClick: () -> Unit,
    // Ini adalah Composable konten utama untuk pengaturan,
    // yang akan menggantikan FragmentContainerView Anda.
    // Contoh: SettingsContent() jika Anda membuat Composable terpisah untuk pengaturan.
    settingsContent: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Menggunakan TopAppBar standar dari Material 3
            // Untuk CoordinatorLayout scrolling behavior, Anda bisa menggunakan TopAppBarScrollBehavior
            // Namun, untuk contoh sederhana ini, kita akan membuatnya statis terlebih dahulu
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_app))
                    }
                },
                // elevation pada TopAppBar Material3 diberikan melalui parameter colors atau behavior
                // Defaultnya sudah memiliki shadow. Untuk menghilangkan, sesuaikan warna atau behavior
                // Misalnya: colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            // TextView di bagian bawah
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface) // Menggunakan warna permukaan tema
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tokenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { paddingValues ->
        // Konten utama, yang menggantikan FragmentContainerView
        // Menggunakan Modifier.padding dari Scaffold untuk menghindari tumpang tindih dengan Top/Bottom bar
        settingsContent(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

// --- Contoh Konten Pengaturan (Menggantikan Fragment) ---
// Ini adalah contoh bagaimana Anda bisa membangun UI preferensi langsung di Compose.
// Anda akan mengisi ini dengan item pengaturan Anda (Switch, Slider, dll.)
@Composable
fun DefaultSettingsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Settings Content Goes Here",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This replaces your PreferenceFragmentCompat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        // Tambahkan komponen pengaturan Compose di sini
        // Misalnya:
        // Switch(checked = true, onCheckedChange = {})
        // Slider(value = 0.5f, onValueChange = {})
    }
}


// --- Preview Composable ---
@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    MaterialTheme { // Pastikan menggunakan tema Compose Anda
        SettingsScreen(
            tokenText = stringResource(id = R.string.token_loading),
            onBackClick = { /* No-op for preview */ },
            settingsContent = { modifier -> DefaultSettingsContent(modifier) }
        )
    }
}