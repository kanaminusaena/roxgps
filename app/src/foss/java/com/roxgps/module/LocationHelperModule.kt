package com.roxgps.module // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk LocationHelperModule (MapLibre Flavor)
// =====================================================================

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent // Module ini terinstal di ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped // Scope level Activity
import androidx.activity.ComponentActivity // Karena MapLibreLocationHelper butuh ini
// import com.roxgps.helper.PermissionHelper // Jika MapLibreLocationHelper butuh ini sebagai dependency

import com.roxgps.helper.ILocationHelper // Interface
import com.roxgps.helper.MapLibreLocationHelper // Implementasi MapLibre

// =====================================================================
// LocationHelperModule (MapLibre Flavor - @InstallIn(ActivityComponent::class))
// =====================================================================

@Module // Ini adalah Module Hilt
@InstallIn(ActivityComponent::class) // Module ini terinstal di ActivityComponent
object LocationHelperModule { // Menggunakan 'object'

    // Ini yang penting: Memberitahu Hilt, saat diminta ILocationHelper, berikan instance MapLibreLocationHelper
    @Provides // Memberitahu Hilt cara membuat objek ILocationHelper
    @ActivityScoped // Instance ILocationHelper di-scope sebagai ActivityScoped (sesuai implementasinya)
    fun bindLocationHelper(
        activity: ComponentActivity // Hilt bisa menyediakan ComponentActivity (dari ActivityModule)
        // permissionHelper: PermissionHelper // Jika MapLibreLocationHelper butuh ini, Hilt akan menyediakannya
    ): ILocationHelper {
        // Hilt akan membuat MapLibreLocationHelper dan mengembalikannya sebagai ILocationHelper
        // Pastikan constructor MapLibreLocationHelper sesuai dengan parameter di sini
        return MapLibreLocationHelper(activity) // Membuat instance MapLibreLocationHelper
    }
}
