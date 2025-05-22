// File: app/src/main/java/com/roxgps/room/DatabaseModule.kt

package com.roxgps.room // Sesuaikan package jika Anda ingin menempatkannya di folder lain (misal: com.roxgps.di)

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import androidx.room.Room
import com.roxgps.module.util.ApplicationScope // Pastikan path ini benar untuk kustom qualifier Anda
import com.roxgps.repository.SettingsRepository
import com.roxgps.repository.SettingsRepositoryImpl
import com.roxgps.update.GitHubService
import com.roxgps.utils.NotificationsChannel
import com.roxgps.xposed.IXposedHookManager
import com.roxgps.xposed.XposedHookManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Hilt Module yang menggabungkan provisi Singleton dari AppDatabase dan SingletonModule yang lama.
 * Menyediakan berbagai dependency tingkat aplikasi (Singleton Scope) untuk seluruh aplikasi.
 *
 * Menggunakan abstract class untuk @Binds dan companion object untuk @Provides.
 */
@Module // Menandakan bahwa ini adalah modul Hilt
@InstallIn(SingletonComponent::class) // Modul ini terinstal di SingletonComponent, hidup selama aplikasi berjalan
abstract class DatabaseModule { // Menggunakan 'abstract class' karena ada @Binds

    // --- Bagian @Binds (untuk mengikat interface ke implementasi konkret) ---
    // Metode-metode ini dipindahkan dari SingletonModule yang lama.
    // @Binds HARUS berada di dalam abstract class dan merupakan abstract fun.

    // Mengikat interface SettingsRepository ke implementasinya (SettingsRepositoryImpl)
    @Binds
    @Singleton // Karena implementasi SettingsRepositoryImpl adalah Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    // Mengikat interface IXposedHookManager ke implementasinya (XposedHookManagerImpl)
    @Binds
    @Singleton // Karena implementasi XposedHookManagerImpl adalah Singleton
    abstract fun bindXposedHookManager(impl: XposedHookManagerImpl): IXposedHookManager

    // CATATAN PENTING: Jika Anda memiliki ILocationHelper dengan implementasi
    // yang berbeda per flavor (misal: GoogleLocationHelperImpl di 'full', MicroGLocationHelperImpl di 'foss'),
    // Anda TIDAK boleh membindingnya di modul ini (di source set 'main').
    // Anda harus membuat modul Hilt TERPISAH di setiap source set flavor (misal: app/src/full/java/...)
    // yang membinding ILocationHelper ke implementasi spesifik flavor-nya.

    // --- Bagian @Provides (untuk menyediakan instance yang perlu dibuat/dibangun) ---
    // Metode-metode ini dipindahkan dari SingletonModule dan DatabaseModule yang lama.
    // @Provides biasanya ditempatkan di dalam 'companion object' di abstract class agar bisa 'static'.
    companion object {

        // =====================================================================
        // Provides untuk CoroutineScope level Aplikasi
        // =====================================================================
        @Provides
        @Singleton
        @ApplicationScope // Menggunakan kustom qualifier untuk CoroutineScope aplikasi
        fun providesApplicationScope(): CoroutineScope {
            // Membuat CoroutineScope dengan SupervisorJob (untuk ketahanan terhadap error anak coroutine)
            // dan Dispatchers.IO (untuk operasi I/O yang blocking).
            return CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }

        // =====================================================================
        // Provides untuk Database Room (AppDatabase)
        // Ini adalah SATU-SATUNYA provider untuk AppDatabase, menggantikan yang duplikat.
        // Menggunakan versi yang menerima AppDatabase.Callback untuk setup yang lebih lengkap.
        // =====================================================================
        @Provides
        @Singleton
        fun provideAppDatabase(
            @ApplicationContext context: Context, // Hilt otomatis menyediakan ApplicationContext
            callback: AppDatabase.Callback        // Hilt akan menginjeksi AppDatabase.Callback jika ada providernya
        ): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "rox_db" // Nama file database lokal Anda
            )
                .fallbackToDestructiveMigration(false) // HATI-HATI: Jika skema berubah dan tidak ada migrasi, DB akan dihancurkan.
                .addCallback(callback) // Menambahkan callback yang sudah di-inject
                .build()
        }

        // =====================================================================
        // Provides untuk DAO (Data Access Objects)
        // =====================================================================
        @Provides
        @Singleton
        fun providesFavoriteDao(appDatabase: AppDatabase): FavoriteDao {
            // Hilt otomatis menyediakan AppDatabase karena sudah didefinisikan di atas
            return appDatabase.favoriteDao()
        }

        @Provides
        @Singleton
        fun provideLocationDao(appDatabase: AppDatabase): LocationDao {
            // Hilt otomatis menyediakan AppDatabase
            return appDatabase.locationDao()
        }

        // =====================================================================
        // Provides untuk Network (Retrofit, GitHubService)
        // =====================================================================
        @Provides
        @Singleton
        fun provideRetrofit(): Retrofit {
            return Retrofit.Builder()
                // Ganti dengan Base URL yang sesuai untuk cek update GitHub
                .baseUrl("https://api.github.com/repos/codenrox/roxgps/")
                .addConverterFactory(GsonConverterFactory.create()) // Menggunakan Gson untuk konversi JSON
                // TODO: Tambahkan interceptor (misal Logging Interceptor) jika diperlukan
                // .client(OkHttpClient.Builder().addInterceptor(...).build())
                .build()
        }

        @Provides
        @Singleton
        fun provideGithubService(retrofit: Retrofit): GitHubService {
            // Hilt otomatis menyediakan Retrofit
            return retrofit.create(GitHubService::class.java)
        }

        // =====================================================================
        // Provides untuk System Services (misal DownloadManager)
        // =====================================================================
        @Provides
        @Singleton
        fun provideDownloadManager(application: Application): DownloadManager {
            return application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }

        @Provides
        @Singleton // Karena NotificationsChannel adalah 'object', ia sudah Singleton secara intrinsik
        fun provideNotificationsChannel(): NotificationsChannel {
            // Cukup kembalikan instance object itu sendiri
            // Tidak perlu Context di sini, karena metode-metode di NotificationsChannel sudah menerima Context
            return NotificationsChannel
        }

        // =CATATAN TAMBAHAN:
        // Semua metode @Provides eksplisit untuk kelas yang memiliki @Inject constructor
        // dan semua dependencies-nya sudah disediakan di sini (atau di modul lain),
        // tidak perlu dideklarasikan secara eksplisit (misal: HookStatusRepository, FavoriteRepository).
        // Hilt sudah cukup pintar untuk membangunnya secara otomatis.
    }
}