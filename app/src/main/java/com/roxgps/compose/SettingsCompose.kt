package com.roxgps.compose // Sesuaikan package jika perlu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.roxgps.R

// Ini adalah Composable yang merepresentasikan layout ActivitySettings
@OptIn(ExperimentalMaterial3Api::class) // Anotasi untuk Material 3 TopAppBar
@Composable
fun SettingsCompose(
	// Parameter yang dibutuhkan, misal:
	// - Token (untuk ditampilkan di bawah)
	token: String? = null,
	// - Callback untuk navigasi kembali
	onNavigateBack: () -> Unit = {},
	// - Konten utama AppSettings (ini akan diganti dengan Composable settings)
	appSettingsContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
		// Placeholder untuk konten settings (yang tadinya FragmentContainerView)
		Box(modifier = Modifier
			.padding(paddingValues)
			.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			Text("AppSettings Content Goes Here (Replace Fragment)")
		}
	}
) {
	// Scaffold menyediakan struktur dasar Material Design (AppBar, FloatingActionButton, dll.)
	Scaffold(
		topBar = {
			// TopAppBar menggantikan MaterialToolbar
			TopAppBar(
				title = {
					Text(stringResource(R.string.settings)) // Menggunakan string resource
				},
				navigationIcon = {
					// Tombol navigasi kembali
					IconButton(onClick = onNavigateBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = stringResource(R.string.back_to_app) // Gunakan string resource
						)
					}
				},
				// Hapus elevation jika ingin rata dengan konten seperti di XML
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surfaceContainer // Warna AppBar
				)
			)
		},
		// contentPadding: Padding yang diberikan oleh Scaffold untuk menghindari AppBar
		content = { paddingValues ->
			// Box untuk menumpuk konten utama dan TextView di bawah
			Box(modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues) // Terapkan padding dari Scaffold
			) {
				// Konten utama settings (placeholder)
				// Parameter appSettingsContent akan dipanggil di sini
				appSettingsContent(paddingValues) // Meneruskan padding ke konten settings

				// TextView Token di bagian bawah
				// Menggunakan Column atau Row jika perlu menata lebih dari satu elemen
				Column(
					modifier = Modifier
						.align(Alignment.BottomCenter) // layout_gravity="bottom|center_horizontal"
						.fillMaxWidth() // layout_width="match_parent"
						.wrapContentHeight() // layout_height="wrap_content"
						.background(MaterialTheme.colorScheme.surfaceContainer) // background="?attr/colorSurface"
						.shadow(4.dp) // elevation="4dp" - Perlu import androidx.compose.ui.graphics.graphicsLayer atau gunakan Modifier.elevation
						.padding(16.dp) // padding="16dp"
						.navigationBarsPadding() // Menghindari navigation bar di bawah
						.imePadding(), // Menghindari keyboard
					horizontalAlignment = Alignment.CenterHorizontally // gravity="center_horizontal"
				) {
					Text(
						text = "Token: ${token ?: stringResource(R.string.token_loading)}", // Tampilkan token atau teks loading
						textAlign = TextAlign.Center, // gravity="center_horizontal" untuk teks
						style = MaterialTheme.typography.bodyMedium // textAppearance="?attr/textAppearanceBodyMedium"
					)
				}
			}
		}
	)
}

// Fungsi Preview untuk melihat tampilan Composable di Android Studio
@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
	// Contoh preview dengan token dummy
	SettingsCompose(token = "contoh_token_12345...")
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreenLoading() {
	// Contoh preview saat token masih loading
	SettingsCompose(token = null)
}
