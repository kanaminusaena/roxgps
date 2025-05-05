package com.roxgps.module // Pastikan package ini sesuai

// =====================================================================
// Import Library untuk ActivityModule
// =====================================================================

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent // Module ini terinstal di ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped // Scope level Activity
import androidx.activity.ComponentActivity // Untuk menyediakan ComponentActivity
import dagger.hilt.android.qualifiers.ActivityContext // Qualifier untuk Context level Activity
import com.roxgps.helper.PermissionHelper // Jika PermissionHelper punya @Inject constructor
import com.roxgps.helper.DialogHelper // Jika DialogHelper punya @Inject constructor


// =====================================================================
// ActivityModule (@InstallIn(ActivityComponent::class))
// =====================================================================

@Module // Ini adalah Module Hilt
@InstallIn(ActivityComponent::class) // Module ini terinstal di ActivityComponent (Activity-level scope)
object ActivityModule { // Menggunakan 'object' karena ini tidak butuh state/instansi

    // Menyediakan ComponentActivity yang terkait dengan Activity saat ini
    @Provides // Memberitahu Hilt cara membuat objek ComponentActivity
    @ActivityScoped // ComponentActivity di-scope sebagai ActivityScoped (satu instance per Activity)
    fun provideComponentActivity(@ActivityContext context: Context): ComponentActivity { // Hilt menyediakan Context level Activity
        // @ActivityContext dijamin adalah Context yang merupakan turunan dari Activity
        return context as ComponentActivity // Casting Context ke ComponentActivity. BAGUS!
    }

    // Menyediakan PermissionHelper
    // Asumsi constructor PermissionHelper punya @Inject constructor(private val activity: ComponentActivity)
    // Jika constructornya sudah @Inject, provide function ini TIDAK diperlukan.
    
    @Provides
    @ActivityScoped // PermissionHelper di-scope sebagai ActivityScoped
    fun providePermissionHelper(activity: ComponentActivity): PermissionHelper { // Hilt menyediakan ComponentActivity
        return PermissionHelper(activity)
    }
    
     // CATATAN: Jika constructor PermissionHelper adalah `@Inject constructor(private val activity: ComponentActivity)`, Hilt akan menyediakannya secara otomatis.

     // Menyediakan DialogHelper
     // Asumsi constructor DialogHelper punya @Inject constructor(@ActivityContext context: Context)
     // Jika constructornya sudah @Inject, provide function ini TIDAK diperlukan.
     /*
     @Provides
     @ActivityScoped // DialogHelper di-scope sebagai ActivityScoped
     fun provideDialogHelper(@ActivityContext context: Context): DialogHelper { // Hilt menyediakan Context level Activity
         return DialogHelper(context)
     }
     */
     // CATATAN: Jika constructor DialogHelper adalah `@Inject constructor(@ActivityContext context: Context)`, Hilt akan menyediakannya secara otomatis.
}
