package com.roxgps.xposed.hookers // Pastikan package ini bener

// --- TAMBAHKAN IMPORT INI DI SINI ---
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam // Untuk tipe lpparam
import de.robv.android.xposed.callbacks.XC_MethodHook.MethodHookParam // Untuk tipe param di after/beforeHookedMethod
// Untuk param.method.getDeclaringClass().getName()
import java.lang.reflect.Method // Atau import de.robv.android.xposed.XC_MethodHook.MethodHookParam.method jika XC_MethodHook punya nested class MethodHookParam.method
// Paling aman import java.lang.reflect.Method

// Import XposedBridge dan XposedHelpers (kayaknya udah ada)
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

// Import library lain yang lo pakai (OkHttp, Map, FileLogger, dll)
import java.util.Map
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
// ... import lainnya
import com.roxgps.utils.FileLogger // Pastikan import ini bener!

// --- AKHIR IMPORT YANG DITAMBAHKAN ---


object CompetitorTokenHooker {

    fun hook(lpparam: LoadPackageParam) { // Tipe lpparam juga pakai LoadPackageParam dari import
         FileLogger.log("GS_HOOK: Memasang hook spesifik untuk GoFood!", "Gojek", "I")

        hookMethodC(lpparam)
        hookMethodIntercept(lpparam)
    }

    // ... method hookMethodC() dan hookMethodIntercept() di bawah ...

    private fun hookMethodC(lpparam: LoadPackageParam) { // Tipe lpparam pakai LoadPackageParam
         try {
            val tmK_ClassName = "Lo.tmK;" // GANTI kalau nama classnya beda!
            val c_MethodName = "c"

            XposedHelpers.findAndHookMethod(
                tmK_ClassName,
                lpparam.classLoader,
                c_MethodName,
                object : XC_MethodHook() { // XC_MethodHook sekarang dikenal
                    override fun afterHookedMethod(param: MethodHookParam) { // MethodHookParam sekarang dikenal
                         val className = param.method.getDeclaringClass().getName() // param.method sekarang dikenal
                         val methodName = param.method.getName() // param.method sekarang dikenal
                         FileLogger.log("GS_HOOK_C: Hooked after $className->$methodName()", "Gojek", "I")

                         val headersMap = param.result as? Map<String, String> // param.result sekarang dikenal

                         if (headersMap != null) {
                             FileLogger.log("GS_HOOK_C: --> Hasil Map Header Diterima: $headersMap", "Gojek", "D")

                             val authHeader = headersMap["Authorization"]
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                 val token = authHeader.substringAfter("Bearer ")
                                 FileLogger.log("GS_HOOK_C: --> TOKEN BEARER: $token", "Gojek", "S")
                                 // TODO: Kirim 'token' ini ke aplikasi kontrol RoxGPS lo pake IPC (langkah selanjutnya)
                             } else {
                                FileLogger.log("GS_HOOK_C: --> Header Authorization tidak ditemukan atau formatnya beda: $authHeader", "Gojek", "W")
                             }
                         } else {
                            FileLogger.log("GS_HOOK_C: --> Hasil method BUKAN Map: ${param.result}", "Gojek", "W")
                         }
                    }
                }
            )
             FileLogger.log("GS_HOOK_C: Hook Lo.tmK;->c() terpasang.", "Gojek", "I")
        } catch (e: Exception) {
            FileLogger.log("GS_HOOK_C: GAGAL pasang hook Lo.tmK;->c(): ${e.message}", "Gojek", "E")
        }
    }

     // method hookMethodIntercept()
    private fun hookMethodIntercept(lpparam: LoadPackageParam) { // Tipe lpparam pakai LoadPackageParam
         try {
            val interceptorClassName = "com.scp.login.sso.data.network.SSOApiFactory\$httpHeaderInterceptor\$2\$2" // GANTI kalau namanya beda!
            val interceptMethodName = "intercept"
            val chainClass = Interceptor.Chain::class.java
            val responseClass = Response::class.java

            XposedHelpers.findAndHookMethod(
                interceptorClassName,
                lpparam.classLoader,
                interceptMethodName,
                chainClass,
                object : XC_MethodHook() { // XC_MethodHook sekarang dikenal
                    override fun beforeHookedMethod(param: MethodHookParam) { // MethodHookParam sekarang dikenal
                        val className = param.method.getDeclaringClass().getName() // param.method dikenal
                        val methodName = param.method.getName() // param.method dikenal
                        FileLogger.log("GS_HOOK_INTERCEPT: Hooked BEFORE $className->$methodName()", "Gojek", "I")

                        val chain = param.args[0] as? Interceptor.Chain

                        if (chain != null) {
                            val request = chain.request()
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req URL: ${request.url}", "Gojek", "D")
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Method: ${request.method}", "Gojek", "D")
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Headers: ${request.headers}", "Gojek", "D")

                            val authHeader = request.header("Authorization")
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                val token = authHeader.substringAfter("Bearer ")
                                FileLogger.log("GS_HOOK_INTERCEPT:   --> Req TOKEN: $token", "Gojek", "S")
                                // TODO: Kirim 'token' ini ke aplikasi kontrol RoxGPS lo pake IPC
                            } else {
                                FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Header Authorization tidak ditemukan atau formatnya beda: $authHeader", "Gojek", "W")
                            }
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) { // MethodHookParam sekarang dikenal
                         val className = param.method.getDeclaringClass().getName() // param.method dikenal
                         val methodName = param.method.getName() // param.method dikenal
                         FileLogger.log("GS_HOOK_INTERCEPT: Hooked AFTER $className->$methodName()", "Gojek", "I")

                         val response = param.result as? Response // param.result dikenal

                         if (response != null) {
                             FileLogger.log("GS_HOOK_INTERCEPT:   --> Resp Code: ${response.code}", "Gojek", "D")
                             FileLogger.log("GS_HOOK_INTERCEPT:   --> Resp Headers: ${response.headers}", "Gojek", "D")
                         }
                    }
                }
            )
             FileLogger.log("GS_HOOK_INTERCEPT: Hook intercept() terpasang.", "Gojek", "I")
        } catch (e: Exception) {
            FileLogger.log("GS_HOOK_INTERCEPT: GAGAL pasang hook intercept(): ${e.message}", "Gojek", "E")
        }
    }
}
