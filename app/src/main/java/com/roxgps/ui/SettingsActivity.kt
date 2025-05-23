package com.roxgps.ui

// =====================================================================
// Import Library
// =====================================================================
// Hapus import yang tidak lagi diperlukan untuk UI XML/Fragment
// import android.app.ActivityManager
// import android.provider.AppSettings
// import android.text.Editable
// import android.text.InputType
// import android.text.TextWatcher
// import android.text.method.DigitsKeyListener
// import android.widget.EditText
// import android.widget.TextView
// import android.widget.Toast
// import androidx.appcompat.app.AppCompatDelegate // Tidak perlu jika tema diatur di Compose MaterialTheme
// import androidx.core.net.toUri // Tidak perlu jika logic askOverlayPermission dipindahkan
// Hapus import PreferenceFragmentCompat dan terkait jika tidak lagi digunakan
// import androidx.preference.DropDownPreference
// import androidx.preference.EditTextPreference
// import androidx.preference.Preference
// import androidx.preference.PreferenceDataStore
// import androidx.preference.PreferenceFragmentCompat
// Hapus binding XML jika tidak lagi digunakan
// import com.roxgps.databinding.ActivitySettingsBinding
// import com.roxgps.utils.JoystickService // Tidak perlu jika logic service dipindahkan
// import com.roxgps.utils.ext.showToast // Tidak perlu jika Toast dihandle di Composable atau ViewModel Event

// Import Compose related
// Import CoroutineScope dan launch jika masih ada observasi di Activity (selain di setContent)
//import com.roxgps.compose.SettingsCompose
//import com.roxgps.compose.SettingsPreferencesComposable
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.roxgps.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// =====================================================================
// ActivitySettings (@AndroidEntryPoint) - Menggunakan Compose UI
// =====================================================================

@AndroidEntryPoint // Anotasi ini diperlukan agar Hilt bisa menginjeksi dependensi ke Activity ini
class ActivitySettings : AppCompatActivity() {

    // Dapatkan instance ViewModel menggunakan delegate Hilt
    private val viewModel: MainViewModel by viewModels() // Inject ViewModel

    // PrefManager tidak perlu di-inject langsung di Activity ini jika hanya digunakan oleh Fragment/Composable.
    // Jika Activity juga perlu akses PrefManager, maka uncomment baris ini.
    // @Inject
    // lateinit var prefManager: PrefManager

    // Binding View dan View XML tidak diperlukan lagi jika menggunakan Compose setContent
    // private lateinit var textViewToken: TextView // Contoh TextView untuk menampilkan token
    // private val binding by lazy { ActivitySettingsBinding.inflate(layoutInflater) }


    // =====================================================================
    // onCreate Method - Menggunakan setContent untuk Compose UI
    // =====================================================================

    @SuppressLint("SetTextI18n") // Anotasi ini mungkin tidak lagi relevan jika teks di Compose
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        // Mengganti setContentView(binding.root) dengan setContent untuk Compose UI
        /*setContent {
            // Menggunakan tema Material3 dari aplikasi
            MaterialTheme { // Tema Material3 dari Compose
                // Surface sebagai container utama dengan warna background tema
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Mengamati StateFlow token dari ViewModel
                    val token by viewModel.token.collectAsState(initial = null)

                    // Memanggil Composable SettingsScreenCompose
                    SettingsCompose(
                        token = token, // Meneruskan token
                        onNavigateBack = {
                            // Aksi saat tombol back di AppBar diklik
                            onBackPressedDispatcher.onBackPressed()
                        },
                        appSettingsContent = { paddingValues ->
                            // Memanggil Composable pengganti fragment settings
                            // Meneruskan paddingValues dari Scaffold agar konten tidak tertutup AppBar/TextView bawah
                            SettingsPreferencesComposable(
                                // ViewModel tidak perlu diteruskan secara eksplisit
                                // jika SettingsPreferencesComposable menggunakan hiltViewModel()
                            )
                        }
                    )
                }
            }
        }*/

        // Kode lama untuk Fragment dan View XML dihapus
        // setContentView(binding.root) // Hapus ini
        // theme.applyStyle(...) // Hapus ini jika tema diatur di Compose
        // setSupportActionBar(binding.toolbar) // Hapus ini, AppBar di Compose
        // if (savedInstanceState == null) { ... } // Hapus ini, Fragment tidak digunakan
        // supportActionBar?.setDisplayHomeAsUpEnabled(true) // Hapus ini, navigasi di Compose

        // onBackPressedDispatcher.addCallback(...) tetap di sini jika Activity perlu menangani tombol back secara kustom

        // Inisialisasi View (misal findViewByID) tidak diperlukan lagi
        // textViewToken = findViewById(R.id.textViewGojekToken) // Hapus ini

