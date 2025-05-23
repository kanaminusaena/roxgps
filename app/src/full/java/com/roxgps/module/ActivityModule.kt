package com.roxgps.module

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.roxgps.helper.GoogleLocationHelperImpl
import com.roxgps.helper.ILocationHelper
import com.roxgps.helper.PermissionHelper
import com.roxgps.repository.SettingsRepository
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import com.roxgps.xposed.IXposedHookManager
import com.roxgps.xposed.XposedHookManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Named
import kotlin.random.Random

/**
 * Hilt Module untuk menyediakan dependencies di scope Activity khusus untuk flavor Google.
 *
 * Module ini selalu menyediakan implementasi GoogleLocationHelperImpl.
 *
 * @since 2025-05-23 13:47:28
 */
@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    private const val TAG = "ActivityModule"

    /**
     * Menyediakan instance PermissionHelper untuk Activity.
     */
    @Provides
    @ActivityScoped
    fun providePermissionHelper(
        @ActivityContext context: Context
    ): PermissionHelper {
        Relog.d("$TAG: Providing PermissionHelper")
        return PermissionHelper(context)
    }

    /**
     * Menyediakan LocationManager dari system service.
     */
    @Provides
    @ActivityScoped
    fun provideLocationManager(
        @ActivityContext context: Context
    ): LocationManager {
        Relog.d("$TAG: Providing LocationManager")
        return ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: throw IllegalStateException("LocationManager not available")
    }

    /**
     * Menyediakan Random number generator.
     */
    @Provides
    @ActivityScoped
    fun provideRandom(): Random {
        Relog.d("$TAG: Providing Random")
        return Random.Default
    }

    /**
     * Menyediakan implementasi ILocationHelper untuk flavor Google.
     * Module ini akan selalu mengembalikan GoogleLocationHelperImpl, terlepas dari ketersediaan Google Play Services.
     */
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

    /**
     * Menyediakan flag ketersediaan Google Play Services.
     * Walaupun pada flavor Google implementasi selalu menggunakan GoogleLocationHelperImpl, flag ini tetap disediakan
     * untuk konsistensi module DI.
     */
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

    /**
     * Menyediakan implementasi dari IXposedHookManager dengan menggunakan XposedHookManagerImpl.
     * Karena XposedHookManagerImpl memiliki parameter context dengan @ApplicationContext,
     * kita harus menginjeksi context level Aplikasi melalui @ApplicationContext.
     */
    @Provides
    @ActivityScoped
    fun provideXposedHookManager(
        @ApplicationContext context: Context
    ): IXposedHookManager {
        Relog.d("$TAG: Providing XposedHookManagerImpl")
        return XposedHookManagerImpl(context)
    }
}