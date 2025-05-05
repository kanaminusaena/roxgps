// File: src/full/java/com/roxgps/module/ActivityModule.kt
package com.roxgps.module // Pastikan package ini SAMA dengan package module di source set main/foss

import com.roxgps.helper.ILocationHelper // Import Interface
import com.roxgps.helper.GoogleLocationHelper // <-- Import Implementasi Google Maps

import dagger.Binds // Import Anotasi Binds
import dagger.Module // Import Anotasi Module
import dagger.hilt.InstallIn // Import InstallIn
import dagger.hilt.android.components.ActivityComponent // Install di ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped // Scope ActivityScoped

// Import untuk provide ComponentActivity jika mau tetap di Module flavor
import android.content.Context
import androidx.activity.ComponentActivity
import dagger.Provides
import dagger.hilt.android.qualifiers.ActivityContext

// =====================================================================
// ActivityModule (Flavor FULL - Google Maps)
// Module spesifik flavor yang menyediakan binding untuk ILocationHelper
// =====================================================================

@Module // Module Hilt
@InstallIn(ActivityComponent::class) // Instal di ActivityComponent
abstract class ActivityModule { // Abstract class karena ada @Binds

    // =====================================================================
    // Binds untuk ILocationHelper -> GoogleLocationHelper
    // Hanya ada di Module flavor 'full'.
    // =====================================================================
    @Binds // Binding
    @ActivityScoped // ILocationHelper di-scope sebagai ActivityScoped
    abstract fun bindLocationHelper(impl: GoogleLocationHelper): ILocationHelper // <-- Bind ke GoogleLocationHelper!
    // Hilt akan mengurus membuat instance GoogleLocationHelper karena punya @Inject constructor
    // dan dependencies-nya (ComponentActivity) disediakan di ActivityComponent.


    // TODO: Tambahkan @Binds lain jika ada Interface lain yang butuh implementasi spesifik flavor Google dan scope Activity
    // Contoh: Jika lo punya Interface IOtherGoogleSpecificHelper dan implementasi OtherGoogleSpecificHelper
    // @Binds @ActivityScoped abstract fun bindOtherHelper(impl: OtherGoogleSpecificHelper): IOtherGoogleSpecificHelper
}

// Module terpisah untuk menyediakan ComponentActivity. Ini bisa di main source set,
// tapi kalau mau dipisah per flavor juga bisa. Jika sudah ada provider ComponentActivity
// di Module lain (misal di main source set), block ini bisa dihapus.
@Module
@InstallIn(ActivityComponent::class)
object ActivityComponentProvidesModule {
    // Menyediakan ComponentActivity yang terkait dengan Activity saat ini
    @Provides // Memberitahu Hilt cara membuat objek ComponentActivity
    @ActivityScoped // ComponentActivity di-scope sebagai ActivityScoped (satu instance per Activity)
    fun provideComponentActivity(@ActivityContext context: Context): ComponentActivity { // Hilt menyediakan Context level Activity
        // @ActivityContext dijamin adalah Context yang merupakan turunan dari Activity
        return context as ComponentActivity // Casting Context ke ComponentActivity.
    }

    // PROVIDES PermissionHelper REDUNDANT jika sudah @Inject constructor(ComponentActivity)
    // Provider ini sebaiknya DIHAPUS dari Module lo jika PermissionHelper punya @Inject constructor.
    // @Provides @ActivityScoped
    // fun providePermissionHelper(activity: ComponentActivity): PermissionHelper { ... }
}
