package com.roxgps.xposed.hookers // Pastikan package ini sesuai

// =====================================================================
// Import Library TokenHooker
// =====================================================================

// import android.util.Log // Tidak diperlukan lagi jika pakai FileLogger
// import de.robv.android.xposed.IXposedHookLoadPackage // Tidak diperlukan jika dipanggil dari HookEntry

// Imports dari kode kamu (tipe hook spesifik /OkHttp)

// Import untuk thread-safe access ke token
import com.roxgps.utils.Relog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Map
import java.util.concurrent.atomic.AtomicReference

// =====================================================================
// Object TokenHooker
// Mengelola hook spesifik  dan penyimpanan token sementara.
// =====================================================================

// Object singleton atau kelas dengan metode static untuk hook spesifik 
// Dipanggil dari HookEntry.handleLoadPackage
object TokenHooker {

    // Tag untuk log internal logger ini, dan nama file log di FileLogger
    private const val TAG = "KampretHooker" // Tag Log spesifik hooker ini
    private const val FILE_LOGGER_TAG = "" // Nama file log spesifik hook  di FileLogger

    // =====================================================================
    // Properti Statis untuk Menyimpan Token Sementara (Thread-Safe)
    // =====================================================================
    // Menggunakan AtomicReference untuk akses thread-safe, karena hook bisa dipanggil dari berbagai thread OkHttp
    private val storedToken = AtomicReference<String?>(null) // Menyimpan token  terakhir

    // =====================================================================
    // Metode Statis untuk Memasang Hook (Dipanggil dari HookEntry)
    // =====================================================================

    /**
     * Metode utama untuk memasang hook spesifik .
     * Dipanggil dari [com.roxgps.xposed.HookEntry].
     *
     * @param lpparam Parameter load package dari Xposed.
     */
    fun hook(lpparam: LoadPackageParam?) {
        // Pastikan lpparam tidak null
        if (lpparam == null) {
            Relog.i(TAG, "hook: lpparam is null")
            return
        }

        // Ganti "com..app" dengan package name APLIKASI  yang TEPAT
        // Ini penting, hooker ini hanya aktif di package 
        val targetPackageName = "com..app" // <-- UBAH INI SESUAI PACKAGE NAME  ASLI!
        if (lpparam.packageName != targetPackageName) {
            // Log ini akan spam kalau tidak di dalam cek package di HookEntry
            // Relog.i(TAG, "Not target package: ${lpparam.packageName}")
            return // Tidak melakukan hook jika bukan package target
        }

        Relog.i(TAG, "Memasang hook spesifik untuk  (${lpparam.packageName})")

        // Panggil metode hook spesifik  kamu di sini
        hookMethodC(lpparam)
        hookMethodIntercept(lpparam)

        // Tambahkan panggilan ke metode hook  lainnya jika ada
        // hookMetodeLain(lpparam)
    }

    // =====================================================================
    // Metode Statis untuk Menyediakan Token (Dipanggil dari RoxGPS Service)
    // =====================================================================

    /**
     * Metode ini dipanggil dari Service AIDL di aplikasi utama RoxGPS
     * untuk mendapatkan token  yang tersimpan di Xposed Module.
     *
     * @return Token  yang tersimpan, atau null jika belum ada.
     */
    @JvmStatic // Penting agar bisa dipanggil dari Java/Kotlin via XposedHelpers.callStaticMethod
    fun getStoredToken(): String? {
        // Mengambil nilai token secara thread-safe
        val token = storedToken.get()
        Relog.i(TAG, "getStoredToken() dipanggil, mengembalikan token.")
        return token
    }


    // =====================================================================
    // Metode Hook Spesifik 
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
                         val headersMap = param.result as? Map<*, *>

