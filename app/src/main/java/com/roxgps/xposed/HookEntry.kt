package com.roxgps.xposed // Pastikan package ini sesuai

// =====================================================================
// Import Library HookEntry
// =====================================================================

import de.robv.android.xposed.IXposedHookLoadPackage // Xposed Hook Interface
import de.robv.android.xposed.XposedBridge // Xposed Logging & Utilities
// Tidak perlu import XC_MethodHook, XposedHelpers, ComponentName, Context, Intent, ServiceConnection, IBinder, Log, IRoxGpsService, FakeLocationData
// karena logic AIDL binding dan hooking pindah ke LocationHook.
// import de.robv.android.xposed.XC_MethodHook
// import de.robv.android.xposed.XposedHelpers
// import android.content.ComponentName
// import android.content.Context
// import android.content.Intent
// import android.content.ServiceConnection
// import android.os.IBinder
// import android.util.Log
// import com.roxgps.ipc.IRoxGpsService // Import ini dipindah ke LocationHook

import com.roxgps.BuildConfig // Import BuildConfig untuk Package ID aplikasi lo
import com.roxgps.utils.FileLogger // Import FileLogger jika digunakan. Pastikan FileLogger adalah object atau bisa diakses statis.

// Import class hooker spesifik lainnya
import com.roxgps.xposed.hookers.KampretTokenHooker // GANTI package/nama class ini kalau beda!

// Import class hooker Location (Object atau Class)
import com.roxgps.xposed.hookers.LocationHook // <-- IMPORT LocationHook yang akan mengurus lokasi & AIDL!


// =====================================================================
// Kelas Utama Xposed Module (Entry Point)
// Bertanggung jawab mendeteksi package dan mendelegasikan proses hooking.
// =====================================================================

class HookEntry : IXposedHookLoadPackage {

    // =====================================================================
    // Properti untuk AIDL Service Binding (Dipindahkan ke LocationHook)
    // =====================================================================
    // private var lokasiPalsuService: IRoxGpsService? = null // <-- DIPINDAH KE LocationHook
    // private val lokasiPalsuServiceConnection = object : ServiceConnection { /* ... */ } // <-- DIPINDAH KE LocationHook


    // =====================================================================
    // Metode handleLoadPackage - Entry Point Hook
    // =====================================================================

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
         // Log awal saat module diload ke package manapun
         // Menggunakan XposedBridge.log atau FileLogger (konsisten salah satu)
         XposedBridge.log("RoxGpsXposed: Loaded package: ${lpparam.packageName}") // Menggunakan XposedBridge.log
         // FileLogger.log("Loaded package: ${lpparam.packageName}", "XposedLog", "I") // Jika pakai FileLogger

        // --- Cek package aplikasi lo sendiri (RoxGPS App) ---
        // Inisialisasi AIDL Binding hanya dilakukan saat module diload di proses RoxGPS.
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            XposedBridge.log("RoxGpsXposed: Masuk ke package RoxGPS!")
            // FileLogger.log("Masuk ke package RoxGPS: ${lpparam.packageName}", "XposedLog", "I") // Jika pakai FileLogger

            // Panggil metode di LocationHook untuk memulai proses binding AIDL
            // LocationHook akan mengurus mendapatkan Context dan melakukan binding.
            LocationHook.initAidlBinding(lpparam) // <-- Panggil LocationHook untuk BINDING!

            // Hook spesifik RoxGPS (jika ada dan masih diperlukan setelah refaktor)
            // Biarkan hook internal di sini jika memang dibutuhkan di proses RoxGPS.
            // XposedHelpers.findAndHookMethod("com.roxgps.ui.viewmodel.MainViewModel", lpparam.classLoader, "updateXposedState", object : XC_MethodHook() {
            //     override fun beforeHookedMethod(param: MethodHookParam) {
            //         param.result = null // Pertahankan hook lama
            //     }
            // })
             // TODO: Tambahkan hook internal RoxGPS lainnya jika ada

            // Tidak perlu panggil LocationHook.initHooks/performHooking di sini,
            // karena hooking lokasi (yang butuh AIDL) tidak terjadi di proses RoxGPS sendiri.
            return // Hentikan proses handleLoadPackage untuk package RoxGPS
        }

        // --- Cek package aplikasi Target (yang mengonsumsi lokasi) ---
        // Daftar package target lokasi palsu. Daftar ini bisa juga disimpan di LocationHook.
        val targetLocationPackages = listOf(
            "com.google.android.apps.maps", // Google Maps
            "com.waze", // Waze
            // TODO: Tambahkan package aplikasi lain yang mau lo palsukan lokasinya
            // "com.nianticlabs.pokemongo", // Pokemon Go (Contoh)
            // "com.jogja.ojekonline" // Contoh aplikasi ojol
        )

        // Jika package yang di-load ada di daftar target lokasi palsu
        if (targetLocationPackages.contains(lpparam.packageName)) {
            XposedBridge.log("RoxGpsXposed: Target location package found: ${lpparam.packageName}. Delegating to LocationHook.")
            // FileLogger.log("Target location package found: ${lpparam.packageName}. Delegating to LocationHook.", "RoxGpsXposed", "I") // Jika pakai FileLogger

            // --- Mendelegasikan proses hooking lokasi ke LocationHook ---
            // Panggil method inisialisasi di LocationHook untuk package target ini.
            // LocationHook akan menyimpan ClassLoader dan menunggu Service AIDL siap.
            LocationHook.initHooksForTargetPackage(lpparam) // <-- Panggil LocationHook untuk HOOKING!

            // return // Optional: Jika tidak ada hook lain di luar LocationHook untuk package ini
        }

        // --- Hook untuk aplikasi Target Lain (Non-Lokasi), contoh GoFood ---
        // GANTI "com.gojek.app" dengan nama package GoFood yang BENAR
        else if (lpparam.packageName == "com.gojek.app") {
             XposedBridge.log("RoxGpsXposed: Target GoFood package found: ${lpparam.packageName}. Initiating competitor hook.")
             // FileLogger.log("Masuk ke package GoFood: ${lpparam.packageName}", "Gojek", "I") // Jika pakai FileLogger
             // Panggil method hook yang ada di class KampretTokenHooker
             KampretTokenHooker.hook(lpparam) // <-- Panggil hooker GoFood di sini!
            // return // Optional: Jika tidak ada hook lain untuk package ini
        }

        // TODO: Tambahkan cek package lain jika ada hook spesifik untuk package lain
    }

    // =====================================================================
    // Metode Internal untuk Binding Service AIDL (DIPINDAHKAN KE LocationHook)
    // =====================================================================
    /*
    private fun bindToRoxGPSService(appContext: Context) { ... } // <-- DIPINDAHKAN KE LocationHook
    private fun unbindFromRoxGPSService(appContext: Context) { ... } // <-- DIPINDAHKAN KE LocationHook
    */

    // Metode Lain HookEntry jika ada
}
