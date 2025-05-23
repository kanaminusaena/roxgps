// src/main/java/com/roxgps/module/AppModule.kt
// INI ADALAH FILE GLOBAL, TIDAK ADA VERSI TERPISAH UNTUK 'full' ATAUPUN 'foss'

package com.roxgps.module

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.roxgps.helper.PermissionHelper
import com.roxgps.utils.Relog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TAG = "AppModule"

    /**
     * Menyediakan instance PermissionHelper (Singleton).
     */
    @Provides
    @Singleton
    fun providePermissionHelper(
        @ApplicationContext context: Context
    ): PermissionHelper {
        Relog.d("$TAG: Providing PermissionHelper")
        return PermissionHelper(context)
    }

    /**
     * Menyediakan LocationManager dari system service (Singleton).
     */
    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context
    ): LocationManager {
        Relog.d("$TAG: Providing LocationManager")
        return ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: throw IllegalStateException("LocationManager not available")
    }

    /**
     * Menyediakan FusedLocationProviderClient (Singleton).
     */
    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        Relog.d("$TAG: Providing FusedLocationProviderClient")
        return LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Menyediakan Random number generator (Singleton).
     */
    @Provides
    @Singleton
    fun provideRandom(): Random {
        Relog.d("$TAG: Providing Random")
        return Random.Default
    }

    /**
     * Menyediakan flag ketersediaan Google Play Services (Singleton).
     */
    @Provides
    @Singleton
    @Named("isGooglePlayAvailable")
    fun provideIsGooglePlayAvailable(
        @ApplicationContext context: Context
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

    // CATATAN PENTING: provideLocationHelper TIDAK ADA DI SINI.
    // provideLocationHelper akan disediakan di FlavorSpecificLocationModule di masing-masing flavor.
}