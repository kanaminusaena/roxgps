package com.roxgps.module.util

import javax.inject.Qualifier

//import dagger.Qualifier // Import Qualifier from Dagger
//import javax.inject.Scope // Opsional: bisa juga anotasi Scope kalau mau bikin scope baru

// Anotasi kustom sebagai qualifier untuk Application Scope CoroutineScope
// Didefinisikan di file terpisah untuk kerapian dan reusability.
@Qualifier // Ini adalah anotasi Qualifier
@Retention(AnnotationRetention.RUNTIME) // Retention RUNTIME agar tersedia saat runtime
//annotation class ApplicationScope // Nama anotasi kustom kita
annotation class ApplicationScope