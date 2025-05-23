package com.roxgps.module

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.roxgps.helper.ILocationHelper
import com.roxgps.helper.MapLibreLocationHelperImpl
import com.roxgps.helper.PermissionHelper
import com.roxgps.repository.SettingsRepository
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import com.roxgps.xposed.IXposedHookManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Named
import kotlin.random.Random

/**
 * Hilt Module untuk menyediakan dependencies di scope Activity khusus untuk flavor MapLibre.
 *
 * @author loserkidz
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
     * Menyediakan implementasi ILocationHelper untuk flavor MapLibre.
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
        // Untuk flavor MapLibre, selalu gunakan MapLibreLocationHelperImpl terlepas dari ketersediaan Google Play Services.
        Relog.i("$TAG: Providing MapLibreLocationHelperImpl")
        return MapLibreLocationHelperImpl(
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
     * Meskipun pada flavor MapLibre flag ini tidak digunakan untuk memilih implementasi,
     * fungsi ini tetap disediakan agar modul DI konsisten.
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
            Relog.e(e,"$TAG: Google Play Services not available")
            false
        }
        Relog.d("$TAG: Google Play Services availability check: $isGoogleBuild")
        return isGoogleBuild
    }
}