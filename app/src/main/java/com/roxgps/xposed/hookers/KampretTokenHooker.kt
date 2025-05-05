package com.roxgps.xposed.hookers // Pastikan package ini sesuai

// =====================================================================
// Import Library KampretTokenHooker
// =====================================================================

// import android.util.Log // Tidak diperlukan lagi jika pakai FileLogger
// import de.robv.android.xposed.IXposedHookLoadPackage // Tidak diperlukan jika dipanggil dari HookEntry
import de.robv.android.xposed.XC_MethodHook // Kelas dasar untuk hook metode
import de.robv.android.xposed.XposedBridge // Jembatan komunikasi dengan Xposed Framework (tetap perlu untuk hookMethod dll)
import de.robv.android.xposed.XposedHelpers // Utility untuk mencari kelas dan metode
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam // Parameter saat package dimuat
import de.robv.android.xposed.callbacks.XC_MethodHook.MethodHookParam // Parameter saat metode di-hook

// Imports dari kode kamu (tipe data spesifik Gojek/OkHttp)
import java.lang.reflect.Method // Untuk param.method.getDeclaringClass/getName
import java.util.Map // Untuk tipe Map di hasil hook
import okhttp3.Interceptor // Untuk tipe Interceptor.Chain
import okhttp3.Request // Untuk tipe Request
import okhttp3.Response // Untuk tipe Response
import okhttp3.Headers // Untuk tipe Headers (dari Request/Response)
import okhttp3.HttpUrl // Untuk tipe HttpUrl (dari Request)

import com.roxgps.utils.FileLogger // Mengimpor FileLogger (Pastikan package ini benar!)

// Import untuk thread-safe access ke token
import java.util.concurrent.atomic.AtomicReference


// =====================================================================
// Object KampretTokenHooker
// Mengelola hook spesifik Gojek dan penyimpanan token sementara.
// =====================================================================

// Object singleton atau kelas dengan metode static untuk hook spesifik Gojek
// Dipanggil dari HookEntry.handleLoadPackage
object KampretTokenHooker {

    // Tag untuk log internal logger ini, dan nama file log di FileLogger
    private const val TAG = "KampretHooker" // Tag Log spesifik hooker ini
    private const val FILE_LOGGER_TAG = "Gojek" // Nama file log spesifik hook Gojek di FileLogger

    // =====================================================================
    // Properti Statis untuk Menyimpan Token Sementara (Thread-Safe)
    // =====================================================================
    // Menggunakan AtomicReference untuk akses thread-safe, karena hook bisa dipanggil dari berbagai thread OkHttp
    private val storedGojekToken = AtomicReference<String?>(null) // Menyimpan token Gojek terakhir

    // =====================================================================
    // Metode Statis untuk Memasang Hook (Dipanggil dari HookEntry)
    // =====================================================================

    /**
     * Metode utama untuk memasang hook spesifik Gojek.
     * Dipanggil dari [com.roxgps.xposed.HookEntry].
     *
     * @param lpparam Parameter load package dari Xposed.
     */
    fun hook(lpparam: LoadPackageParam?) {
        // Pastikan lpparam tidak null
        if (lpparam == null) {
            FileLogger.log(TAG, "hook: lpparam is null", FILE_LOGGER_TAG, "E")
            return
        }

        // Ganti "com.gojek.app" dengan package name APLIKASI GOJEK yang TEPAT
        // Ini penting, hooker ini hanya aktif di package Gojek
        val targetPackageName = "com.gojek.app" // <-- UBAH INI SESUAI PACKAGE NAME GOJEK ASLI!
        if (lpparam.packageName != targetPackageName) {
            // Log ini akan spam kalau tidak di dalam cek package di HookEntry
            // FileLogger.log(TAG, "Not target package: ${lpparam.packageName}", FILE_LOGGER_TAG, "D")
            return // Tidak melakukan hook jika bukan package target
        }

        FileLogger.log(TAG, "Memasang hook spesifik untuk Gojek (${lpparam.packageName})", FILE_LOGGER_TAG, "I")

        // Panggil metode hook spesifik Gojek kamu di sini
        hookMethodC(lpparam)
        hookMethodIntercept(lpparam)

        // Tambahkan panggilan ke metode hook Gojek lainnya jika ada
        // hookMetodeLain(lpparam)
    }

    // =====================================================================
    // Metode Statis untuk Menyediakan Token (Dipanggil dari RoxGPS Service)
    // =====================================================================

    /**
     * Metode ini dipanggil dari Service AIDL di aplikasi utama RoxGPS
     * untuk mendapatkan token Gojek yang tersimpan di Xposed Module.
     *
     * @return Token Gojek yang tersimpan, atau null jika belum ada.
     */
    @JvmStatic // Penting agar bisa dipanggil dari Java/Kotlin via XposedHelpers.callStaticMethod
    fun getStoredToken(): String? {
        // Mengambil nilai token secara thread-safe
        val token = storedGojekToken.get()
        FileLogger.log(TAG, "getStoredToken() dipanggil, mengembalikan token.", FILE_LOGGER_TAG, "D")
        return token
    }