                         if (headersMap != null) {
                             Relog.i(HOOK_TAG, "Hooked after $className->$methodName(), Hasil Map Header Diterima.") // Menggunakan FileLogger

                             val authHeader = headersMap["Authorization"]
                             if (authHeader is String && authHeader.startsWith("Bearer ")) {
                                 // Ambil token setelah "Bearer "
                                 val token = authHeader.substringAfter("Bearer ")
                                 Relog.i(HOOK_TAG, "--> TOKEN BEARER DITEMUKAN: $token") // Menggunakan FileLogger (Level S=Secret/Sensitive?)

                                 // --- SIMPAN TOKEN DI VARIABEL STATIS ---
                                 // Menyimpan token secara thread-safe
                                 storedToken.set(token)
                                 Relog.i(HOOK_TAG, "--> TOKEN DISIMPAN SEMENTARA.")

                                 // TODO: Opsi: Laporkan ke Service AIDL bahwa token baru tersedia?
                                 // Ini butuh mekanisme sinkronisasi tambahan (misal: module panggil method AIDL notifyNewTokenAvailable())
                                 // Tapi ini lebih kompleks. Metode "Service meminta token" lebih sederhana.

                             } else {
                                Relog.w(HOOK_TAG, "--> Header Authorization tidak ditemukan atau formatnya beda: $authHeader")
                             }
                         } else {
                            Relog.w(HOOK_TAG, "Hooked after $className->$methodName(), Hasil method BUKAN Map: ${param.result}")
                         }
                    }
                }
            )
             Relog.i(TAG, "Hook $tmK_ClassName->$c_MethodName() terpasang.")
        } catch (e: XposedHelpers.ClassNotFoundError) {
             Relog.i(TAG, "GAGAL temukan class $tmK_ClassName: ${e.message}")
        } catch (e: NoSuchMethodError) {
             Relog.i(TAG, "GAGAL temukan method $c_MethodName di $tmK_ClassName: ${e.message}")
        } catch (e: Throwable) { // Tangkap Throwable umum untuk safety
            Relog.i(TAG, "GAGAL pasang hook $tmK_ClassName->$c_MethodName(): ${e.message}")
             // Opsional: Laporkan error pasang hook ke Service AIDL (jika Service terhubung saat itu, agak sulit)
             // runCatching { aidlService?.reportHookError("Error hook $tmK_ClassName->$c_MethodName: ${e.message}") } // aidlService tidak tersedia di sini
        }
    }

    // Metode untuk hook intercept() pada interceptor OkHttp
    private fun hookMethodIntercept(lpparam: LoadPackageParam) {
         // Tag spesifik untuk hook ini
         val HOOK_TAG = "HookIntercept"
         val interceptorClassName = "com.scp.login.sso.hook.network.SSOApiFactory\$httpHeaderInterceptor\$2\$2" // <-- VERIFIKASI LAGI NAMA KELAS INI!
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
                        Relog.i(HOOK_TAG, "Hooked BEFORE $className->$methodName()")

                        // Ambil objek Interceptor.Chain dari parameter
                        val chain = param.args[0] as? Interceptor.Chain

                        if (chain != null) {
                            // Dapatkan objek Request dari chain
                            val request = chain.request()
                            // Log ini bisa sangat sering dan besar. Gunakan Level D atau V.
                            Relog.i(HOOK_TAG, "  --> Req URL: ${request.url()}")
                            Relog.i(HOOK_TAG, "  --> Req Method: ${request.method()}")
                            Relog.i(HOOK_TAG, "  --> Req Headers: ${request.headers()}") // Logging headers OkHttp aman di hook

                            // Coba ambil Authorization header dari Request
                            val authHeader = request.header("Authorization")
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                // Ambil token
                                val token = authHeader.substringAfter("Bearer ")
                                Relog.i(HOOK_TAG, "  --> Req TOKEN DITEMUKAN: $token") // Menggunakan FileLogger (Level S=Secret/Sensitive?)

                                 // --- SIMPAN TOKEN DI VARIABEL STATIS ---
                                 // Menyimpan token secara thread-safe
                                 storedToken.set(token)
                                 Relog.i(HOOK_TAG, "  --> TOKEN DISIMPAN SEMENTARA.")

                                 // TODO: Opsi: Laporkan ke Service AIDL bahwa token baru tersedia? (Sama seperti di hookMethodC)
                                 // Ini butuh mekanisme sinkronisasi tambahan (misal: module panggil method AIDL notifyNewTokenAvailable())
                                 // Tapi ini lebih kompleks. Metode "Service meminta token" lebih sederhana.

                             } else {
                                Relog.i(HOOK_TAG, "  --> Req Header Authorization tidak ditemukan atau formatnya beda: $authHeader") // Turunkan level log jika tidak penting
                             }
                        }
                    }

                    // Kode ini dijalankan SETELAH metode target dieksekusi
                    override fun afterHookedMethod(param: MethodHookParam) {
                         val className = param.method.declaringClass.name
                         val methodName = param.method.name
                         // Log ini bisa sangat sering
                         Relog.i(HOOK_TAG, "Hooked AFTER $className->$methodName()")

                         // Ambil objek Response dari hasil metode
                         val response = param.result as? Response

                         if (response != null) {
                             Relog.i(HOOK_TAG, "  --> Resp Code: ${response.code()}")
                             // Logging headers OkHttp aman di hook
                             Relog.i(HOOK_TAG, "  --> Resp Headers: ${response.headers()}")
                             // Catatan: Mengambil body response di after hook SANGAT SENSITIF dan bisa menyebabkan error/crash
                             // response.body?.string() // <-- JANGAN LAKUKAN INI KECUALI SANGAT PERLU DAN HATI-HATI!
                         }
                    }
                }
            )
             Relog.i(TAG, "Hook $interceptorClassName->$interceptMethodName() terpasang.")
        } catch (e: XposedHelpers.ClassNotFoundError) {
             Relog.i(TAG, "GAGAL temukan class $interceptorClassName: ${e.message}")
        } catch (e: NoSuchMethodError) {
             Relog.i(TAG, "GAGAL temukan method $interceptMethodName di $interceptorClassName: ${e.message}")
        } catch (e: Throwable) { // Tangkap Throwable umum untuk safety
            Relog.i(TAG, "GAGAL pasang hook $interceptorClassName->$interceptMethodName(): ${e.message}")
             // Opsional: Laporkan error pasang hook ke Service AIDL (jika Service terhubung saat itu, agak sulit)
             // runCatching { aidlService?.reportHookError("Error hook $interceptorClassName->$interceptMethodName: ${e.message}") } // aidlService tidak tersedia di sini
        }
    }

    // Jika ada metode hook lain, tambahkan di sini
    // private fun hookMetodeLain(lpparam: LoadPackageParam) { ... }


    // Catatan Penting:
    // Nama kelas  Lo.tmK; dan com.scp.login.sso.hook.network.SSOApiFactory$httpHeaderInterceptor$2$2
    // serta nama metode c dan intercept SANGAT RENTAN BERUBAH saat  melakukan update.
    // Kamu perlu memverifikasi nama-nama ini setiap  update jika hooknya tidak berfungsi.
    // Mengambil token dari header Authorization Bearer adalah pendekatan yang umum.
    // Logik IPC untuk mengirim token ke aplikasi kontrol (RoxGPS) perlu kamu implementasikan.
    // Implementasi di atas menggunakan metode "Service AIDL meminta token dari Module" via XposedHelpers.callStaticMethod

}
