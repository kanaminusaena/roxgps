package com.roxgps.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.roxgps.BuildConfig // Pastikan ini terimport

// Import class hooker GoFood yang tadi lo buat
import com.roxgps.xposed.hookers.CompetitorTokenHooker // GANTI package/nama class ini kalau beda!
import com.roxgps.utils.FileLogger

class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // --- Hook untuk aplikasi lo sendiri (RoxGPS) ---
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            XposedBridge.log("GS_HOOK_ENTRY: Masuk ke package RoxGPS!") // Log buat debug di entry point
            XposedHelpers.findAndHookMethod("com.roxgps.ui.viewmodel.MainViewModel", lpparam.classLoader, "updateXposedState", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Catatan: hook ini mungkin butuh disesuaikan tergantung fungsi updateXposedState
                    param.result = null // Tetap pertahankan hook lama sesuai kode lo
                }
            })
             // Kalau ada hook lain spesifik RoxGPS, bisa ditaruh di sini di dalam blok ini
        }

        // --- Hook untuk aplikasi Target (GoFood) ---
        // GANTI "com.gojek.app" dengan nama package GoFood yang BENAR
        else if (lpparam.packageName == "com.gojek.app") {
             // FileLogger.log("GS_HOOK_ENTRY: Masuk ke package GoFood!", "Gojek", "I") // Pindah log awal ke FileLogger kalau FileLogger ada di modul
             //XposedBridge.log("GS_HOOK_ENTRY: Masuk ke package GoFood!") // Pakai XposedBridge.log kalau FileLogger belum siap/ada di sini
             FileLogger.log("GS_HOOK_ENTRY: Masuk ke package GoFood!", "Gojek", "I") // Ganti log
             // Panggil method hook yang ada di class CompetitorTokenHooker
             CompetitorTokenHooker.hook(lpparam) // <-- Panggil hooker GoFood di sini!
        }

        // --- Panggilan ke Method Hook Umum atau Hook Sistem ---
        // Kode ini akan jalan di SEMUA package setelah cek package RoxGPS dan GoFood
        // Atau kalau lo mau initHooks cuma jalan di package tertentu, pindahin panggilan ini ke dalam if/else if
        LocationHook.initHooks(lpparam) // Panggil initHooks milik object LocationHook

        // Kalau ada cek package lain, tambahin di sini pakai else if
        // else if (lpparam.packageName == "nama.package.aplikasi.lain") { ... }
    }
}
