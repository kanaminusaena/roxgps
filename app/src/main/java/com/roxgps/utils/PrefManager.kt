package com.roxgps.utils // Pastikan package ini sesuai

// =====================================================================
// Import Library PrefManager (Versi Perbaikan untuk Hilt)
// =====================================================================

import android.content.Context // Untuk Context
import android.content.SharedPreferences // Untuk SharedPreferences
import androidx.appcompat.app.AppCompatDelegate // Untuk nilai default tema
import com.roxgps.BuildConfig // Untuk BuildConfig.APPLICATION_ID
// Import Hilt dan Coroutines untuk Injection
import dagger.hilt.android.qualifiers.ApplicationContext // Qualifier Hilt untuk Application Context
import kotlinx.coroutines.CoroutineScope // Untuk CoroutineScope
import kotlinx.coroutines.Dispatchers // Untuk Dispatchers
import kotlinx.coroutines.launch // Untuk launch coroutine
import javax.inject.Inject // Anotasi Inject
import javax.inject.Singleton // Anotasi Singleton
// Import Qualifier CoroutineScope (pastikan package-nya benar)
import com.roxgps.module.utils.ScopeQualifiers // Import anotasi qualifier @ApplicationScope


// =====================================================================
// Class PrefManager (@Singleton untuk Hilt)
// =====================================================================

/**
 * Helper class untuk mengelola shared preferences aplikasi.
 * Disediakan sebagai Singleton oleh Hilt.
 * Menggunakan Dependency Injection untuk Context dan CoroutineScope.
 * Menggunakan MODE_PRIVATE untuk keamanan.
 */
@Singleton // Hilt akan mengelola instance tunggal dari class ini selama aplikasi hidup
class PrefManager @Inject constructor( // Anotasi @Inject constructor untuk Hilt. Hilt akan memanggil constructor ini.
    // Minta Context level Application melalui injection Hilt
    @ApplicationContext private val context: Context,
    // Minta CoroutineScope level Aplikasi melalui injection Hilt
    // Gunakan qualifier @ApplicationScope yang sudah kita definisikan dan provide di SingletonModule.
    @ScopeQualifiers private val applicationScope: CoroutineScope
) {

    // =====================================================================
    // Konstanta Kunci Preference
    // =====================================================================
    // Gunakan companion object untuk konstanta di dalam class
    private companion object {
        private const val PREFS_FILE_NAME = "${BuildConfig.APPLICATION_ID}_prefs"
        private const val START = "start"
        private const val LATITUDE = "latitude"
        private const val LONGITUDE = "longitude"
        private const val HOOKED_SYSTEM = "system_hooked"
        private const val RANDOM_POSITION = "random_position"
        private const val ACCURACY_SETTING = "accuracy_level"
        private const val MAP_TYPE = "map_type"
        private const val DARK_THEME = "dark_theme"
        private const val DISABLE_UPDATE = "update_disabled"
        private const val ENABLE_JOYSTICK = "joystick_enabled"
    }


    // =====================================================================
    // SharedPreferences Instance
    // =====================================================================

    // Menginisialisasi SharedPreferences secara lazy dan AMAN
    private val pref: SharedPreferences by lazy {
        // Menggunakan Context yang di-inject.
        // MENGGUNAKAN MODE_PRIVATE UNTUK KEAMANAN!
        // File preference HANYA bisa diakses oleh aplikasi lo sendiri.
        context.getSharedPreferences( // Menggunakan Context yang di-inject
            PREFS_FILE_NAME,
            Context.MODE_PRIVATE // <-- MENGGUNAKAN MODE_PRIVATE (DIREKOMENDASIKAN & AMAN)
        )
        // Tidak perlu try-catch SecurityException lagi karena MODE_PRIVATE aman secara default.
    }


    // =====================================================================
    // Properti Akses Data Preference (Menggunakan Getter/Setter Custom)
    // =====================================================================
    // Menggunakan properti custom getter/setter untuk akses data dengan tipe yang sesuai.
    // Operasi baca (getter) dilakukan di thread pemanggil.
    // Operasi tulis (setter dan update) dilakukan di background thread menggunakan CoroutineScope yang di-inject.

    val isStarted : Boolean
        get() = pref.getBoolean(START, false)

    val getLat : Double
        get() = pref.getFloat(LATITUDE, 40.7128F).toDouble() // Potensi kehilangan presisi Double -> Float

    val getLng : Double
        get() = pref.getFloat(LONGITUDE, -74.0060F).toDouble() // Potensi kehilangan presisi Double -> Float

    // Properti dengan setter yang menulis ke preference di background via Coroutine Scope
    var isSystemHooked : Boolean
        get() = pref.getBoolean(HOOKED_SYSTEM, false)
        set(value) {
            applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putBoolean(HOOKED_SYSTEM, value).apply() // apply() untuk write async
            }
        }

    var isRandomPosition :Boolean
        get() = pref.getBoolean(RANDOM_POSITION, false)
        set(value) {
             applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putBoolean(RANDOM_POSITION, value).apply() // apply()
            }
        }

    var accuracy : String?
        get() = pref.getString(ACCURACY_SETTING,"10")
        set(value) {
             applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putString(ACCURACY_SETTING, value).apply() // apply()
            }
        }

    var mapType : Int
        get() = pref.getInt(MAP_TYPE,1)
        set(value) {
             applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putInt(MAP_TYPE, value).apply() // apply()
            }
        }

    var darkTheme: Int
        get() = pref.getInt(DARK_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = run { // Menggunakan run agar setter bisa pakai block
             applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putInt(DARK_THEME, value).apply() // apply()
            }
        }

    var isUpdateDisabled: Boolean
        get() = pref.getBoolean(DISABLE_UPDATE, false)
        set(value) {
             applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putBoolean(DISABLE_UPDATE, value).apply() // apply()
            }
        }

    var isJoystickEnabled: Boolean
        get() = pref.getBoolean(ENABLE_JOYSTICK, false)
        set(value) {
             applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
                pref.edit().putBoolean(ENABLE_JOYSTICK, value).apply() // apply()
            }
        }


    // =====================================================================
    // Metode Update Data Utama (dengan Background Thread)
    // =====================================================================

    /**
     * Mengupdate status START, latitude, dan longitude di SharedPreferences.
     * Operasi penulisan dilakukan di background thread menggunakan CoroutineScope yang di-inject.
     *
     * @param start Status START (true/false).
     * @param la Latitude (Double).
     * @param ln Longitude (Double).
     */
    fun update(start:Boolean, la: Double, ln: Double) {
        applicationScope.launch(Dispatchers.IO) { // Menggunakan CoroutineScope yang di-inject
            val prefEditor = pref.edit()
            prefEditor.putFloat(LATITUDE, la.toFloat()) // Potensi kehilangan presisi
            prefEditor.putFloat(LONGITUDE, ln.toFloat()) // Potensi kehilangan presisi
            prefEditor.putBoolean(START, start)
            prefEditor.apply() // apply() untuk write async
        }
    }

    // runInBackground tidak diperlukan lagi jika menggunakan scope yang di-inject
    // private fun runInBackground(method: suspend () -> Unit){ ... }

}
