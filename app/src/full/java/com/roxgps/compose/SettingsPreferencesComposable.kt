package com.roxgps.compose // Sesuaikan package

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roxgps.R
import com.roxgps.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// Ini adalah Composable yang akan menggantikan SettingsPreferenceFragment
// Bertanggung jawab menampilkan UI untuk setiap preferensi dan berinteraksi dengan ViewModel/PrefManager
@Composable
fun SettingsPreferencesComposable(
	// ViewModel akan di-inject secara otomatis oleh Hilt jika menggunakan hiltViewModel()
	viewModel: MainViewModel = viewModel()
) {
	// Ambil CoroutineScope yang terkait dengan Composable ini
	val coroutineScope = rememberCoroutineScope()
	val context = LocalContext.current // Ambil Context jika diperlukan (misal untuk Toast)

	// --- Mengamati StateFlow dari ViewModel (yang berasal dari PrefManager) ---
	// Gunakan collectAsStateWithLifecycle untuk mengamati StateFlow dengan lifecycle-aware
	val isSystemHooked by viewModel.isSystemHooked.collectAsStateWithLifecycle()
	val isRandomPosition by viewModel.isRandomPosition.collectAsStateWithLifecycle()
	val accuracyLevel by viewModel.accuracyLevel.collectAsStateWithLifecycle() // Tipe String
	val mapType by viewModel.mapType.collectAsStateWithLifecycle()
	val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle() // Tipe Int
	val isUpdateDisabled by viewModel.isUpdateDisabled.collectAsStateWithLifecycle()
	val isJoystickEnabled by viewModel.isJoystickEnabled.collectAsStateWithLifecycle()
	val isStarted by viewModel.isStarted.collectAsStateWithLifecycle() // Perlu juga status 'start' untuk logic joystick

	val modifier: Modifier = Modifier.fillMaxWidth()
	val thickness: Dp = 1.dp
	val color: Color = MaterialTheme.colorScheme.outlineVariant // Contoh: warna abu-abu tipis dari tema

	// --- Tata Letak Preferensi ---
	// Gunakan Column untuk menata preferensi secara vertikal
	Column(modifier = Modifier
		.fillMaxSize()
		.padding(16.dp) // Padding keseluruhan
	) {
		// TODO: Implementasikan UI untuk setiap preferensi
		// Ini hanyalah contoh placeholder untuk menunjukkan bagaimana membaca dan menulis nilai
		// Kamu perlu mengganti ini dengan Composable UI yang sesuai (Toggle, Slider, Dropdown, dll.)

		// Contoh Toggle Preference (System Hooked)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(stringResource(R.string.pref_system_hooked_title)) // Judul preferensi
			Switch(
				checked = isSystemHooked, // Baca nilai dari StateFlow
				onCheckedChange = { newValue ->
					// Tulis nilai baru menggunakan suspend fun di ViewModel (yang panggil PrefManager)
					coroutineScope.launch {
						viewModel.updateSystemHooked(newValue)
					}
				}
			)
		}
		HorizontalDivider(modifier, thickness, color) // Garis pemisah

		// Contoh Toggle Preference (Random Position)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(stringResource(R.string.pref_random_position_title))
			Switch(
				checked = isRandomPosition,
				onCheckedChange = { newValue ->
					coroutineScope.launch {
						viewModel.updateRandomPosition(newValue)
					}
				}
			)
		}
		HorizontalDivider(modifier, thickness, color)

		// Contoh EditText Preference (Accuracy Level)
		// Untuk EditTextPreference, biasanya butuh dialog atau Composable yang lebih kompleks
		// Ini contoh sederhana menampilkan nilai dan tombol untuk memicu dialog (dialognya perlu diimplementasikan)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Column {
				Text(stringResource(R.string.pref_accuracy_level_title))
				Text("Current: $accuracyLevel m", style = MaterialTheme.typography.bodySmall) // Tampilkan nilai saat ini
			}
			Button(onClick = {
				// TODO: Tampilkan dialog EditText untuk mengubah nilai accuracyLevel
				// Contoh: showEditTextDialog(currentValue = accuracyLevel) { newValue -> coroutineScope.launch { viewModel.updateAccuracyLevel(newValue) } }
			}) {
				Text("Edit")
			}
		}
		HorizontalDivider(modifier, thickness, color)

		// Contoh DropDown Preference (Map Type)
		// Untuk DropDownPreference juga butuh Composable yang lebih kompleks (misal ExposedDropdownMenu)
		// Ini contoh sederhana menampilkan nilai dan tombol untuk memicu dialog (dialognya perlu diimplementasikan)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Column {
				Text(stringResource(R.string.pref_map_type_title))
				Text("Current: $mapType", style = MaterialTheme.typography.bodySmall) // Tampilkan nilai saat ini
			}
			Button(onClick = {
				// TODO: Tampilkan dialog DropDown untuk memilih mapType
				// Contoh: showDropDownDialog(options = mapTypeOptions, currentValue = mapType) { newValue -> coroutineScope.launch { viewModel.updateMapType(newValue) } }
			}) {
				Text("Change")
			}
		}
		HorizontalDivider(modifier, thickness, color)

		// Contoh DropDown Preference (Dark Theme)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Column {
				Text(stringResource(R.string.pref_dark_theme_title))
				Text("Current Mode: $darkTheme", style = MaterialTheme.typography.bodySmall) // Tampilkan nilai saat ini
			}
			Button(onClick = {
				// TODO: Tampilkan dialog DropDown untuk memilih darkTheme
				// Contoh: showDropDownDialog(options = themeOptions, currentValue = darkTheme) { newValue -> coroutineScope.launch { viewModel.updateDarkTheme(newValue) } }
			}) {
				Text("Change")
			}
		}
		HorizontalDivider(modifier, thickness, color)

		// Contoh Toggle Preference (Update Disabled)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(stringResource(R.string.pref_update_disabled_title))
			Switch(
				checked = isUpdateDisabled,
				onCheckedChange = { newValue ->
					coroutineScope.launch {
						viewModel.updateDisabled(newValue)
					}
				}
			)
		}
		HorizontalDivider(modifier, thickness, color)

		// Contoh Toggle Preference (Joystick Enabled)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(stringResource(R.string.pref_joystick_enabled_title))
			Switch(
				checked = isJoystickEnabled,
				onCheckedChange = { newValue ->
					coroutineScope.launch {
						viewModel.updateJoystickEnabled(newValue)
						// TODO: Tambahkan logic start/stop JoystickService di sini atau panggil method di ViewModel
						// yang akan memicu start/stop service berdasarkan nilai newValue dan isStarted.value
						// if (newValue && isStarted) {
						//     // Start service
						// } else {
						//     // Stop service
						// }
					}
				}
			)
		}
		HorizontalDivider(modifier, thickness, color)

		// TODO: Tambahkan preferensi lain sesuai preferences.xml
		// system_hooked, random_position, update_disabled, joystick_enabled

		// Catatan: Untuk preferensi yang memicu aksi (misal Joystick Enabled),
		// logic aksinya (start/stop service) sebaiknya di ViewModel atau Helper yang dipanggil ViewModel,
		// bukan langsung di Composable ini. Composable hanya memanggil ViewModel untuk update state.
	}
}
