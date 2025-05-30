package com.roxgps.ui // Sesuaikan dengan package Activity Anda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Import ini
import com.roxgps.R // Import R
//import com.roxgps.ui.theme.YourAppNameTheme // Ganti dengan tema Compose Anda
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActivitySettings : ComponentActivity() { // Pastikan Activity Anda adalah AndroidEntryPoint

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Menggunakan tema Material3, ganti dengan tema aplikasi Anda jika ada
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        tokenText = stringResource(id = R.string.token_loading), // Mengambil string dari resources
                        onBackClick = { onBackPressedDispatcher.onBackPressed() }, // Menggunakan dispatcher untuk kembali
                        settingsContent = { modifier ->
                            // Di sini Anda panggil Composable yang berisi konten pengaturan Anda
                            // Contoh:
                            // MyRealSettingsComposable(modifier = modifier, viewModel = hiltViewModel())
                            DefaultSettingsContent(modifier = modifier) // Menggunakan contoh yang saya berikan
                        }
                    )
                }
            }
        }
    }
}