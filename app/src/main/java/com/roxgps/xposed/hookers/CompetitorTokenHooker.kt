package com.roxgps.xposed.hookers // Pastikan package ini sesuai

import android.util.Log // Untuk logging Android
import de.robv.android.xposed.IXposedHookLoadPackage // Interface utama Xposed Hook
import de.robv.android.xposed.XC_MethodHook // Kelas dasar untuk hook metode
import de.robv.android.xposed.XposedBridge // Jembatan komunikasi dengan Xposed Framework
import de.robv.android.xposed.XposedHelpers // Utility untuk mencari kelas dan metode
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam // Parameter saat package dimuat
import de.robv.android.xposed.callbacks.XC_MethodHook.MethodHookParam // Parameter saat metode di-hook

// Imports dari kode kamu
import java.lang.reflect.Method // Untuk param.method.getDeclaringClass/getName
import java.util.Map // Untuk tipe Map di hasil hook
import okhttp3.Interceptor // Untuk tipe Interceptor.Chain
import okhttp3.Request // Untuk tipe Request
import okhttp3.Response // Untuk tipe Response
import okhttp3.Headers // Untuk tipe Headers (dari Request/Response)
import okhttp3.HttpUrl // Untuk tipe HttpUrl (dari Request)

import com.roxgps.utils.FileLogger // Mengimpor FileLogger (Pastikan package ini benar!)

// Kelas hook Xposed utama untuk menangani logic spesifik Gojek
// Mengimplementasikan IXposedHookLoadPackage agar dipanggil oleh Xposed Framework
class CompetitorTokenHooker : IXposedHookLoadPackage {

    private val TAG = "RoxGPSXposed" // Tag untuk log XposedBridge
    private val FILE_LOGGER_TAG = "Gojek" // Tag untuk FileLogger

    // Metode utama yang dipanggil Xposed Framework saat package dimuat
    // Di sinilah logic hook Gojek kamu dijalankan
    override fun handleLoadPackage(lpparam: LoadPackageParam?) {
        // Pastikan lpparam tidak null dan package yang di-hook adalah Gojek
        if (lpparam == null) {
            Log.e(TAG, "handleLoadPackage: lpparam is null")
            return
        }

        // Ganti "com.gojek.app" dengan package name APLIKASI GOJEK yang TEPAT
        // Kamu bisa cari package name Gojek di App Info di HP Android atau pakai aplikasi Package Name Viewer
        val targetPackageName = "com.gojek.app" // <-- UBAH INI SESUAI PACKAGE NAME GOJEK ASLI!
        if (lpparam.packageName != targetPackageName) {
            return // Tidak melakukan hook jika bukan package target
        }

        Log.i(TAG, "Hooking package: ${lpparam.packageName}")
        FileLogger.log("GS_HOOK: Memasang hook spesifik untuk GoFood!", FILE_LOGGER_TAG, "I")

        // Panggil metode hook spesifik Gojek kamu di sini
        hookMethodC(lpparam)
        hookMethodIntercept(lpparam)

        // Tambahkan panggilan ke metode hook Gojek lainnya jika ada
        // hookMetodeLain(lpparam)
    }

