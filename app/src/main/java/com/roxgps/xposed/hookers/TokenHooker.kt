package com.roxgps.xposed.hookers

import com.roxgps.utils.Relog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import okhttp3.Interceptor
import okhttp3.Response // Ini tetap okhttp3
import okhttp3.OkHttpClient // Ini tetap okhttp3
import okhttp3.Request // Ini tetap okhttp3
import okhttp3.Headers // Jika Anda mengakses headers secara langsung
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Map
import java.util.concurrent.atomic.AtomicReference

object TokenHooker {
    private const val TAG = "KampretHooker"
    private const val FILE_LOGGER_TAG = ""
    private const val HOOK_TAG_C = "HookC"
    private const val HOOK_TAG_INTERCEPT = "HookIntercept"
    //private const val CURRENT_TIMESTAMP = "2025-05-23 16:38:50"
    private const val CURRENT_USER = "kanaminusaena"
    private val storedToken = AtomicReference<String?>(null)

    // Constants for logging
    private const val DATE_FORMAT = "dd-MM-yyyy HH:mm:ss"

    fun hook(lpparam: LoadPackageParam?) {
        if (lpparam == null) {
            Relog.i(TAG, "hook: lpparam is null")
            return
        }

        val targetPackageName = "com..app"
        if (lpparam.packageName != targetPackageName) {
            return
        }

        Relog.i(TAG, """
            Memasang hook spesifik untuk ${lpparam.packageName}
            Waktu: $DATE_FORMAT
            User: $CURRENT_USER
        """.trimIndent())

        hookMethodC(lpparam)
        hookMethodIntercept(lpparam)
    }

    @JvmStatic
    fun getStoredToken(): String? {
        return storedToken.get()?.also {
            Relog.i(TAG, """
                getStoredToken() dipanggil
                Token: ${it.take(5)}...
                Waktu: $DATE_FORMAT
                User: $CURRENT_USER
            """.trimIndent())
        }
    }

