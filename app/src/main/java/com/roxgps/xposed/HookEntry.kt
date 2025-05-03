package com.roxgps.xposed // Contoh package modul Xposed lo

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
// Import class hooker GoFood yang tadi lo buat
import com.roxgps.xposed.hookers.CompetitorTokenHooker // GANTI kalau beda package/nama classnya!
import com.roxgps.utils.FileLogger

class LocationHook : IXposedHookLoadPackage {

    // Pastikan BuildConfig bisa diakses kalau pake BuildConfig.APPLICATION_ID
    // import com.roxgps.BuildConfig // Contoh import BuildConfig

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // --- Hook untuk aplikasi lo sendiri (RoxGPS) ---
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            XposedBridge.log("GS_HOOK: Masuk ke package RoxGPS!") // Log buat debug
            XposedHelpers.findAndHookMethod("com.roxgps.ui.viewmodel.MainViewModel", lpparam.classLoader, "updateXposedState", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Catatan: hook ini mungkin butuh disesuaikan tergantung fungsi updateXposedState
                    param.result = null // Tetap pertahankan hook lama sesuai kode lo
                }
            })
             // Kalau ada hook lain spesifik RoxGPS, bisa ditaruh di sini
        }

        // --- Hook untuk aplikasi Target (GoFood) ---
        // GANTI "com.gojek.app" dengan nama package GoFood yang BENAR
        else if (lpparam.packageName == "com.gojek.app") {
             FileLogger.log("GS_HOOK: Masuk ke package GoFood!", "Gojek", "I") // Log awal pakai FileLogger
             // Panggil method hook yang ada di class GofoodHooker
             CompetitorTokenHooker.hook(lpparam) // <-- Panggil hooker GoFood di sini!
        }

        // --- Panggilan ke Method Hook Umum atau Hook Aplikasi Lain ---
        // Kode ini akan jalan di SEMUA package setelah cek package RoxGPS dan GoFood
        // LocationHook.initHooks(lpparam) // Kalau initHooks berisi hook umum, tetap jalankan

        // Kalau ada cek package lain, tambahin di sini pakai else if
        // else if (lpparam.packageName == "nama.package.aplikasi.lain") { ... }
    }

    // Method initHooks (kalau ada)
    /*
    companion object { // Method static biasanya di dalam companion object di Kotlin
        fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
            // Kode hook umum atau hook aplikasi lain yang selalu jalan
        }
    }
    */
}
