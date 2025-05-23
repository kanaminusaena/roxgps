// src/foss/java/com/roxgps/module/ActivityModule.kt
// ATAU
// src/maplibre/java/com/roxgps/module/ActivityModule.kt
package com.roxgps.module

// import android.location.LocationManager // Hapus jika sudah dipindahkan ke AppModule
// import com.roxgps.helper.ILocationHelper // Hapus
// import com.roxgps.repository.SettingsRepository // Hapus jika tidak ada lagi @Provides di sini
// import com.roxgps.utils.PrefManager // Hapus jika tidak ada lagi @Provides di sini
// import com.roxgps.xposed.IXposedHookManager // Hapus
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

// import javax.inject.Named // Hapus jika tidak ada lagi @Named yang digunakan
// import kotlin.random.Random // Hapus jika sudah dipindahkan ke AppModule

/**
 * Hilt Module untuk menyediakan dependencies di scope Activity.
 * Hanya berisi dependensi yang BENAR-BENAR Activity-scoped untuk flavor ini.
 */
@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    private const val TAG = "ActivityModule"

    /**
     * Menyediakan instance PermissionHelper untuk Activity.
     * Ini bisa tetap di ActivityModule jika Anda ingin PermissionHelper spesifik untuk Activity.
     * Jika ILocationHelper (sekarang Singleton) juga membutuhkan PermissionHelper,
     * maka PermissionHelper juga harus disediakan sebagai Singleton di AppModule.
     * Jadi, Anda perlu memutuskan: Apakah PermissionHelper ini hanya untuk Activity?
     * Jika ILocationHelper Singleton yang baru bergantung padanya, maka ini harus menjadi Singleton.
     * Solusi terbaik: Pindahkan ini juga ke AppModule (Singleton) agar konsisten.
     */
    /*@Provides
    @ActivityScoped // <-- Jika ini tetap di sini, artinya PermissionHelper ini hanya untuk Activity.
    fun providePermissionHelper(
        @ActivityContext context: Context
    ): PermissionHelper {
        Relog.d("$TAG: Providing PermissionHelper")
        return PermissionHelper(context)
    }*/

    // --- Berikut adalah provider yang SEHARUSNYA SUDAH DIHAPUS dari ActivityModule Anda ---

    // provideLocationManager: HAPUS! (Sudah dipindahkan ke AppModule sebagai Singleton)
    /*
    @Provides
    @ActivityScoped
    fun provideLocationManager(...) { ... }
    */

    // provideRandom: HAPUS! (Sudah dipindahkan ke AppModule sebagai Singleton)
    /*
    @Provides
    @ActivityScoped
    fun provideRandom() { ... }
    */

    // provideLocationHelper: HAPUS! (Sudah dipindahkan ke FlavorSpecificLocationModule sebagai Singleton)
    /*
    @Provides
    @ActivityScoped
    fun provideLocationHelper(...) { ... }
    */

    // provideIsGooglePlayAvailable: HAPUS! (Sudah dipindahkan ke AppModule sebagai Singleton)
    /*
    @Provides
    @Named("isGooglePlayAvailable")
    fun provideIsGooglePlayAvailable(...) { ... }
    */

    // provideXposedHookManager: HAPUS! (Sudah ada di DatabaseModule sebagai Singleton)
    /*
    @Provides
    @ActivityScoped
    fun provideXposedHookManager(...) { ... }
    */
}