        // Mengamati StateFlow dari ViewModel (dari Repositories) - Observasi sekarang dilakukan di dalam Composable
        // Kecuali jika ada event atau state yang perlu ditangani di level Activity (misal navigasi, dialog global)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Amati saat Activity/Fragment STARTED
                viewModel.isModuleHooked.collect { isHooked ->
                    // Update UI berdasarkan status hook - Logic ini sebaiknya di Composable
                    // Log.d("SettingsActivity", "Module hooked status: $isHooked")
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastHookError.collect { errorMsg ->
                    // Tampilkan error di UI (misal Toast, Snackbar, atau TextView error)
                    // Logic ini sebaiknya di Composable atau ViewModel Event
                    // Log.e("SettingsActivity", "Hook error: $errorMsg")
                }
            }
        }

        // Mengamati Token dari ViewModel - Observasi sekarang dilakukan di dalam setContent
        /*
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.token.collect { token ->
                    // Update UI dengan token - Logic ini sekarang di Composable
                }
            }
        }
        */
        // TODO: Tambahkan pengamatan StateFlow atau event lain dari ViewModel (jika ada event yang perlu ditangani di Activity, bukan di Composable)

    }

    // TODO: Tambahkan method untuk trigger fetchToken() dari UI (misal di tombol refresh)
    // fun onRefreshTokenButtonClick() {
    //     viewModel.fetchToken() // Panggil fetchToken() dari ViewModel
    // }

    // TODO: Tambahkan interaksi UI lainnya

    // onOptionsItemSelected tidak diperlukan lagi jika AppBar diimplementasikan di Compose
    // override fun onOptionsItemSelected(item: MenuItem): Boolean { ... } // Hapus ini


    // =====================================================================
    // SettingsPreferenceFragment (@AndroidEntryPoint) - DIHAPUS
    // =====================================================================
    // Fragment ini digantikan oleh SettingsPreferencesComposable
    // Hapus seluruh definisi class SettingsPreferenceFragment
    /*
    @AndroidEntryPoint
    class SettingsPreferenceFragment : PreferenceFragmentCompat() {

        @Inject
        lateinit var prefManager: PrefManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager?.preferenceDataStore = SettingPreferenceDataStore(
                prefManager,
                lifecycleScope
            )
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<EditTextPreference>("accuracy_level")?.let { pref ->
                lifecycleScope.launch {
                    prefManager.accuracyLevel.collect { accuracy ->
                        pref.summary = "$accuracy m"
                    }
                }
                pref.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER
                    editText.keyListener = DigitsKeyListener.getInstance("0123456789.,")
                    editText.addTextChangedListener(getCommaReplacerTextWatcher(editText))
                }
                pref.setOnPreferenceChangeListener { preference, newValue ->
                    try {
                    } catch (n: NumberFormatException) {
                        n.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.enter_valid_input),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnPreferenceChangeListener false
                    }
                    true
                }
            }

            findPreference<DropDownPreference>("dark_theme")?.let { pref ->
                pref.setOnPreferenceChangeListener { _, newValue ->
                    val newMode = (newValue as String).toInt()
                    if (prefManager.darkTheme.value != newMode) {
                        AppCompatDelegate.setDefaultNightMode(newMode)
                        activity?.recreate()
                    }
                    true
                }
            }

            findPreference<Preference>("joystick_enabled")?.let { pref ->
                lifecycleScope.launch {
                     prefManager.isJoystickEnabled.collect { isEnabled ->
                         pref.summary = if (isEnabled) "Joystick enabled" else "Joystick disabled"
                     }
                 }
                pref.setOnPreferenceClickListener {
                    if (askOverlayPermission()){
                        if (isJoystickRunning()) {
                            requireContext().stopService(Intent(context,JoystickService::class.java))
                            lifecycleScope.launch { prefManager.setJoystickEnabled(false) }
                        } else if (prefManager.isStarted.value) {
                            requireContext().startService(Intent(context,JoystickService::class.java))
                            lifecycleScope.launch { prefManager.setJoystickEnabled(true) }
                        } else {
                            requireContext().showToast(requireContext().getString(R.string.location_not_select))
                        }
                    }
                    true
                }
            }

             findPreference<Preference>("system_hooked")?.let { pref ->
                  lifecycleScope.launch {
                      prefManager.isSystemHooked.collect { isHooked ->
                          pref.summary = if (isHooked) "System hooked" else "System not hooked"
                      }
                  }
             }

             findPreference<Preference>("random_position")?.let { pref ->
                  lifecycleScope.launch {
                      prefManager.isRandomPosition.collect { isRandom ->
                          pref.summary = if (isRandom) "Random position enabled" else "Random position disabled"
                      }
                  }
             }

             findPreference<Preference>("update_disabled")?.let { pref ->
                  lifecycleScope.launch {
                      prefManager.isUpdateDisabled.collect { isDisabled ->
                          pref.summary = if (isDisabled) "Update disabled" else "Update enabled"
                      }
                  }
             }
        }

        // Metode helper (tetap di dalam Fragment atau pindahkan jika digunakan di luar)
        private fun isJoystickRunning(): Boolean {
            var isRunning = false
            val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager? ?: return false
            // getRunningServices is deprecated. A better approach for checking if your own service is running
            // is to use a static flag in the service or bind to the service.
            // This deprecated approach should be replaced in the Compose implementation.
            for (service in manager.getRunningServices(Int.MAX_VALUE)) { // <<< Deprecated method call
                if ("com.roxgps.utils.JoystickService" == service.service.className) {
                    isRunning = true
                }
            }
            return isRunning
        }

        private fun askOverlayPermission() : Boolean {
            if (AppSettings.canDrawOverlays(requireContext())){
                return true
            }
            val intent = Intent(AppSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${requireContext().applicationContext?.packageName}".toUri()) // <<< Gunakan requireContext()
            requireContext().startActivity(intent) // <<< Gunakan requireContext()
            return false
        }

        private fun getCommaReplacerTextWatcher(editText: EditText): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun onTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()
                    if (text.contains(",")) {
                        editText.setText(text.replace(",", "."))
                        editText.setSelection(editText.text.length)
                    }
                }
            }
        }
    }
    */
}

