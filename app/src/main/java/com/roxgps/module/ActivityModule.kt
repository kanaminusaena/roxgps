package com.roxgps.module

import android.content.Context
import androidx.activity.ComponentActivity
import com.roxgps.helper.PermissionHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module // Ini adalah Modul Hilt
@InstallIn(ActivityComponent::class) // Modul ini diinstal di ActivityComponent
object ActivityModule { // Menggunakan 'object' karena semua fungsinya adalah @Provides

    /**
     * Menyediakan instance [PermissionHelper] untuk setiap Activity.
     * Menggunakan `@ActivityScoped` agar instance PermissionHelper ini menjadi singleton
     * selama masa hidup Activity tempat ia diinjeksikan.
     *
     * @param ActivityContext Instance [ComponentActivity] yang secara otomatis disediakan oleh Hilt
     * saat modul ini aktif dalam konteks ActivityComponent.
     */
    @Provides
    @ActivityScoped // Penting: Pastikan ini adalah ActivityScoped, bukan Singleton
    fun providePermissionHelper(@ActivityContext context: Context): PermissionHelper {
        val activity = context as ComponentActivity
        return PermissionHelper(activity)
    }
}