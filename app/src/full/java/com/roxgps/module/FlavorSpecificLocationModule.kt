package com.roxgps.module

import android.content.Context
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.roxgps.helper.GoogleLocationHelperImpl
import com.roxgps.helper.ILocationHelper
import com.roxgps.repository.SettingsRepository
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import com.roxgps.xposed.IXposedHookManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class) // <-- Instal di SingletonComponent
object FlavorSpecificLocationModule {

    private const val TAG = "FossLocationModule"

    /**
     * Menyediakan implementasi ILocationHelper untuk flavor 'foss' (GoogleLocationHelperImpl)
     * sebagai Singleton.
     */
    @Provides
    @Singleton // <-- Cakupan Singleton
    fun provideLocationHelper(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        prefManager: PrefManager,
        locationManager: LocationManager,
        random: Random,
        xposedHookManager: IXposedHookManager,
        fusedLocationProviderClient: FusedLocationProviderClient, // Diperlukan oleh GoogleLocationHelperImpl
        @Named("isGooglePlayAvailable") isGooglePlayAvailable: Boolean
    ): ILocationHelper {
        Relog.i("$TAG: Providing GoogleLocationHelperImpl")
        return GoogleLocationHelperImpl(
            context,
            settingsRepository,
            prefManager,
            locationManager,
            random,
            xposedHookManager,
            fusedLocationProviderClient // Pastikan konstruktornya menerima ini
        )
    }
}