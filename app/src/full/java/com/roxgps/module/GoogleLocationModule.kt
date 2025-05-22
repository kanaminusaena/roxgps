package com.roxgps.module

import com.roxgps.helper.GoogleLocationHelperImpl
import com.roxgps.helper.ILocationHelper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module SPESIFIK untuk Flavor 'google'.
 * Bertugas untuk memberi tahu Hilt implementasi ILocationHelper mana yang harus
 * disediakan ketika kode meminta ILocationHelper dalam konteks ActivityComponent.
 */
@Module // Menandai kelas ini sebagai Module Hilt
@InstallIn(SingletonComponent::class) // <<< PERBAIKAN: Instal Module ini di ActivityComponent
abstract class GoogleLocationModule {

    /**
     * Memberi tahu Hilt untuk menggunakan GoogleLocationHelperImpl
     * sebagai implementasi dari ILocationHelper ketika dibutuhkan dalam Activity Scope.
     *
     * @param implementation Instance dari GoogleLocationHelperImpl yang akan disediakan oleh Hilt.
     * @return Instance ILocationHelper.
     */
    @Binds // Binding
    @Singleton // Scope binding (cocok dengan ActivityComponent)
    abstract fun bindLocationHelper(
        implementation: GoogleLocationHelperImpl
    ): ILocationHelper
}

// Hapus atau pastikan block ActivityComponentProvidesModule di bawah ini tidak aktif
// jika ComponentActivity sudah disediakan oleh Hilt secara default di ActivityComponent.
// Hilt biasanya sudah menyediakan ComponentActivity secara otomatis di ActivityComponent.
/*
@Module
@InstallIn(ActivityComponent::class)
object ActivityComponentProvidesModule {
    // ...
}
*/