    // Metode untuk hook Lo.tmK;->c()
    private fun hookMethodC(lpparam: LoadPackageParam) {
         try {
            // Nama kelas dan metode target di Gojek
            // Lo.tmK; adalah nama kelas yang di-deobfuscate/ProGuard (mungkin berubah di update Gojek)
            val tmK_ClassName = "Lo.tmK;" // <-- VERIFIKASI LAGI NAMA KELAS INI!
            val c_MethodName = "c" // <-- VERIFIKASI LAGI NAMA METODE INI!

            XposedHelpers.findAndHookMethod(
                tmK_ClassName, // Nama kelas
                lpparam.classLoader, // Class loader package target
                c_MethodName, // Nama metode
                object : XC_MethodHook() { // Implementasi hook
                    // Kode ini dijalankan SETELAH metode target dieksekusi
                    override fun afterHookedMethod(param: MethodHookParam) {
                         // Dapatkan nama kelas dan metode yang di-hook
                         val className = param.method.declaringClass.name
                         val methodName = param.method.name
                         FileLogger.log("GS_HOOK_C: Hooked after $className->$methodName()", FILE_LOGGER_TAG, "I")

                         // Coba ambil hasil metode sebagai Map<String, String> (sesuai logic kode kamu)
                         val headersMap = param.result as? Map<String, String>

                         if (headersMap != null) {
                             FileLogger.log("GS_HOOK_C: --> Hasil Map Header Diterima: $headersMap", FILE_LOGGER_TAG, "D")

                             val authHeader = headersMap["Authorization"]
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                 // Ambil token setelah "Bearer "
                                 val token = authHeader.substringAfter("Bearer ")
                                 FileLogger.log("GS_HOOK_C: --> TOKEN BEARER: $token", FILE_LOGGER_TAG, "S")

                                 // TODO: Kirim 'token' ini ke aplikasi kontrol RoxGPS lo pake IPC (LANGKAH SELANJUTNYA!)
                                 // Implementasi IPC di sini, misal:
                                 // val intent = Intent("com.roxgps.ACTION_RECEIVE_TOKEN") // Action Intent khusus
                                 // intent.putExtra("token", token) // Sisipkan token ke Intent
                                 // context.sendBroadcast(intent) // Kirim broadcast Intent
                                 // Untuk kirim broadcast ke aplikasi lain, perlu set package penerima dan permission jika perlu
                             } else {
                                FileLogger.log("GS_HOOK_C: --> Header Authorization tidak ditemukan atau formatnya beda: $authHeader", FILE_LOGGER_TAG, "W")
                             }
                         } else {
                            FileLogger.log("GS_HOOK_C: --> Hasil method BUKAN Map: ${param.result}", FILE_LOGGER_TAG, "W")
                         }
                    }
                }
            )
             FileLogger.log("GS_HOOK_C: Hook $tmK_ClassName->$c_MethodName() terpasang.", FILE_LOGGER_TAG, "I")
        } catch (e: Exception) {
            // Log error jika gagal memasang hook
            FileLogger.log("GS_HOOK_C: GAGAL pasang hook $tmK_ClassName->$c_MethodName(): ${e.message}", FILE_LOGGER_TAG, "E")
            XposedBridge.log("RoxGPSXposed: Error hooking $tmK_ClassName->$c_MethodName(): ${e.message}") // Log juga ke log XposedBridge
        }
    }

    // Metode untuk hook intercept() pada interceptor OkHttp
    private fun hookMethodIntercept(lpparam: LoadPackageParam) {
         try {
            // Nama kelas interceptor OkHttp (biasanya nama inner class atau anonymous class, sangat rentan berubah)
            // com.scp.login.sso.data.network.SSOApiFactory$httpHeaderInterceptor$2$2
            val interceptorClassName = "com.scp.login.sso.data.network.SSOApiFactory\$httpHeaderInterceptor\$2\$2" // <-- VERIFIKASI LAGI NAMA KELAS INI!
            val interceptMethodName = "intercept" // Nama metode intercept
            val chainClass = Interceptor.Chain::class.java // Tipe parameter pertama metode intercept

            XposedHelpers.findAndHookMethod(
                interceptorClassName, // Nama kelas interceptor
                lpparam.classLoader, // Class loader package target
                interceptMethodName, // Nama metode
                chainClass, // Tipe parameter pertama (Interceptor.Chain)
                object : XC_MethodHook() { // Implementasi hook
                    // Kode ini dijalankan SEBELUM metode target dieksekusi
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.method.declaringClass.name
                        val methodName = param.method.name
                        FileLogger.log("GS_HOOK_INTERCEPT: Hooked BEFORE $className->$methodName()", FILE_LOGGER_TAG, "I")

                        // Ambil objek Interceptor.Chain dari parameter
                        val chain = param.args[0] as? Interceptor.Chain

                        if (chain != null) {
                            // Dapatkan objek Request dari chain
                            val request = chain.request()
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req URL: ${request.url}", FILE_LOGGER_TAG, "D")
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Method: ${request.method}", FILE_LOGGER_TAG, "D")
                            // Logging headers OkHttp aman di hook
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Headers: ${request.headers}", FILE_LOGGER_TAG, "D")

                            // Coba ambil Authorization header dari Request
                            val authHeader = request.header("Authorization")
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                // Ambil token
                                val token = authHeader.substringAfter("Bearer ")
                                FileLogger.log("GS_HOOK_INTERCEPT:   --> Req TOKEN: $token", FILE_LOGGER_TAG, "S")

                                // TODO: Kirim 'token' ini ke aplikasi kontrol RoxGPS lo pake IPC (LANGKAH SELANJUTNYA!)
                                 // Implementasi IPC di sini, misal:
                                 // val intent = Intent("com.roxgps.ACTION_RECEIVE_TOKEN") // Action Intent khusus
                                 // intent.putExtra("token", token) // Sisipkan token ke Intent
                                 // context.sendBroadcast(intent) // Kirim broadcast Intent
                             } else {
                                FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Header Authorization tidak ditemukan atau formatnya beda: $authHeader", FILE_LOGGER_TAG, "W")
                             }
                        }
                    }

                    // Kode ini dijalankan SETELAH metode target dieksekusi
                    override fun afterHookedMethod(param: MethodHookParam) {
                         val className = param.method.declaringClass.name
                         val methodName = param.method.name
                         FileLogger.log("GS_HOOK_INTERCEPT: Hooked AFTER $className->$methodName()", FILE_LOGGER_TAG, "I")

                         // Ambil objek Response dari hasil metode
                         val response = param.result as? Response

                         if (response != null) {
                             FileLogger.log("GS_HOOK_INTERCEPT:   --> Resp Code: ${response.code}", FILE_LOGGER_TAG, "D")
                             // Logging headers OkHttp aman di hook
                             FileLogger.log("GS_HOOK_INTERCEPT:   --> Resp Headers: ${response.headers}", FILE_LOGGER_TAG, "D")
                             // Catatan: Mengambil body response di after hook SANGAT SENSITIF dan bisa menyebabkan error
                         }
                    }
                }
            )
             FileLogger.log("GS_HOOK_INTERCEPT: Hook $interceptorClassName->$interceptMethodName() terpasang.", FILE_LOGGER_TAG, "I")
        } catch (e: Exception) {
            // Log error jika gagal memasang hook
            FileLogger.log("GS_HOOK_INTERCEPT: GAGAL pasang hook $interceptorClassName->$interceptMethodName(): ${e.message}", FILE_LOGGER_TAG, "E")
             XposedBridge.log("RoxGPSXposed: Error hooking $interceptorClassName->$interceptMethodName(): ${e.message}") // Log juga ke log XposedBridge
        }
    }

    // Jika ada metode hook lain, tambahkan di sini
    // private fun hookMetodeLain(lpparam: LoadPackageParam) { ... }


    // Catatan Penting:
    // Nama kelas Gojek Lo.tmK; dan com.scp.login.sso.data.network.SSOApiFactory$httpHeaderInterceptor$2$2
    // serta nama metode c dan intercept SANGAT RENTAN BERUBAH saat Gojek melakukan update.
    // Kamu perlu memverifikasi nama-nama ini setiap Gojek update jika hooknya tidak berfungsi.
    // Mengambil token dari header Authorization Bearer adalah pendekatan yang umum.
    // Logik IPC untuk mengirim token ke aplikasi kontrol (RoxGPS) perlu kamu implementasikan.

}