// =====================================================================
// SettingPreferenceDataStore - DIHAPUS (Tidak diperlukan lagi untuk Compose Preferences)
// =====================================================================
// Class ini tidak diperlukan lagi karena Composable Preferences berinteraksi
// langsung dengan PrefManager (DataStore) melalui ViewModel.
// Hapus seluruh definisi class SettingPreferenceDataStore
/*
class SettingPreferenceDataStore(
    private val prefManager: PrefManager,
    private val coroutineScope: CoroutineScope // <<< Terima CoroutineScope di sini
) : PreferenceDataStore() {

    // --- Metode GET (Membaca dari StateFlow PrefManager) ---
    // Gunakan .value untuk mendapatkan nilai sinkron dari StateFlow
    override fun getBoolean(key: String?, defValue: Boolean): Boolean { // Hapus defValue, gunakan default dari StateFlow PrefManager
        return when (key) {
            "system_hooked" -> prefManager.isSystemHooked.value // <<< Gunakan .value
            "random_position" -> prefManager.isRandomPosition.value // <<< Gunakan .value
            "update_disabled" -> prefManager.isUpdateDisabled.value // <<< Gunakan .value
            "joystick_enabled" -> prefManager.isJoystickEnabled.value // <<< Gunakan .value
            // Default value diambil dari StateFlow PrefManager, jadi tidak perlu defValue di sini.
            // Jika key tidak cocok, ini akan throw exception, sesuaikan jika perlu default lain.
            else -> defValue // <<< Gunakan defValue jika key tidak cocok
        }
    }

    override fun getInt(key: String?, defValue: Int): Int { // Tambahkan getInt untuk map_type dan dark_theme
        return when (key) {
            "map_type" -> prefManager.mapType.value // <<< Gunakan .value
            "dark_theme" -> prefManager.darkTheme.value // <<< Gunakan .value
            else -> defValue // <<< Gunakan defValue jika key tidak cocok
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        return when (key) {
            "accuracy_level" -> prefManager.accuracyLevel.value // <<< Gunakan .value (nama properti accuracyLevel)
            // map_type dan dark_theme sekarang ditangani di getInt
            else -> defValue // <<< Gunakan defValue jika key tidak cocok
        }
    }

    // Hapus getFloat, getLong, dll. jika tidak digunakan di preferences.xml

    // --- Metode PUT (Menulis ke PrefManager suspend fun) ---
    // Luncurkan coroutine untuk memanggil suspend fun di PrefManager
    override fun putBoolean(key: String?, value: Boolean) {
        coroutineScope.launch { // <<< Luncurkan coroutine
            when (key) {
                "system_hooked" -> prefManager.setSystemHooked(value) // <<< Panggil suspend fun
                "random_position" -> prefManager.setRandomPosition(value) // <<< Panggil suspend fun
                "update_disabled" -> prefManager.setUpdateDisabled(value) // <<< Panggil suspend fun
                "joystick_enabled" -> prefManager.setJoystickEnabled(value) // <<< Panggil suspend fun
                else -> {} // Handle unknown key silently or log a warning
            }
        }
    }

    override fun putInt(key: String?, value: Int) { // Tambahkan putInt
        coroutineScope.launch { // <<< Luncurkan coroutine
            when (key) {
                "map_type" -> prefManager.setMapType(value) // <<< Panggil suspend fun
                "dark_theme" -> prefManager.setDarkTheme(value) // <<< Panggil suspend fun
                else -> {} // Handle unknown key silently or log a warning
            }
        }
    }

    override fun putString(key: String?, value: String?) {
        coroutineScope.launch { // <<< Luncurkan coroutine
            when (key) {
                "accuracy_level" -> prefManager.setAccuracyLevel(value ?: "10") // <<< Panggil suspend fun (handle null)
                // map_type dan dark_theme sekarang ditangani di putInt
                else -> {} // Handle unknown key silently or log a warning
            }
        }
    }

    // Hapus putFloat, putLong, dll. jika tidak digunakan di preferences.xml
}
*/
