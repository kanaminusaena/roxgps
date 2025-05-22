package com.roxgps.module // Sesuaikan dengan package di mana Hilt Modules Anda berada

import com.roxgps.helper.ILocationHelper
import com.roxgps.helper.MapLibreLocationHelperImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
/**
 * Hilt Module SPESIFIK untuk Flavor 'maplibre'.
 * Bertugas untuk memberi tahu Hilt implementasi ILocationHelper mana yang harus
 * disediakan ketika kode meminta ILocationHelper dalam konteks SingletonComponent.
 */
@Module // Menandai kelas ini sebagai Module Hilt
@InstallIn(SingletonComponent::class) // Menginstal Module ini di SingletonComponent (Singleton Scope)
abstract class MapLibreLocationModule { // Kelas abstrak karena menggunakan @Binds

    /**
     * Memberi tahu Hilt untuk menggunakan MapLibreFlavorLocationHelperImpl
     * sebagai implementasi dari ILocationHelper ketika dibutuhkan dalam Singleton Scope.
     *
     * @param implementation Instance dari MapLibreFlavorLocationHelperImpl yang akan disediakan oleh Hilt.
     * @return Instance ILocationHelper.
     */
    @Binds
    @Singleton // Tambahkan anotasi scope jika diperlukan, harus sama dengan scope ILocationHelper
    abstract fun bindLocationHelper(
        implementation: MapLibreLocationHelperImpl // Parameter harus bertipe implementasi yang diinginkan
    ): ILocationHelper // Tipe return harus interface

}