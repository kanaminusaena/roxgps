package com.roxgps.module // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk SingletonModule
// =====================================================================

import android.app.Application // Import Application
import android.app.DownloadManager // Import DownloadManager
import android.content.Context // Import Context
import androidx.room.Room // Import Room
import dagger.Module // Import Anotasi Module
import dagger.Provides // Import Anotasi Provides
import dagger.hilt.InstallIn // Import InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext // Qualifier Context Aplikasi
import dagger.hilt.components.SingletonComponent // Import SingletonComponent
// Import anotasi qualifier CoroutineScope dari file terpisah
import com.roxgps.module.utils.ScopeQualifiers // <-- IMPORT DARI FILE TERPISAH! GUNAKAN INI!
import com.roxgps.room.AppDatabase // Import AppDatabase
import com.roxgps.room.FavoriteDao // Import FavoriteDao
// Repositories TIDAK perlu PROVIDES eksplisit jika @Inject constructor & dependency tersedia
// import com.roxgps.repository.FavoriteRepository
// import com.roxgps.repository.DownloadRepository
// import com.roxgps.repository.SearchRepository
// import com.roxgps.repository.HookStatusRepository // <--- HookStatusRepository TIDAK perlu Provides eksplisit

// Helpers UMUM TIDAK perlu PROVIDES eksplisit jika @Inject constructor & dependency tersedia
// import com.roxgps.helper.SearchHelper
// import com.roxgps.utils.PrefManager
// import com.roxgps.utils.NotificationsChannel
// import com.roxgps.helper.DialogHelper
// import com.roxgps.helper.NotificationHelper
// import com.roxgps.helper.PermissionHelper
// import com.roxgps.update.UpdateChecker // <--- UpdateChecker TIDAK perlu Provides eksplisit

import com.roxgps.update.GitHubService // Import GitHubService (Jika Retrofit Service)

import kotlinx.coroutines.CoroutineScope // Import CoroutineScope
import kotlinx.coroutines.SupervisorJob // Import SupervisorJob
import kotlinx.coroutines.Dispatchers // Import Dispatchers (Default atau IO)
import retrofit2.Retrofit // Import Retrofit
import retrofit2.converter.gson.GsonConverterFactory // Import Converter Factory Retrofit
import javax.inject.Singleton // Import Singleton

// Import Callback AppDatabase DENGAN @Inject constructor
import com.roxgps.room.AppDatabase.Callback // <-- PENTING: Import Callback yang ada @Inject constructor!


// =====================================================================
// SingletonModule (@InstallIn(SingletonComponent::class))
// Module ini menyediakan dependency Singleton (Application Scope) yang Hilt tidak bisa buat otomatis.
// Repositories dan Helpers umum dengan @Inject constructor disediakan otomatis oleh Hilt.
// =====================================================================

@Module // Ini adalah Module Hilt
@InstallIn(SingletonComponent::class) // Module ini terinstal di SingletonComponent
object SingletonModule { // Menggunakan 'object' karena ini tidak butuh state/instansi

    // =====================================================================
    // Provides untuk CoroutineScope level Aplikasi
    // =====================================================================
    @Provides // Memberitahu Hilt cara membuat CoroutineScope
    @Singleton // Scope-nya Singleton
    @ScopeQualifiers // <-- Menggunakan anotasi qualifier yang di-import (sesuai kode lo)
    fun providesApplicationScope(): CoroutineScope {
        // Membuat CoroutineScope dengan SupervisorJob DAN Dispatcher yang jelas.
        // Pilih Dispatchers.IO untuk task I/O atau Dispatchers.Default untuk CPU-bound.
        return CoroutineScope(SupervisorJob() + Dispatchers.IO) // <-- DITAMBAH Dispatchers.IO untuk task I/O
    }

