package com.roxgps.xposed.hookers // Pastikan package ini bener

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Map // Import Map dari Java
import okhttp3.Interceptor // Import library OkHttp yang relevan
import okhttp3.Request
import okhttp3.Response
import okhttp3.Headers
import okhttp3.ResponseBody
import okio.Buffer
import java.nio.charset.Charset
import com.roxgps.utils.FileLogger
// Asumsi class FileLogger ada di package com.roxgps.xposed.utils atau yang bisa diimport
// import com.roxgps.xposed.utils.FileLogger // <-- Pastikan import ini bener!

object CompetitorTokenHooker {

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.gojek.app") { // GANTI dengan nama package GoFood yang BENER
             FileLogger.log("GS_HOOK: Masuk ke package GoFood!", "Gojek", "I") // Ganti log awal

            hookMethodC(lpparam)
            hookMethodIntercept(lpparam) // Masih pasang hook intercept juga kalau mau

        }
    }

    private fun hookMethodC(lpparam: XC_LoadPackage.LoadPackageParam) {
         try {
            val tmK_ClassName = "Lo.tmK;" // GANTI kalau nama classnya beda!
            val c_MethodName = "c"

            XposedHelpers.findAndHookMethod(
                tmK_ClassName,
                lpparam.classLoader,
                c_MethodName,
                object : XposedHelpers.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                         val className = param.method.getDeclaringClass().getName()
                         val methodName = param.method.getName()
                         FileLogger.log("GS_HOOK_C: Hooked after $className->$methodName()", "Gojek", "I") // Ganti log

                         val headersMap = param.result as? Map<String, String>

                         if (headersMap != null) {
                             FileLogger.log("GS_HOOK_C: --> Hasil Map Header Diterima: $headersMap", "Gojek", "D") // Ganti log (level Debug)

                             val authHeader = headersMap["Authorization"]
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                 val token = authHeader.substringAfter("Bearer ")
                                 FileLogger.log("GS_HOOK_C: --> TOKEN BEARER: $token", "Gojek", "S") // <-- Ganti log (level Sensitive/Secret)
                                 // TODO: Kirim 'token' ini ke aplikasi kontrol RoxGPS lo pake IPC (langkah selanjutnya)
                             } else {
                                FileLogger.log("GS_HOOK_C: --> Header Authorization tidak ditemukan atau formatnya beda: $authHeader", "Gojek", "W") // Ganti log (level Warning)
                             }
                         } else {
                            FileLogger.log("GS_HOOK_C: --> Hasil method BUKAN Map: ${param.result}", "Gojek", "W") // Ganti log (level Warning)
                         }
                    }
                }
            )
             FileLogger.log("GS_HOOK_C: Hook Lo.tmK;->c() terpasang.", "Gojek", "I") // Ganti log
        } catch (e: Exception) {
            FileLogger.log("GS_HOOK_C: GAGAL pasang hook Lo.tmK;->c(): ${e.message}", "Gojek", "E") // Ganti log (level Error)
        }
    }
     // ... method hookMethodIntercept() di bawah ini juga diganti log-nya ...
     // private fun hookMethodIntercept(lpparam: ...) { ... }
    // ... hook method() di atas ini ...
    private fun hookMethodIntercept(lpparam: XC_LoadPackage.LoadPackageParam) {
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
                object : XposedHelpers.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.method.getDeclaringClass().getName()
                        val methodName = param.method.getName()
                        FileLogger.log("GS_HOOK_INTERCEPT: Hooked BEFORE $className->$methodName()", "Gojek", "I") // Ganti log

                        val chain = param.args[0] as? Interceptor.Chain

                        if (chain != null) {
                            val request = chain.request() // DAPET OBJEK REQUEST ASLI
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req URL: ${request.url}", "Gojek", "D") // Ganti log
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Method: ${request.method}", "Gojek", "D") // Ganti log
                            FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Headers: ${request.headers}", "Gojek", "D") // Ganti log

                            val authHeader = request.header("Authorization")
                             if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                val token = authHeader.substringAfter("Bearer ")
                                FileLogger.log("GS_HOOK_INTERCEPT:   --> Req TOKEN: $token", "Gojek", "S") // <-- Ganti log (level Sensitive/Secret)
                                // TODO: Kirim 'token' ini ke aplikasi kontrol RoxGPS lo pake IPC
                            } else {
                                FileLogger.log("GS_HOOK_INTERCEPT:   --> Req Header Authorization tidak ditemukan atau formatnya beda: $authHeader", "Gojek", "W")
                            }

                            // Optional: Baca Request Body (hati-hati!)
                            /*
                            val requestBody = request.body
                            if (requestBody != null) {
                                try {
                                    val buffer = Buffer()
                                    requestBody.writeTo(buffer)
                                    val charset = requestBody.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
                                    FileLogger.log("GS_HOOK_INTERCEPT:   --> Request Body: ${buffer.readString(charset)}", "Gojek", "V") // Level Verbose
                                } catch (e: Exception) {
                                    FileLogger.log("GS_HOOK_INTERCEPT: Error baca Request Body: ${e.message}", "Gojek", "E")
                                }
                            }
                             */
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                         val className = param.method.getDeclaringClass().getName()
                         val methodName = param.method.getName()
                         FileLogger.log("GS_HOOK_INTERCEPT: Hooked AFTER $className->$methodName()", "Gojek", "I") // Ganti log

                         val response = param.result as? Response

                         if (response != null) {
                             FileLogger.log("GS_HOOK_INTERCEPT:   --> Resp Code: ${response.code}", "Gojek", "D") // Ganti log
                             FileLogger.log("GS_HOOK_INTERCEPT:   --> Resp Headers: ${response.headers}", "Gojek", "D") // Ganti log

                             // Optional: Baca Response Body (HATI-HATI! Gunakan buffer.clone() agar tidak merusak stream asli)
                             /*
                             val responseBody = response.body
                             if (responseBody != null) {
                                 try {
                                     val source = responseBody.source()
                                     source.request(Long.MAX_VALUE) // Buffer the entire response
                                     val buffer = source.buffer()
                                     val charset = responseBody.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
                                     val responseBodyString = buffer.clone().readString(charset) // CLONE buffer
                                     FileLogger.log("GS_HOOK_INTERCEPT:   --> Response Body: $responseBodyString", "Gojek", "V") // Level Verbose
                                     // TODO: Kalau responsenya JSON data restoran, parse string ini di sini!
                                 } catch (e: Exception) {
                                     FileLogger.log("GS_HOOK_INTERCEPT: Error baca Response Body: ${e.message}", "Gojek", "E")
                                 }
                             }
                             */
                         }
                    }
                }
            )
             FileLogger.log("GS_HOOK_INTERCEPT: Hook intercept() terpasang.", "Gojek", "I") // Ganti log
        } catch (e: Exception) {
            FileLogger.log("GS_HOOK_INTERCEPT: GAGAL pasang hook intercept(): ${e.message}", "Gojek", "E") // Ganti log
        }
    }
}
