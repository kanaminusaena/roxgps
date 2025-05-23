package com.roxgps.module

import android.content.Context
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.roxgps.helper.ILocationHelper
import com.roxgps.helper.MapLibreLocationHelperImpl
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

    private const val TAG = "MapLibreLocationModule"

    /**
     * Menyediakan implementasi ILocationHelper untuk flavor 'maplibre' (MapLibreLocationHelperImpl)
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
        // MapLibreLocationHelperImpl mungkin TIDAK memerlukan FusedLocationProviderClient.
        // Hapus parameter ini jika konstruktornya tidak menerimanya.
        fusedLocationProviderClient: FusedLocationProviderClient,
        @Named("isGooglePlayAvailable") isGooglePlayAvailable: Boolean // Tetap ada untuk konsistensi, meskipun mungkin tidak digunakan oleh MapLibreLocationHelperImpl
    ): ILocationHelper {
        Relog.i("$TAG: Providing MapLibreLocationHelperImpl")
        return MapLibreLocationHelperImpl(
            context,
            settingsRepository,
            prefManager,
            locationManager,
            random,
            xposedHookManager,
            fusedLocationProviderClient
            // Pastikan Anda meneruskan parameter yang benar sesuai dengan konstruktor MapLibreLocationHelperImpl
            // Jika Anda menghapus fusedLocationProviderClient dari parameter, hapus juga dari konstruktor di bawah.
        )
    }
}