    // =====================================================================
    // Provides untuk Database Room
    // Dependency AppDatabase.Callback yang punya @Inject constructor akan DI-INJECT otomatis oleh Hilt
    // =====================================================================
    @Provides @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context, // Hilt otomatis provide ApplicationContext
        // Meminta AppDatabase.Callback sebagai parameter. Hilt akan mengurus
        // injeksi dependency (@ScopeQualifiers CoroutineScope) untuk membuat Callback ini.
        callback: AppDatabase.Callback // <-- Meminta Callback yang sudah DI-INJECT oleh Hilt
    ): AppDatabase {
        return Room.databaseBuilder(
            context, // Gunakan ApplicationContext
            AppDatabase::class.java,
            "user_database" // Nama database lo
        )
        .fallbackToDestructiveMigration() // Opsi: Kalau skema berubah dan tidak ada migrasi, hancurkan DB (hati-hati data hilang!)
        .addCallback(callback) // Menambahkan callback yang sudah di-inject dan dibuat oleh Hilt
        .build()
    }

    // Provider eksplisit untuk AppDatabase.Callback DIHAPUS! Hilt bisa bikin sendiri karena punya @Inject constructor.
    // @Provides @Singleton
    // fun provideDatabaseCallback(): AppDatabase.Callback { /* ... */ } // <-- DIHAPUS!


    // =====================================================================
    // Provides untuk DAO (Didapatkan dari Database)
    // =====================================================================
    @Provides @Singleton
    fun providesFavoriteDao(appDatabase: AppDatabase): FavoriteDao {
        // Hilt otomatis provide AppDatabase karena sudah didefinisikan di atas
        return appDatabase.favoriteDao()
    }

    // =====================================================================
    // Provides untuk Network (Retrofit, Service)
    // =====================================================================
     // Menyediakan Retrofit instance
    @Provides @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            // Base URL untuk cek update GitHub
            .baseUrl("https://api.github.com/repos/codenrox/roxgps/") // TODO: Ambil dari BuildConfig atau sumber konfigurasi lain
            .addConverterFactory(GsonConverterFactory.create()) // Gunakan Gson Converter
            // TODO: Tambahkan interceptor (misal Logging Interceptor) jika perlu
            // .client(OkHttpClient.Builder().addInterceptor(...).build())
            .build()
    }

    // Menyediakan GitHubService (interface API Retrofit)
    @Provides @Singleton
    fun provideGithubService(retrofit: Retrofit): GitHubService {
        // Hilt otomatis provide Retrofit karena sudah didefinisikan di atas
        return retrofit.create(GitHubService::class.java)
    }


    // =====================================================================
    // Provides untuk System Services
    // =====================================================================
    @Provides @Singleton
    fun provideDownloadManager(application: Application): DownloadManager {
        // Menggunakan Application Context untuk getSystemService
        return application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    // =====================================================================
    // Providers untuk Helpers Umum dan Repositories dengan @Inject constructor
    // Mereka TIDAK perlu Providers eksplisit di sini karena Hilt bisa buat otomatis
    // selama dependencies-nya (Context, DAO, DM, dll) disediakan di Module ini.
    // =====================================================================

    // Contoh: PROVIDES HookStatusRepository REDUNDANT karena sudah @Singleton dengan @Inject constructor
    // dan tidak ada dependency yang perlu cara khusus untuk disediakan.
    // @Provides @Singleton
    // fun provideHookStatusRepository(): HookStatusRepository { return HookStatusRepository() } // <-- REDUNDANT

    // Contoh: PROVIDES FavoriteRepository REDUNDANT jika sudah @Singleton dengan @Inject constructor(FavoriteDao)
    // @Provides @Singleton
    // fun provideFavoriteRepository(dao: FavoriteDao): FavoriteRepository { return FavoriteRepository(dao) } // <-- REDUNDANT

    // TODO: Hapus semua PROVIDES eksplisit untuk kelas yang punya @Inject constructor
    // dan dependencies-nya disediakan di Module ini atau Module Singleton lainnya.

}
