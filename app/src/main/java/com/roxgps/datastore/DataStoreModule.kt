package com.roxgps.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Singleton

// =====================================================================
// DataStoreModule (Hilt Module)
// Menyediakan instance DataStore<AppSettings> sebagai Singleton.
// =====================================================================

/**
 * Modul Hilt untuk menyediakan instance DataStore<AppSettings> sebagai Singleton.
 * DataStore akan dibuat dan dikelola oleh Hilt.
 */
@Module
@InstallIn(SingletonComponent::class) // Install di SingletonComponent agar DataStore menjadi Singleton
object DataStoreModule {

    private const val APP_SETTINGS_FILE_NAME = "app_settings.pb"
    private const val LOG_TAG = "AppSettingsDataStore" // Untuk logging Timber

    @Singleton // Scope ini juga Singleton
    @Provides
    fun provideDataStoreCoroutineScope(): CoroutineScope {
        // Menggunakan SupervisorJob agar Job anak (baca/tulis) tidak membatalkan Job induk jika terjadi error.
        // Menggunakan Dispatchers.IO karena operasi DataStore melibatkan I/O disk.
        return CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @Singleton // DataStore adalah Singleton
    @Provides
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context, // Inject Application Context
        scope: CoroutineScope, // Inject Coroutine Scope yang sudah disediakan di atas
        serializer: AppSettingsSerializer // Inject Serializer
    ): DataStore<AppSettings> {
        return DataStoreFactory.create(
            serializer = serializer, // Gunakan Serializer yang di-inject

            // --- PERBAIKAN PENTING DI SINI: CorruptionHandler ---
            corruptionHandler = ReplaceFileCorruptionHandler { exception ->
                Timber.e(exception, "$LOG_TAG: DataStore for AppSettings is corrupted. Clearing data.")
                AppSettings.getDefaultInstance() // Mengembalikan nilai default saat korupsi terdeteksi
            },
            // ---------------------------------------------------

            // Sertakan CoroutineScope yang sudah disediakan. Ini memberikan kontrol yang lebih baik.
            scope = scope,

            // produceFile lambda untuk menentukan lokasi file DataStore.
            // Sintaks lambda di Kotlin bisa lebih ringkas jika argumen terakhir adalah lambda.
            produceFile = { context.dataStoreFile(APP_SETTINGS_FILE_NAME) }
        )
    }

    @Singleton // Serializer adalah Singleton (objek stateless)
    @Provides
    fun provideAppSettingsSerializer(): AppSettingsSerializer {
        return AppSettingsSerializer // Mengembalikan objek Serializer
    }
}