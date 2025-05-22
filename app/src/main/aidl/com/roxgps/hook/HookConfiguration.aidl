// File: [Proyek Aplikasi Utama Kamu]/src/main/aidl/com/roxgps/hook/HookConfiguration.aidl
// (Sesuaikan package dengan package AIDL kamu)

package com.roxgps.hook;

// Deklarasikan kelas yang akan digunakan di AIDL jika tidak standar
// import com.roxgps.hook.FakeLocationData; // Contoh jika HookConfiguration berisi FakeLocationData

/**
 * Data class Parcelable untuk menampung konfigurasi setting Xposed Hook.
 * Objek ini akan dikirim dari aplikasi utama ke Xposed Module melalui AIDL.
 */
parcelable HookConfiguration;