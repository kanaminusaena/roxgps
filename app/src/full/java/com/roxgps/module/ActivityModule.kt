// com/roxgps/module/ActivityModule.kt (Setelah Perubahan)
package com.roxgps.module

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * Hilt Module untuk menyediakan dependencies di scope Activity.
 */
@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    private const val TAG = "ActivityModule"

    /**
     * Menyediakan instance PermissionHelper untuk Activity.
     */
    /*@Provides
    @ActivityScoped
    fun providePermissionHelper(
        @ActivityContext context: Context
    ): PermissionHelper {
        Relog.d("$TAG: Providing PermissionHelper")
        return PermissionHelper(context)
    }*/

    // CATATAN: provideLocationManager dan provideRandom dipindahkan ke AppModule.kt

    // HAPUS provideLocationHelper dari sini:
    /*
    @Provides
    @ActivityScoped
    fun provideLocationHelper(
        @ActivityContext context: Context,
        settingsRepository: SettingsRepository,
        prefManager: PrefManager,
        locationManager: LocationManager,
        random: Random,
        xposedHookManager: IXposedHookManager,
        @Named("isGooglePlayAvailable") isGooglePlayAvailable: Boolean
    ): ILocationHelper {
        Relog.i("$TAG: Providing GoogleLocationHelperImpl")
        return GoogleLocationHelperImpl(
            context,
            settingsRepository,
            prefManager,
            locationManager,
            random,
            xposedHookManager
        )
    }
    */

    // HAPUS provideIsGooglePlayAvailable dari sini:
    /*
    @Provides
    @Named("isGooglePlayAvailable")
    fun provideIsGooglePlayAvailable(
        @ActivityContext context: Context
    ): Boolean {
        val isGoogleBuild = try {
            Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            true
        } catch (e: ClassNotFoundException) {
            Relog.e(e,"$TAG: Google Play Services not available ${e.message}")
            false
        }
        Relog.d("$TAG: Google Play Services availability check: $isGoogleBuild")
        return isGoogleBuild
    }
    */

    // HAPUS provideXposedHookManager dari sini:
    /*
    @Provides
    @ActivityScoped
    fun provideXposedHookManager(
        @ApplicationContext context: Context
    ): IXposedHookManager {
        Relog.d("$TAG: Providing XposedHookManagerImpl")
        return XposedHookManagerImpl(context)
    }
    */

    // Anda mungkin juga perlu memindahkan provideLocationManager dan provideRandom
    // dari ActivityModule ke AppModule jika mereka dibutuhkan sebagai Singleton.
    // Jika tidak, biarkan di ActivityModule (tapi error Anda menyiratkan ILocationHelper yang tergantung pada mereka perlu Singleton).
    // Saya sarankan memindahkan LocationManager dan Random ke AppModule/SingletonComponent juga.
}