    // =====================================================================
    // Metode Hook Spesifik Gojek
    // =====================================================================

    // Metode untuk hook Lo.tmK;->c()
    private fun hookMethodC(lpparam: LoadPackageParam) {
         // Tag spesifik untuk hook ini
         val HOOK_TAG = "HookC"
         val tmK_ClassName = "Lo.tmK;" // <-- VERIFIKASI LAGI NAMA KELAS INI!
         val c_MethodName = "c" // <-- VERIFIKASI LAGI NAMA METODE INI!
         try {

            XposedHelpers.findAndHookMethod(
                tmK_ClassName, // Nama kelas
                lpparam.classLoader, // Class loader package target
                c_MethodName, // Nama metode
                object : XC_MethodHook() { // Implementasi hook
                    // Kode ini dijalankan SETELAH metode target dieksekusi
                    override fun afterHookedMethod(param: MethodHookParam) {
                         // Dapatkan nama kelas dan metode yang di-hook (untuk log)
                         val className = param.method.declaringClass.name
                         val methodName = param.method.name

                         // Coba ambil hasil metode sebagai Map<String, String> (sesuai logic kode kamu)
                         val headersMap = param.result as? Map<String, String>

                         if (headersMap != null) {
                             FileLogger.log(HOOK_TAG, "Hooked after $className->$methodName(), Hasil Map Header Diterima.", FILE_LOGGER_TAG, "D") // Menggunakan FileLogger

                             val authHeader = headersMap["Authorization"]
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                 // Ambil token setelah "Bearer "
                                 val token = authHeader.substringAfter("Bearer ")
                                 FileLogger.log(HOOK_TAG, "--> TOKEN BEARER DITEMUKAN: $token", FILE_LOGGER_TAG, "S") // Menggunakan FileLogger (Level S=Secret/Sensitive?)

                                 // --- SIMPAN TOKEN DI VARIABEL STATIS ---
                                 // Menyimpan token secara thread-safe
                                 storedGojekToken.set(token)
                                 FileLogger.log(HOOK_TAG, "--> TOKEN DISIMPAN SEMENTARA.", FILE_LOGGER_TAG, "D")

                                 // TODO: Opsi: Laporkan ke Service AIDL bahwa token baru tersedia?
                                 // Ini butuh mekanisme sinkronisasi tambahan (misal: module panggil method AIDL notifyNewTokenAvailable())
                                 // Tapi ini lebih kompleks. Metode "Service meminta token" lebih sederhana.

                             } else {
                                FileLogger.log(HOOK_TAG, "--> Header Authorization tidak ditemukan atau formatnya beda: $authHeader", FILE_LOGGER_TAG, "W")
                             }
                         } else {
                            FileLogger.log(HOOK_TAG, "Hooked after $className->$methodName(), Hasil method BUKAN Map: ${param.result}", FILE_LOGGER_TAG, "W")
                         }
                    }
                }
            )
             FileLogger.log(TAG, "Hook $tmK_ClassName->$c_MethodName() terpasang.", FILE_LOGGER_TAG, "I")
        } catch (e: XposedHelpers.FindClassException) {
             FileLogger.log(TAG, "GAGAL temukan class $tmK_ClassName: ${e.message}", FILE_LOGGER_TAG, "E")
        } catch (e: NoSuchMethodError) {
             FileLogger.log(TAG, "GAGAL temukan method $c_MethodName di $tmK_ClassName: ${e.message}", FILE_LOGGER_TAG, "E")
        } catch (e: Throwable) { // Tangkap Throwable umum untuk safety
            FileLogger.log(TAG, "GAGAL pasang hook $tmK_ClassName->$c_MethodName(): ${e.message}", FILE_LOGGER_TAG, "E")
             // Opsional: Laporkan error pasang hook ke Service AIDL (jika Service terhubung saat itu, agak sulit)
             // runCatching { aidlService?.reportHookError("Error hook $tmK_ClassName->$c_MethodName: ${e.message}") } // aidlService tidak tersedia di sini
        }
    }

    // Metode untuk hook intercept() pada interceptor OkHttp
    private fun hookMethodIntercept(lpparam: LoadPackageParam) {
         // Tag spesifik untuk hook ini
         val HOOK_TAG = "HookIntercept"
         val interceptorClassName = "com.scp.login.sso.data.network.SSOApiFactory\$httpHeaderInterceptor\$2\$2" // <-- VERIFIKASI LAGI NAMA KELAS INI!
         val interceptMethodName = "intercept" // Nama metode intercept
         val chainClass = Interceptor.Chain::class.java // Tipe parameter pertama metode intercept
         try {

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
                        // Log ini bisa sangat sering, gunakan Level D atau V jika perlu debug traffic.
                        FileLogger.log(HOOK_TAG, "Hooked BEFORE $className->$methodName()", FILE_LOGGER_TAG, "D")

                        // Ambil objek Interceptor.Chain dari parameter
                        val chain = param.args[0] as? Interceptor.Chain

                        if (chain != null) {
                            // Dapatkan objek Request dari chain
                            val request = chain.request()
                            // Log ini bisa sangat sering dan besar. Gunakan Level D atau V.
                            FileLogger.log(HOOK_TAG, "  --> Req URL: ${request.url}", FILE_LOGGER_TAG, "D")
                            FileLogger.log(HOOK_TAG, "  --> Req Method: ${request.method}", FILE_LOGGER_TAG, "D")
                            FileLogger.log(HOOK_TAG, "  --> Req Headers: ${request.headers}", FILE_LOGGER_TAG, "D") // Logging headers OkHttp aman di hook

                            // Coba ambil Authorization header dari Request
                            val authHeader = request.header("Authorization")
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                // Ambil token
                                val token = authHeader.substringAfter("Bearer ")
                                FileLogger.log(HOOK_TAG, "  --> Req TOKEN DITEMUKAN: $token", FILE_LOGGER_TAG, "S") // Menggunakan FileLogger (Level S=Secret/Sensitive?)

                                 // --- SIMPAN TOKEN DI VARIABEL STATIS ---
                                 // Menyimpan token secara thread-safe
                                 storedGojekToken.set(token)
                                 FileLogger.log(HOOK_TAG, "  --> TOKEN DISIMPAN SEMENTARA.", FILE_LOGGER_TAG, "D")

                                 // TODO: Opsi: Laporkan ke Service AIDL bahwa token baru tersedia? (Sama seperti di hookMethodC)
                                 // Ini butuh mekanisme sinkronisasi tambahan (misal: module panggil method AIDL notifyNewTokenAvailable())
                                 // Tapi ini lebih kompleks. Metode "Service meminta token" lebih sederhana.

                             } else {
                                FileLogger.log(HOOK_TAG, "  --> Req Header Authorization tidak ditemukan atau formatnya beda: $authHeader", FILE_LOGGER_TAG, "D") // Turunkan level log jika tidak penting
                             }
                        }
                    }

                    // Kode ini dijalankan SETELAH metode target dieksekusi
                    override fun afterHookedMethod(param: MethodHookParam) {
                         val className = param.method.declaringClass.name
                         val methodName = param.method.name
                         // Log ini bisa sangat sering
                         FileLogger.log(HOOK_TAG, "Hooked AFTER $className->$methodName()", FILE_LOGGER_TAG, "D")

                         // Ambil objek Response dari hasil metode
                         val response = param.result as? Response

                         if (response != null) {
                             FileLogger.log(HOOK_TAG, "  --> Resp Code: ${response.code}", FILE_LOGGER_TAG, "D")
                             // Logging headers OkHttp aman di hook
                             FileLogger.log(HOOK_TAG, "  --> Resp Headers: ${response.headers}", FILE_LOGGER_TAG, "D")
                             // Catatan: Mengambil body response di after hook SANGAT SENSITIF dan bisa menyebabkan error/crash
                             // response.body?.string() // <-- JANGAN LAKUKAN INI KECUALI SANGAT PERLU DAN HATI-HATI!
                         }
                    }
                }
            )
             FileLogger.log(TAG, "Hook $interceptorClassName->$interceptMethodName() terpasang.", FILE_LOGGER_TAG, "I")
        } catch (e: XposedHelpers.FindClassException) {
             FileLogger.log(TAG, "GAGAL temukan class $interceptorClassName: ${e.message}", FILE_LOGGER_TAG, "E")
        } catch (e: NoSuchMethodError) {
             FileLogger.log(TAG, "GAGAL temukan method $interceptMethodName di $interceptorClassName: ${e.message}", FILE_LOGGER_TAG, "E")
        } catch (e: Throwable) { // Tangkap Throwable umum untuk safety
            FileLogger.log(TAG, "GAGAL pasang hook $interceptorClassName->$interceptMethodName(): ${e.message}", FILE_LOGGER_TAG, "E")
             // Opsional: Laporkan error pasang hook ke Service AIDL (jika Service terhubung saat itu, agak sulit)
             // runCatching { aidlService?.reportHookError("Error hook $interceptorClassName->$interceptMethodName: ${e.message}") } // aidlService tidak tersedia di sini
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
    // Implementasi di atas menggunakan metode "Service AIDL meminta token dari Module" via XposedHelpers.callStaticMethod

}