    private fun hookMethodC(lpparam: LoadPackageParam) {
        val tmK_ClassName = "Lo.tmK;"
        val c_MethodName = "c"

        try {
            XposedHelpers.findAndHookMethod(
                tmK_ClassName,
                lpparam.classLoader,
                c_MethodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val className = param.method.declaringClass.name
                        val methodName = param.method.name

                        (param.result as? Map<*, *>)?.let { headersMap ->
                            Relog.i(HOOK_TAG_C, """
                                Hooked after $className->$methodName()
                                Hasil Map Header Diterima
                                Waktu: $DATE_FORMAT
                                User: $CURRENT_USER
                            """.trimIndent())

                            processAuthorizationHeader(
                                headersMap["Authorization"] as? String,
                                HOOK_TAG_C
                            )
                        } ?: Relog.w(HOOK_TAG_C, """
                            Hooked after $className->$methodName()
                            Hasil method BUKAN Map: ${param.result}
                            Waktu: $DATE_FORMAT
                        """.trimIndent())
                    }
                }
            )
            Relog.i(TAG, "Hook $tmK_ClassName->$c_MethodName() terpasang.")
        } catch (e: Throwable) {
            handleHookError(e, tmK_ClassName, c_MethodName)
        }
    }

    private fun hookMethodIntercept(lpparam: LoadPackageParam) {
        val interceptorClassName =
            "com.scp.login.sso.hook.network.SSOApiFactory\$httpHeaderInterceptor$2$2"
        val interceptMethodName = "intercept"

        try {
            XposedHelpers.findAndHookMethod(
                interceptorClassName,
                lpparam.classLoader,
                interceptMethodName,
                Interceptor.Chain::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val chain = param.args.firstOrNull() as? Interceptor.Chain ?: return

                        try {
                            val request = chain.request()
                            logRequestDetails(request)
                            processAuthorizationHeader(
                                request.header("Authorization"),
                                HOOK_TAG_INTERCEPT
                            )
                        } catch (e: Exception) {
                            Relog.e(HOOK_TAG_INTERCEPT, """
                                Error processing request: ${e.message}
                                Waktu: $DATE_FORMAT
                            """.trimIndent())
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.result as? Response)?.let { response ->
                            Relog.i(HOOK_TAG_INTERCEPT, """
                                --> Response Details:
                                Code: ${response.code}
                                Headers: ${response.headers}
                                Waktu: $DATE_FORMAT
                            """.trimIndent())
                        }
                    }
                }
            )
            Relog.i(TAG, "Hook $interceptorClassName->$interceptMethodName() terpasang.")
        } catch (e: Throwable) {
            handleHookError(e, interceptorClassName, interceptMethodName)
        }
    }

    private fun processHeaderMap(param: MethodHookParam, hookTag: String) {
        val className = param.method.declaringClass.name
        val methodName = param.method.name

        (param.result as? Map<*, *>)?.let { headersMap ->
            Relog.i(hookTag, "Hooked after $className->$methodName(), Hasil Map Header Diterima.")

            val authHeader = headersMap["Authorization"] as? String
            processAuthorizationHeader(authHeader, hookTag)
        } ?: Relog.w(hookTag, "Hooked after $className->$methodName(), Hasil method BUKAN Map: ${param.result}")
    }

    private fun processInterceptorRequest(param: MethodHookParam, hookTag: String) {
        val chain = param.args.firstOrNull() as? Interceptor.Chain ?: return

        try {
            val request = chain.request()
            if (shouldLogRequest()) {
                logRequestDetails(request)
            }

            processAuthorizationHeader(request.header("Authorization"), hookTag)
        } catch (e: Exception) {
            Relog.e(hookTag, "Error processing request: ${e.message}")
        }
    }

    private fun processInterceptorResponse(param: MethodHookParam, hookTag: String) {
        val className = param.method.declaringClass.name
        val methodName = param.method.name
        Relog.i(hookTag, "Hooked AFTER $className->$methodName()")

        (param.result as? Response)?.let { response ->
            if (shouldLogResponse()) {
                logResponseDetails(response, hookTag)
            }
        }
    }

    private fun processAuthorizationHeader(authHeader: String?, hookTag: String) {
        if (authHeader?.startsWith("Bearer ") == true) {
            val token = authHeader.substringAfter("Bearer ")
            if (token != storedToken.get()) {
                storedToken.set(token)
                Relog.i(hookTag, """
                    Token baru ditemukan dan disimpan
                    Token: ${token.take(5)}...
                    Waktu: $DATE_FORMAT
                    User: $CURRENT_USER
                """.trimIndent())
                // TODO: Opsi: Laporkan ke Service AIDL bahwa token baru tersedia
            }
        } else {
            Relog.d(hookTag, """
                Header Authorization tidak valid: ${authHeader?.take(10)}...
                Waktu: $DATE_FORMAT
            """.trimIndent())
        }
    }

    private fun shouldLogRequest() = true // Implement your logging logic here
    private fun shouldLogResponse() = true // Implement your logging logic here

    private fun logRequestDetails(request: Request) {
        @Suppress("DEPRECATION")
        Relog.i(TAG, """
        --> Request Details:
        URL: ${request.url}           // Menggunakan property url langsung
        Method: ${request.method}
        Headers: ${request.headers}
        Waktu: 2025-05-23 17:10:01
        User: kanaminusaena
    """.trimIndent())
    }

    private fun logResponseDetails(response: Response, hookTag: String) {
        Relog.i(hookTag, """
        --> Response Details:
        Code: ${response.code}
        Headers: ${response.headers}
        Waktu: 2025-05-23 17:10:01
        User: kanaminusaena
    """.trimIndent())
    }

    private fun handleHookError(e: Throwable, className: String, methodName: String) {
        val errorMessage = when (e) {
            is XposedHelpers.ClassNotFoundError -> "GAGAL temukan class"
            is NoSuchMethodError -> "GAGAL temukan method"
            else -> "GAGAL pasang hook"
        }
        Relog.e(TAG, """
            $errorMessage $className->$methodName()
            Error: ${e.message}
            Waktu: $DATE_FORMAT
            User: $CURRENT_USER
        """.trimIndent())
    }

    private fun getCurrentTimestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
    }
}