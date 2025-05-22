// File: com/roxgps/xposed/LocationHook.kt
package com.roxgps.xposed // <<< PASTIKAN INI SESUAI DENGAN PACKAGE MODUL XPOSED KAMU <<<

// === Import Library yang Dibutuhkan ===
// ====================================
// === Import Interface AIDL dan Data Class AIDL ===
// PENTING: File .aidl harus sudah DICOPY ke src/main/aidl MODUL Xposed dan dikompilasi!

// === Import Kelas Android yang Dibutuhkan untuk Hooking ===
// =========================================================
// Tambahkan import kelas Android lain jika metode yang dihook membutuhkannya (misal LocationRequest, LocationListener, PendingIntent)
// import android.location.LocationRequest
// import android.location.LocationListener
// import android.app.PendingIntent
// === Import untuk HiddenApiBypass ===
// PENTING: Pastikan library HiddenApiBypass sudah ditambahkan di build.gradle modul Xposed

// === Import untuk RemoteException ===

// === Import untuk SecurityException ===

// === Import untuk AndroidAppHelper (untuk mendapatkan calling package name dan context) ===

// === Import untuk Random Noise ===

// === Import untuk Thread Management ===
import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.pm.ApplicationInfo
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Debug
import android.os.RemoteException
import android.os.SystemClock
import android.util.Base64
import com.roxgps.IRoxAidlService
import com.roxgps.data.FakeLocationData
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Field
import java.util.Collections
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Any
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Double
import kotlin.Exception
import kotlin.Float
import kotlin.FloatArray
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.apply
import kotlin.collections.List
import kotlin.collections.any
import kotlin.collections.arrayListOf
import kotlin.collections.average
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.contentEquals
import kotlin.collections.contentHashCode
import kotlin.collections.filterNot
import kotlin.collections.forEach
import kotlin.collections.getOrNull
import kotlin.collections.lastIndex
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mapOf
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.toByteArray
import kotlin.collections.toFloatArray
import kotlin.collections.toIntArray
import kotlin.collections.toList
import kotlin.collections.toString
import kotlin.floatArrayOf
import kotlin.getValue
import kotlin.lazy
import kotlin.let
import kotlin.math.abs
import kotlin.random.Random
import kotlin.repeat
import kotlin.synchronized
import kotlin.to

// =====================================================================
// LocationHook Object - Kode Final Konsolidasi dan Anti-Deteksi
// Mengambil hook lokasi palsu LENGKAP dari Service AIDL dan menyuntikkannya,
// dengan implementasi ignorePkg di setiap hook callback dan anti-deteksi.
// Ditambah hook untuk menyembunyikan keberadaan modul dengan filter yang lebih kuat.
// Ditambah pencegahan Time Consistency Check.
// Ditambah bypass getNetworkProvider.
// Ditambah Provider Consistency dan Random Realistic Satellite Count.
// Ditambah String Obfuscation (Base64 + XOR).
// Ditambah System App Check.
// Ditambah Error Handling yang Lebih Robust.
// Ditambah Cache Layer untuk efisiensi.
// Ditambah Thread Safety.
// Ditambah Integrity Checks dan Anti-Debugging.
// Ditambah Provider Rotation.
// Ditambah Network State Masking.
// Ditambah Random Bearing Changes.
// Ditambah Refined Location Creation.
// Ditambah Pattern Detection.
// Ditambah Stack Trace Check.
// Ditambah Rate Limiting.
// Ditambah Input Validation.
// Ditambah Circuit Breaker for AIDL.
// Ditambah Failsafe Mechanism.
// Ditambah Velocity Check.
// Ditambah Altitude Consistency.
// Ditambah Realistic Satellite Data.
// Ditambah System Property Spoofing.
// Ditambah Random Sleep.
// Ditambah Thread Management for AIDL Calls.
// Ditambah Magnetic Field Simulation.
// Ditambah Network State Consistency.
// Ditambah Sensor Integration.
// Ditambah Device Motion Pattern.
// Ditambah Enhanced Failsafe Mechanism.
// Ditambah Location Request Interceptor.
// Ditambah Enhanced Provider Protection.
// Ditambah GMS Location Protection.
// Ditambah Location Data Validation.
// Ditambah Passive Provider Protection.
// =====================================================================
object LocationHook {

    // === ENHANCEMENT: Tambahkan Salt/Key untuk XOR Encryption ===
    // Generate key acak saat Module pertama kali dimuat.
    private val encryptionKey = ByteArray(32).apply { Random.nextBytes(this) } // Key 32 byte (256 bit)

    // === Helper untuk mendekode string Base64 (dan opsional XOR) ===
    // Digunakan untuk string obfuscation.
    private fun decrypt(encoded: String, useKey: Boolean = false): String {
        return try {
            // Menggunakan Base64 Android util untuk decode
            val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)

            val finalBytes = if (useKey) {
                // Implementasi XOR dengan key jika useKey true
                decodedBytes.mapIndexed { index, byte ->
                    // XOR byte dengan byte dari encryptionKey (ulang key jika lebih pendek)
                    (byte.toInt() xor encryptionKey[index % encryptionKey.size].toInt()).toByte()
                }.toByteArray()
            } else {
                // Jika useKey false, gunakan hasil decode Base64 langsung
                decodedBytes
            }
            // Konversi byte array ke String menggunakan UTF-8
            finalBytes.toString(Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            // Handle jika string Base64 tidak valid
            XposedBridge.log("[$TAG] Decrypt failed: Invalid Base64 string: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            encoded // Kembalikan string asli jika gagal decode
        } catch (e: Exception) {
            // Handle error lain saat decode/XOR
            XposedBridge.log("[$TAG] Decrypt failed: Unexpected error: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            encoded // Kembalikan string asli jika gagal decode
        }
    }

    // Helper untuk mengenkode string menggunakan Base64 (dan opsional XOR)
    private fun encrypt(original: String, useKey: Boolean = false): String {
        return try {
            val originalBytes = original.toByteArray(Charsets.UTF_8)
            val finalBytes = if (useKey) {
                originalBytes.mapIndexed { index, byte ->
                    (byte.toInt() xor encryptionKey[index % encryptionKey.size].toInt()).toByte()
                }.toByteArray()
            } else {
                originalBytes
            }
            Base64.encodeToString(finalBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            XposedBridge.log("[$TAG] Encrypt failed: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            original // Kembalikan string asli jika gagal encode
        }
    }


    // Tag untuk log Xposed. Filter logcat dengan tag ini. Di-obfuscate.
    private val TAG = decrypt("UjB4R3BzWHBvc2VkOkxvY2F0aW9uSG9vaw==", useKey = true) // "RoxGpsXposed:LocationHook"

    // Interval minimal (ms) sebelum hook callback meminta update hook lagi dari Service AIDL.
    // Mengontrol seberapa sering hook di-refresh dari Service di sisi hook.
    private const val LOCATION_UPDATE_INTERVAL_HOOK_CHECK = 80L // Sesuaikan jika perlu

    // Variabel untuk menyimpan hook lokasi palsu terbaru yang DIDAPATKAN dari Service AIDL.
    // Ini diupdate secara berkala oleh updateFakeLocationDataFromService().
    // === ENHANCEMENT: Tambahkan @Volatile untuk memastikan visibilitas antar thread ===
    @Volatile private var latestFakeLocationData: FakeLocationData? = null

    // Referensi ke objek IRoxAidlService yang didapat dari binding di HookEntry.
    // Ini diset oleh HookEntry.onServiceConnected().
    private var aidlService: IRoxAidlService? = null

    // Waktu terakhir kali hook lokasi palsu berhasil diperbarui dari Service.
    private var mLastUpdatedFromService: Long = 0

    // === Objek kunci untuk sinkronisasi saat mengakses/mengupdate state bersama ===
    // Digunakan dengan synchronized().
    private val updateLock = Object() // === ENHANCEMENT: Objek kunci untuk sinkronisasi ===

    // === Daftar package yang akan diabaikan (tidak disuntik lokasi palsu) ===
    // String package name di-obfuscate menggunakan Base64 (tanpa XOR key untuk kesederhanaan).
    private val ignorePkg = arrayListOf(
        decrypt("Y29tLmFuZHJvaWQubG9jYXRpb24uZnVzZWQ="), // "com.android.location.fused"
        decrypt("Y29tLmdvb2dsZS5hbmRyb2lkLmdtcw=="),     // "com.google.android.gms"
        decrypt("YW5kcm9pZA=="),                    // "android"
        decrypt("Y29tLnJveGdwcw==")                  // <<< INI PACKAGE APLIKASI UTAMA KAMU SENDIRI! PASTIkan ID-nya Benar! "com.roxgps"
        // TODO: Tambahkan package lain yang ingin kamu abaikan di sini (di-obfuscate)
    )

    // Helper untuk Reflection field mIsFromMockProvider (diinisialisasi saat pertama kali digunakan)
    private val mIsFromMockProviderField: Field? by lazy {
        try {
            // Mencari field mIsFromMockProvider menggunakan Reflection (nama field di-obfuscate dengan key)
            Location::class.java.getDeclaredField(decrypt("bUlzRnJvbU1vY2tQcm92aWRlcg==", useKey = true)).apply { // "mIsFromMockProvider"
                isAccessible = true // Set field agar bisa diakses meskipun private
            }
        } catch (e: Exception) {
            // Log error jika field tidak ditemukan (nama field bisa beda antar versi/ROM)
            XposedBridge.log("[$TAG] Failed to get mIsFromMockProvider field via Reflection: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            null // Kembalikan null jika field tidak ditemukan
        }
    }

    // === Helper untuk Reflection field mIsFromNetwork (diinisialisasi saat pertama kali digunakan) ===
    // Digunakan untuk Network State Masking.
    private val mIsFromNetworkField: Field? by lazy {
        try {
            // Mencari field mIsFromNetwork menggunakan Reflection (nama field di-obfuscate dengan key)
            Location::class.java.getDeclaredField(decrypt("bUlzRnJvbU5ldHdvcms=", useKey = true)).apply { // "mIsFromNetwork"
                isAccessible = true // Set field agar bisa diakses meskipun private
            }
        } catch (e: Exception) {
            // Log error jika field tidak ditemukan
            XposedBridge.log("[$TAG] Failed to get mIsFromNetwork field via Reflection: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            null // Kembalikan null jika field tidak ditemukan
        }
    }


    // === Helper untuk mendapatkan waktu yang konsisten (elapsedRealtimeNanos) ===
    // Digunakan untuk set Location.time dan Location.elapsedRealtimeNanos
    private fun getConsistentTime(): Long {
        return try {
            // Coba gunakan SystemClock.elapsedRealtimeNanos() (lebih akurat dan stabil)
            SystemClock.elapsedRealtimeNanos()
        } catch (e: Exception) {
            // Fallback ke System.nanoTime() jika SystemClock.elapsedRealtimeNanos() gagal (sangat jarang)
            XposedBridge.log("[$TAG] getConsistentTime: SystemClock.elapsedRealtimeNanos() failed: ${e.message ?: "Unknown error"}. Falling back to System.nanoTime().") // FIX: Handle nullable message
            System.nanoTime()
        }
    }

    // === Daftar provider lokasi yang didukung untuk konsistensi ===
    // String provider di-obfuscate menggunakan Base64 (tanpa XOR key untuk kesederhanaan).
    private val supportedProviders = setOf(
        decrypt("Z3Bz"), // "gps"
        decrypt("bmV0d29yaw=="), // "network"
        decrypt("cGFzc2l2ZQ=="), // "passive"
        decrypt("ZnVzZWQ=") // "fused"
        // TODO: Tambahkan nama provider lain jika perlu (di-obfuscate)
    )

    // === Helper untuk mengecek apakah package adalah aplikasi sistem ===
    // Digunakan di hook callbacks untuk menghindari faking pada aplikasi sistem.
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            // Dapatkan PackageManager dari context aplikasi saat ini
            val pm = AndroidAppHelper.currentApplication().packageManager
            // Dapatkan ApplicationInfo untuk package name
            val ai = pm.getApplicationInfo(packageName, 0)
            // Cek flag FLAG_SYSTEM di ApplicationInfo
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            // Jika terjadi error (misal, package tidak ditemukan), asumsikan bukan aplikasi sistem
            XposedBridge.log("[$TAG] isSystemApp: Error checking package $packageName: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            false
        }
    }

    // === Helper untuk mengecek kesehatan Service AIDL ===
    // Menggunakan metode ping() di AIDL Service (asumsi ada).
    private fun isServiceHealthy(): Boolean {
        return try {
            // Panggil metode ping() di AIDL Service. Asumsi ping() mengembalikan Boolean.
            // Jika aidlService null atau panggilan ping() melempar RemoteException, anggap tidak sehat.
            aidlService?.ping() == true // <<< Asumsi ada metode ping() di IRoxAidlService.aidl
        } catch (e: RemoteException) {
            // Jika RemoteException terjadi, Service tidak responsif
            XposedBridge.log("[$TAG] isServiceHealthy: RemoteException during ping: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            false
        } catch (e: Exception) {
            // Handle error lain saat ping
            XposedBridge.log("[$TAG] isServiceHealthy: Unexpected error during ping: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            false
        }
    }

    // === Helper untuk mereset state lokasi di sisi Hook ===
    // Dipanggil jika Service tidak sehat atau ada error fatal.
    private fun resetLocationState() {
        XposedBridge.log("[$TAG] resetLocationState: Resetting local location state.")
        latestFakeLocationData = null // @Volatile memastikan update ini terlihat
        mLastUpdatedFromService = 0
        LocationCache.clearCache() // Bersihkan cache
        PatternDetector.clearHistory() // Bersihkan history PatternDetector
        VelocityChecker.clearState() // Bersihkan state VelocityChecker
        AltitudeChecker.clearState() // Bersihkan state AltitudeChecker
        SatelliteSimulator.clearState() // Bersihkan state SatelliteSimulator
        FailsafeManager.clearState() // Bersihkan state FailsafeManager
        MagneticFieldSimulator.clearState() // === ENHANCEMENT: Bersihkan state MagneticFieldSimulator ===
        NetworkStateSimulator.clearState() // === ENHANCEMENT: Bersihkan state NetworkStateSimulator ===
        SensorSimulator.clearState() // === ENHANCEMENT: Bersihkan state SensorSimulator ===
        MotionPatternSimulator.clearState() // === ENHANCEMENT: Bersihkan state MotionPatternSimulator ===
        ProviderProtector.clearState() // === ENHANCEMENT: Bersihkan state ProviderProtector ===
        LocationValidator.clearState() // === ENHANCEMENT: Bersihkan state LocationValidator ===
        // TODO: Tambahkan reset state lain jika ada (misal: cached listeners, pending intents)
    }

    // === Helper untuk menangani berbagai jenis error ===
    // Dipanggil dari blok catch di updateFakeLocationDataFromService.
    //@Suppress("SENSELESS_COMPARISON")
    private fun handleError(error: Exception) { // <<< Tanda tangan diubah menjadi Exception
        // Log error awal (jenis dan pesan). 'error' di sini dijamin Exception.
        XposedBridge.log("[$TAG] Error occurred: ${error.javaClass.simpleName} - ${error.message ?: "Unknown error"}")

        // Lakukan penanganan spesifik jika ada. Tindakan umum (reset state) dilakukan di luar when.
        // Blok when sekarang beroperasi pada Exception.
        when (error) { // Di sini error dijamin sebagai Exception
            is RemoteException -> {
                XposedBridge.log("[$TAG] AIDL remote exception detected.")
                // Tindakan spesifik untuk RemoteException jika ada
            }
            is SecurityException -> {
                XposedBridge.log("[$TAG] Security exception detected.")
                // Tindakan spesifik untuk SecurityException jika ada
            }
            // Menangani semua Exception lain yang BUKAN RemoteException atau SecurityException.
            // Ini akan menangkap semua Exception yang tidak ditangkap oleh cabang di atasnya.
            // Cabang ini akan menangkap misalnya RuntimeException, NullPointerException, IllegalArgumentException, dll.
            // Nama cabangnya tetap 'is Exception' atau bisa juga diubah menjadi 'else' DI SINI
            // karena di konteks when(error: Exception), 'else' berarti 'semua Exception lainnya'.
            else -> { // <<< Di konteks when(error: Exception), 'else' berarti 'semua Exception lainnya'
                XposedBridge.log("[$TAG] General or other Exception detected.")
                // Tindakan spesifik untuk Exception umum jika ada
            }
        }


        // Tindakan penanganan error umum yang dilakukan untuk SEMUA jenis error
        // Ini dilakukan SETELAH blok when, dan HANYA SATU KALI.
        synchronized(updateLock) {
            XposedBridge.log("[$TAG] Performing general error recovery: Clearing cache and resetting state.")
            LocationCache.clearCache() // Bersihkan cache (dilakukan di sini saja)
            resetLocationState() // Reset state lokasi (dilakukan di sini saja)
            // Log bahwa kita akan menggunakan failsafe setelah error
            XposedBridge.log("[$TAG] Error handled. Using failsafe (null location).")
        }

        // Log stack trace untuk debugging
        try {
            // Perhatikan: printStackTrace() di Throwable tetap bisa menerima objek Exception
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            XposedBridge.log("[$TAG] Stack trace:\n$sw")
        } catch (e: Exception) {
            XposedBridge.log("[$TAG] Failed to log stack trace: ${e.message ?: "Unknown error"}")
        }
    }

    // === Cache Layer untuk menyimpan lokasi terakhir secara singkat ===
    // Membantu menghindari panggilan AIDL berulang jika hook dipanggil sangat sering.
    private object LocationCache {
        // Variabel untuk menyimpan objek Location terakhir yang berhasil dibuat/diambil
        private var lastLocation: Location? = null
        // Waktu (dalam milliseconds) saat lastLocation terakhir diperbarui
        private var lastUpdateTime: Long = 0
        // Usia maksimum cache dalam milliseconds. Jika hook lebih tua dari ini, cache dianggap tidak valid.
        private const val MAX_CACHE_AGE = 100L // Sesuaikan jika perlu. 100ms adalah nilai yang wajar untuk responsivitas.

        /**
         * Mencoba mendapatkan objek Location dari cache jika masih valid.
         /** @param provider Provider yang diminta (tidak digunakan untuk validasi cache saat ini, tapi bisa ditambahkan).*/
         * @return Objek Location dari cache jika valid, atau null jika cache kosong atau sudah kadaluarsa.
         */
        fun getCachedLocation(): Location? {
            val now = System.currentTimeMillis()
            // Cek apakah ada hook di cache DAN apakah usia cache masih di bawah MAX_CACHE_AGE
            return if (lastLocation != null && now - lastUpdateTime < MAX_CACHE_AGE) {
                // XposedBridge.log("[$TAG] LocationCache: Cache hit for provider ${provider ?: "unknown provider"}.") // FIX: Handle nullable provider (optional, can be noisy)
                lastLocation // Kembalikan objek dari cache
            } else {
                // XposedBridge.log("[$TAG] LocationCache: Cache miss or expired for provider ${provider ?: "unknown provider"}.") // FIX: Handle nullable provider (optional, can be noisy)
                null // Cache kosong atau sudah kadaluarsa
            }
        }

        /**
         * Memperbarui cache dengan objek Location yang baru.
         * @param location Objek Location baru yang akan disimpan di cache.
         */
        fun updateCache(location: Location) {
            lastLocation = location
            lastUpdateTime = System.currentTimeMillis() // Simpan waktu update cache
            // XposedBridge.log("[$TAG] LocationCache: Cache updated.") // Log ini bisa sangat berisik
        }

        /**
         * Membersihkan cache.
         */
        fun clearCache() {
            lastLocation = null
            lastUpdateTime = 0
            XposedBridge.log("[$TAG] LocationCache: Cache cleared.")
        }
    }

    // === Flag untuk menandai apakah deteksi Xposed/debugging aktif ===
    // Jika true, faking akan dinonaktifkan.
    @Volatile private var isDetectionTriggered = false // === ENHANCEMENT: Flag deteksi ===

    // === Helper untuk melakukan cek anti-deteksi ===
    // Mengecek keberadaan file/properti Xposed dan status debugger.
    // Jika deteksi terjadi, set isDetectionTriggered menjadi true.
    private fun runDetectionChecks(): Boolean {
        // Jika deteksi sudah terpicu sebelumnya, tidak perlu cek lagi.
        if (isDetectionTriggered) {
            // XposedBridge.log("[$TAG] runDetectionChecks: Detection already triggered. Skipping checks.") // Log ini bisa sangat berisik
            return true // Deteksi aktif
        }

        // === ENHANCEMENT: Cek keberadaan file/properti Xposed umum ===
        // String di-obfuscate dengan key.
        val xposedFiles = listOf(
            decrypt("L3N5c3RlbS9iaW5veC94cG9zZWQ=", useKey = true), // "/system/bin/xposed"
            decrypt("L3N5c3RlbS94cG9zZWQuc2g=", useKey = true), // "/system/xposed.sh"
            decrypt("L3N5c3RlbS9saWIveHBvc2VkLnNv", useKey = true), // "/system/lib/xposed.so"
            decrypt("L3N5c3RlbS9saWI2NC94cG9zZWQuc28=", useKey = true) // "/system/lib64/xposed.so"
            // TODO: Tambahkan path file Xposed/LSPosed/EdXposed lain jika perlu (di-obfuscate dengan key)
        )

        val xposedProperties = listOf(
            decrypt("cm8uYnVpbGQuc2VsaW51eA==", useKey = true), // "ro.build.selinux" - sering dimodifikasi oleh Xposed
            decrypt("cm8uYnVpbGQudGFncyE9cmVsZWFzZS1rZXlz", useKey = true), // "ro.build.tags!=release-keys" - sering di-set di custom ROM/rooted
            decrypt("cm8uZGVidWdnYWJsZQ==", useKey = true) // "ro.debuggable" - cek nilainya
            // TODO: Tambahkan nama properti lain yang terkait Xposed/root jika perlu (di-obfuscate dengan key)
        )

        var detected = false

        // Cek file
        for (filePath in xposedFiles) {
            if (File(filePath).exists()) {
                XposedBridge.log("[$TAG] runDetectionChecks: Detected Xposed file: $filePath")
                detected = true
                break // Cukup satu file terdeteksi
            }
        }

        // Cek properti sistem (jika belum terdeteksi oleh file)
        if (!detected) {
            for (propName in xposedProperties) {
                // Menggunakan System.getProperty() untuk membaca properti sistem
                val decodedPropName = decrypt(propName, useKey = true) // Decode nama properti sebelum cek
                val propValue = System.getProperty(decodedPropName)
                // Logika cek properti bisa bervariasi tergantung properti.
                // Contoh sederhana: cek apakah properti ada atau nilainya tidak sesuai standar.
                // Untuk "ro.build.selinux", nilai standar biasanya "enforcing" atau "permissive".
                // Untuk "ro.build.tags", nilai standar biasanya "release-keys".
                // Untuk "ro.debuggable", nilai standar di production build adalah "0".
                when (decodedPropName) { // Decode nama properti untuk perbandingan
                    "ro.build.selinux" -> {
                        if (propValue != null && propValue.contains(decrypt("ZGlzYWJsZWQ=", useKey = true), true)) { // "disabled"
                            XposedBridge.log("[$TAG] runDetectionChecks: Detected suspicious property $decodedPropName = $propValue") // FIX: Handle nullable propValue
                            detected = true
                        }
                    }
                    "ro.build.tags" -> { // Perhatikan: nama properti sebenarnya adalah "ro.build.tags"
                        if (propValue != null && !propValue.contains(decrypt("cmVsZWFzZS1rZXlz", useKey = true), true)) { // "release-keys"
                            XposedBridge.log("[$TAG] runDetectionChecks: Detected suspicious property $decodedPropName = $propValue") // FIX: Handle nullable propValue
                            detected = true
                        }
                    }
                    "ro.debuggable" -> { // Perhatikan: nama properti sebenarnya adalah "ro.debuggable"
                        if (propValue == "1") {
                            XposedBridge.log("[$TAG] runDetectionChecks: Detected suspicious property $decodedPropName = $propValue") // FIX: Handle nullable propValue
                            detected = true
                        }
                    }
                    else -> {
                        // Cek properti lain jika ditambahkan
                    }
                }
                if (detected) break
            }
        }


        // === ENHANCEMENT: Cek status debugger ===
        if (!detected && Debug.isDebuggerConnected()) {
            XposedBridge.log("[$TAG] runDetectionChecks: Debugger detected!")
            detected = true
        }

        // Jika deteksi terjadi, set flag isDetectionTriggered
        if (detected) {
            isDetectionTriggered = true // @Volatile memastikan update ini terlihat
            XposedBridge.log("[$TAG] runDetectionChecks: Anti-detection triggered. Faking disabled.")
        }

        return isDetectionTriggered // Kembalikan status deteksi
    }

    // === Helper untuk Time Consistency Check ===
    // Memeriksa apakah waktu lokasi (elapsedRealtimeNanos) konsisten dengan waktu sistem saat ini.
    // Ini membantu mendeteksi lokasi palsu yang memiliki timestamp yang tidak sinkron.
    private const val MAX_TIME_DIFFERENCE_NANOS = 2_000_000_000L // 2 detik dalam nanoseconds (sesuaikan jika perlu)

    private fun isTimeConsistent(location: Location): Boolean {
        val currentTime = SystemClock.elapsedRealtimeNanos()
        val locationTime = location.elapsedRealtimeNanos

        // Hitung selisih absolut antara waktu sistem dan waktu lokasi
        val difference = abs(currentTime - locationTime)

        // Jika selisih di bawah batas maksimum, anggap konsisten
        val consistent = difference < MAX_TIME_DIFFERENCE_NANOS

        // Log (opsional, bisa berisik)
        // if (!consistent) {
        //      XposedBridge.log("[$TAG] Time inconsistency detected! Location time: $locationTime, Current time: $currentTime, Difference: $difference ns")
        // }
        return consistent
    }

    // === Provider Rotation ===
    // Menggunakan AtomicInteger untuk menghitung dan merotasi provider lokasi yang disuntikkan.
    private val providerRotation = AtomicInteger(0)

    private fun getNextProvider(): String {
        // Dapatkan nilai counter saat ini dan tambahkan 1 untuk panggilan berikutnya
        val currentIndex = providerRotation.getAndIncrement()

        // Gunakan modulo untuk merotasi indeks provider
        val providers = supportedProviders.toList() // Ubah set menjadi list untuk akses indeks
        val nextProvider = providers[currentIndex % providers.size]

        // XposedBridge.log("[$TAG] Provider Rotation: Next provider is $nextProvider") // Log (opsional)
        return nextProvider
    }

    // === Network State Masking ===
    // Mencoba menyetel field internal mIsFromNetwork menjadi false.
    private fun maskNetworkState(location: Location) {
        try {
            // Gunakan Reflection pada field mIsFromNetworkField (sudah diinisialisasi)
            mIsFromNetworkField?.setBoolean(location, false)
            // XposedBridge.log("[$TAG] maskNetworkState: Successfully set mIsFromNetwork to false.") // Log (opsional)
        } catch (_: Exception) {
            // Silent catch atau log error jika gagal (misal, field tidak ditemukan)
            // XposedBridge.log("[$TAG] maskNetworkState: Failed to set mIsFromNetwork: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message (optional)
        }
    }

    // === Random Bearing Changes ===
    // Menambah sedikit noise acak pada bearing lokasi.
    private fun addBearingNoise(location: Location) {
        // Cek apakah lokasi memiliki bearing
        if (!location.hasBearing()) return

        val currentBearing = location.bearing
        // Noise acak antara -1.0f dan +1.0f derajat (sesuaikan magnitudo noise jika perlu)
        val noise = (Random.nextFloat() - 0.5f) * 2f
        var newBearing = currentBearing + noise

        // Pastikan bearing tetap dalam rentang 0-360 derajat
        newBearing = (newBearing % 360 + 360) % 360 // Menangani nilai negatif dan di atas 360

        location.bearing = newBearing
        // XposedBridge.log("[$TAG] addBearingNoise: Original bearing ${currentBearing}, New bearing ${newBearing}") // Log (opsional)
    }

    // === Pattern Detection ===
    // Mendeteksi pola gerakan yang terlalu teratur dan menyarankan penambahan noise.
    private object PatternDetector {
        // Menggunakan LinkedList yang disinkronisasi untuk menyimpan history lokasi.
        // LinkedList disinkronisasi karena bisa diakses dari berbagai thread hook.
        private val lastLocations = Collections.synchronizedList(LinkedList<Location>()) // === ENHANCEMENT: Synchronized LinkedList ===
        private const val MAX_HISTORY = 10 // Jumlah lokasi terakhir yang disimpan untuk analisis pola.

        /**
         * Menambahkan lokasi baru ke history. Membuang lokasi terlama jika history melebihi batas.
         * @param location Lokasi yang akan ditambahkan.
         */
        fun addLocation(location: Location) {
            // Sinkronisasi sudah ditangani oleh Collections.synchronizedList
            lastLocations.add(0, location)
            while (lastLocations.size > MAX_HISTORY) {
                lastLocations.removeAt(lastLocations.lastIndex)
            }
            // XposedBridge.log("[$TAG] PatternDetector: Added location. History size: ${lastLocations.size}") // Log (opsional)
        }

        /**
         * Membersihkan history lokasi. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearHistory() {
            // Sinkronisasi sudah ditangani oleh Collections.synchronizedList
            lastLocations.clear()
            XposedBridge.log("[$TAG] PatternDetector: History cleared.")
        }

        /**
         * Menganalisis history lokasi untuk mendeteksi pola yang terlalu teratur.
         * Saat ini hanya memeriksa perbedaan lat/lon antara 3 lokasi terakhir.
         * @return true jika pola terdeteksi, false sebaliknya.
         */
        fun shouldAddNoise(): Boolean {
            // Membutuhkan minimal 3 lokasi untuk analisis pola dasar ini.
            if (lastLocations.size < 3) {
                // XposedBridge.log("[$TAG] PatternDetector: Not enough history (${lastLocations.size}) for pattern check.") // Log (opsional)
                return false
            }

            // Ambil 3 lokasi terbaru dari history.
            // Karena LinkedList disinkronisasi, akses indeks ini aman.
            val loc0 = lastLocations[0] // Lokasi terbaru
            val loc1 = lastLocations[1] // Lokasi sebelumnya
            val loc2 = lastLocations[2] // Lokasi dua sebelumnya

            // === ENHANCEMENT: Cek perbedaan lat/lon antara lokasi berturut-turut ===
            // Jika perbedaan lat/lon antara (loc0 dan loc1) SANGAT MIRIP dengan perbedaan
            // lat/lon antara (loc1 dan loc2), ini mengindikasikan gerakan yang terlalu linier dan teratur.
            // Threshold 0.000001 derajat (~0.1 meter) adalah nilai yang sangat kecil.
            val dLat1 = loc0.latitude - loc1.latitude
            val dLon1 = loc0.longitude - loc1.longitude

            val dLat2 = loc1.latitude - loc2.latitude
            val dLon2 = loc1.longitude - loc2.longitude

            // Hitung selisih absolut antara perbedaan lat/lon
            val diffLat = abs(dLat1 - dLat2)
            val diffLon = abs(dLon1 - dLon2)

            // Log (opsional, bisa berisik)
            // XposedBridge.log("[$TAG] PatternDetector: dLat1=$dLat1, dLat2=$dLat2, diffLat=$diffLat")
            // XposedBridge.log("[$TAG] PatternDetector: dLon1=$dLon1, dLon2=$dLon2, diffLon=$diffLon")


            // Jika selisih perbedaan lat ATAU selisih perbedaan lon SANGAT KECIL, deteksi pola.
            // Threshold bisa disesuaikan.
            val patternDetected = diffLat < 0.000001 || diffLon < 0.000001 // === ENHANCEMENT: Threshold untuk deteksi pola ===

            if (patternDetected) {
                XposedBridge.log("[$TAG] PatternDetector: Too regular movement pattern detected! Adding extra noise.")
            }

            return patternDetected
        }

        // TODO: Tambahkan metode analisis pola yang lebih canggih di sini (misal: cek kecepatan konstan, bearing konstan, dll.)
    }

    // === Stack Trace Check ===
    // Memeriksa stack trace untuk mendeteksi panggilan dari kelas-kelas terkait deteksi atau keamanan.
    private fun makeHookingSafer(param: XC_MethodHook.MethodHookParam) {
        try {
            val stack = Thread.currentThread().stackTrace
            // String yang di-obfuscate dengan key
            val detectionString = decrypt("ZGV0ZWN0aW9u", useKey = true) // "detection"
            val securityString = decrypt("c2VjdXJpdHk=", useKey = true) // "security"

            // Cek setiap elemen di stack trace
            if (stack.any { it.className != null && (it.className.contains(detectionString, true) || it.className.contains(securityString, true)) }) {
                XposedBridge.log("[$TAG] makeHookingSafer: Detected suspicious class in stack trace. Returning null.")
                param.result = null // Set hasil metode hook menjadi null
                // Jangan return di sini, biarkan hook callback asli selesai (meskipun hasilnya sudah null)
            }
        } catch (_: Exception) {
            // Silent catch untuk menghindari crash jika ada masalah dengan stack trace
            // XposedBridge.log("[$TAG] makeHookingSafer: Error checking stack trace: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message (optional)
        }
    }

    // === Circuit Breaker State ===
    // Variabel untuk mengimplementasikan Circuit Breaker pattern untuk panggilan AIDL.
    private var aidlFailureCount = 0 // Menghitung kegagalan panggilan AIDL berturut-turut
    private var lastAidlFailureTime = 0L // Waktu terakhir kali panggilan AIDL gagal
    private const val AIDL_FAILURE_THRESHOLD = 5 // Jumlah kegagalan berturut-turut sebelum Circuit Breaker trip
    private const val AIDL_TIMEOUT_MS = 5000L // Durasi (ms) Circuit Breaker tetap trip sebelum mencoba lagi

    // === Velocity Checker ===
    // Mendeteksi kecepatan tidak realistis antara dua lokasi berturut-turut.
    private object VelocityChecker {
        private var lastLocation: Location? = null // Lokasi terakhir yang berhasil divalidasi kecepatannya
        private var lastTime: Long = 0 // Waktu (elapsedRealtime) saat lastLocation dicatat
        private const val MAX_SPEED_MS = 150.0 // Kecepatan maksimum yang dianggap realistis (540 km/jam)

        /**
         * Memeriksa apakah kecepatan dari lastLocation ke location saat ini realistis.
         * @param location Lokasi saat ini yang akan divalidasi.
         * @return true jika kecepatan realistis atau ini lokasi pertama, false jika kecepatan tidak realistis.
         */
        fun isVelocityRealistic(location: Location): Boolean {
            val last = lastLocation
            val now = SystemClock.elapsedRealtime() // Menggunakan elapsedRealtime untuk konsistensi

            // Jika ini lokasi pertama, tidak ada kecepatan untuk dihitung. Langsung catat dan return true.
            if (last == null) {
                lastLocation = location // Catat lokasi dan waktu saat ini
                lastTime = now
                // XposedBridge.log("[$TAG] VelocityChecker: First location received.") // Log (opsional)
                return true
            }

            // Hitung selisih waktu dalam detik
            val timeDiff = (now - lastTime) / 1000.0 // Konversi ms ke detik

            // Jika selisih waktu zero atau negatif (tidak mungkin terjadi dengan elapsedRealtime yang benar),
            // atau jika selisih waktu terlalu kecil untuk perhitungan kecepatan yang stabil,
            // kita bisa mengabaikan cek kecepatan untuk momen ini atau menganggapnya realistis.
            // Untuk keamanan, kita anggap realistis jika timeDiff <= 0 atau sangat kecil.
            if (timeDiff <= 0.01) { // Misalnya, abaikan jika selisih waktu kurang dari 10ms
                // XposedBridge.log("[$TAG] VelocityChecker: Time difference too small ($timeDiff s). Skipping velocity check.") // Log (opsional)
                // Tetap update lastLocation dan lastTime meskipun cek dilewati, untuk perhitungan berikutnya.
                lastLocation = location
                lastTime = now
                return true
            }


            // Hitung jarak antara lokasi terakhir dan lokasi saat ini dalam meter
            val distance = location.distanceTo(last)

            // Hitung kecepatan dalam meter per detik
            val speed = distance / timeDiff

            // Cek apakah kecepatan melebihi batas maksimum
            if (speed > MAX_SPEED_MS) {
                XposedBridge.log("[$TAG] VelocityChecker: Unrealistic velocity detected: ${"%.2f".format(speed)} m/s (Max: $MAX_SPEED_MS m/s) between ${last.latitude},${last.longitude} and ${location.latitude},${location.longitude} in ${"%.2f".format(timeDiff)} s.")
                // Jangan update lastLocation/lastTime jika kecepatan tidak realistis,
                // agar cek berikutnya tetap membandingkan dengan lokasi realistis terakhir.
                return false // Kecepatan tidak realistis
            }

            // Jika kecepatan realistis, catat lokasi dan waktu saat ini untuk cek berikutnya.
            lastLocation = location
            lastTime = now
            // XposedBridge.log("[$TAG] VelocityChecker: Velocity realistic: ${"%.2f".format(speed)} m/s") // Log (opsional)
            return true // Kecepatan realistis
        }

        /**
         * Membersihkan state VelocityChecker. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            lastLocation = null
            lastTime = 0
            XposedBridge.log("[$TAG] VelocityChecker: State cleared.")
        }
    }

    // === ENHANCEMENT: Altitude Consistency Checker ===
    // Mendeteksi perubahan ketinggian yang tidak realistis antara dua lokasi berturut-turut.
    private object AltitudeChecker {
        private const val MAX_ALTITUDE_CHANGE = 100.0 // Perubahan ketinggian maksimum yang dianggap realistis dalam meter (sesuaikan jika perlu)
        private var lastAltitude: Double? = null // Ketinggian terakhir yang berhasil divalidasi

        /**
         * Memeriksa apakah perubahan ketinggian dari lastAltitude ke newAltitude saat ini realistis.
         * @param newAltitude Ketinggian saat ini yang akan divalidasi.
         * @return true jika perubahan ketinggian realistis atau ini ketinggian pertama, false jika perubahan tidak realistis.
         */
        fun isAltitudeRealistic(newAltitude: Double): Boolean {
            val last = lastAltitude

            // Jika ini ketinggian pertama, tidak ada perubahan untuk dihitung. Langsung catat dan return true.
            if (last == null) {
                lastAltitude = newAltitude // Catat ketinggian saat ini
                // XposedBridge.log("[$TAG] AltitudeChecker: First altitude received.") // Log (opsional)
                return true
            }

            // Hitung perubahan ketinggian absolut
            val change = abs(newAltitude - last)

            // Cek apakah perubahan ketinggian melebihi batas maksimum
            if (change > MAX_ALTITUDE_CHANGE) {
                XposedBridge.log("[$TAG] AltitudeChecker: Unrealistic altitude change detected: ${"%.2f".format(change)} m (Max: $MAX_ALTITUDE_CHANGE m) from ${"%.2f".format(last)} m to ${"%.2f".format(newAltitude)} m.")
                // Jangan update lastAltitude jika perubahan tidak realistis,
                // agar cek berikutnya tetap membandingkan dengan ketinggian realistis terakhir.
                return false // Perubahan ketinggian tidak realistis
            }

            // Jika perubahan ketinggian realistis, catat ketinggian saat ini untuk cek berikutnya.
            lastAltitude = newAltitude
            // XposedBridge.log("[$TAG] AltitudeChecker: Altitude change realistic: ${"%.2f".format(change)} m") // Log (opsional)
            return true // Perubahan ketinggian realistis
        }

        /**
         * Mengembalikan ketinggian terakhir yang valid.
         * @return Ketinggian terakhir yang valid, atau null jika belum ada.
         */
        fun getLastValidAltitude(): Double? {
            return lastAltitude
        }

        /**
         * Membersihkan state AltitudeChecker. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            lastAltitude = null
            XposedBridge.log("[$TAG] AltitudeChecker: State cleared.")
        }
    }

    // === ENHANCEMENT: Realistic Satellite Data Simulator ===
    // Mensimulasikan hook satelit yang lebih dinamis untuk extras Location.
    private object SatelliteSimulator {
        // Data class untuk menyimpan informasi satu satelit
        private data class SatelliteInfo(
            var prn: Int, // Pseudo-Random Noise (nomor identifikasi satelit)
            var elevation: Float, // Sudut elevasi satelit (0-90 derajat)
            var azimuth: Float, // Sudut azimuth satelit (0-360 derajat)
            var snr: Float // Signal-to-Noise Ratio (kekuatan sinyal satelit)
        )

        private val satellites = mutableListOf<SatelliteInfo>() // Daftar satelit yang disimulasikan
        private var lastUpdate = 0L // Waktu terakhir kali hook satelit diperbarui
        private const val UPDATE_INTERVAL = 1000L // Interval (ms) untuk memperbarui hook satelit (misal: setiap 1 detik)

        /**
         * Menghasilkan Bundle extras yang berisi hook satelit yang disimulasikan.
         * Data satelit diperbarui secara berkala berdasarkan UPDATE_INTERVAL.
         * @return Bundle extras dengan hook satelit.
         */
        fun generateSatelliteData(): Bundle {
            val now = SystemClock.elapsedRealtime() // Menggunakan elapsedRealtime untuk konsistensi

            // Perbarui hook satelit jika sudah melewati interval update
            if (now - lastUpdate > UPDATE_INTERVAL) {
                updateSatellites()
                lastUpdate = now
                // XposedBridge.log("[$TAG] SatelliteSimulator: Updating satellite hook.") // Log (opsional)
            } else {
                // XposedBridge.log("[$TAG] SatelliteSimulator: Using cached satellite hook.") // Log (optional)
            }

            // Buat Bundle extras dengan hook satelit saat ini
            return Bundle().apply {
                // String keys di-obfuscate dengan key
                putInt(decrypt("c2F0ZWxsaXRlcw==", useKey = true), satellites.size) // "satellites"
                // Hitung rata-rata SNR dari semua satelit
                putFloat(decrypt("bWVhblNuUg==", useKey = true), satellites.map { it.snr }.average().toFloat()) // "meanSnr"
                // Daftar PRN satelit
                putIntArray(decrypt("cHJucw==", useKey = true), satellites.map { it.prn }.toIntArray()) // "prns"
                // Daftar SNR satelit
                putFloatArray(decrypt("c25ycw==", useKey = true), satellites.map { it.snr }.toFloatArray()) // "snrs"
                // TODO: Tambahkan hook satelit lain jika diperlukan (misal: azimuths, elevations)
                // putFloatArray(decrypt("YXppbXV0aHM=", useKey = true), satellites.map { it.azimuth }.toFloatArray()) // "azimuths"
                // putFloatArray(decrypt("ZWxldmF0aW9ucw==", useKey = true), satellites.map { it.elevation }.toFloatArray()) // "elevations"
            }
        }

        /**
         * Memperbarui hook satelit yang disimulasikan.
         * Jika daftar satelit kosong, inisialisasi dengan hook acak.
         * Jika sudah ada, perbarui posisi (elevasi, azimuth) dan kekuatan sinyal (SNR) secara acak.
         */
        private fun updateSatellites() {
            if (satellites.isEmpty()) {
                // Inisialisasi satelit jika belum ada
                val numberOfSatellites = (6..12).random() // Jumlah satelit realistis (6-12)
                // XposedBridge.log("[$TAG] SatelliteSimulator: Initializing $numberOfSatellites satellites.") // Log (opsional)
                repeat(numberOfSatellites) {
                    satellites.add(SatelliteInfo(
                        prn = (1..32).random(), // PRN satelit GPS (1-32)
                        elevation = Random.nextFloat() * (90f - 15f) + 15f, // Elevasi acak (15-90 derajat)
                        azimuth = Random.nextFloat() * 360f, // Azimuth acak (0-360 derajat)
                        snr = Random.nextFloat() * (45f - 15f) + 15f // SNR acak (15-45 dB)
                    ))
                }
            } else {
                // Perbarui hook satelit yang sudah ada dengan sedikit perubahan acak
                // XposedBridge.log("[$TAG] SatelliteSimulator: Updating existing ${satellites.size} satellites.") // Log (opsional)
                satellites.forEach {
                    // Perbarui elevasi dengan noise acak dan batasi dalam rentang realistis
                    it.elevation += (Random.nextFloat() - 0.5f) * 4f // Noise elevasi ±2 derajat
                    it.elevation = it.elevation.coerceIn(10f, 90f) // Batasi antara 10 dan 90 derajat

                    // Perbarui azimuth dengan noise acak dan pastikan dalam rentang 0-360
                    it.azimuth = (it.azimuth + (Random.nextFloat() - 0.5f) * 10f) % 360 // Noise azimuth ±5 derajat
                    if (it.azimuth < 0) it.azimuth += 360 // Pastikan positif

                    // Perbarui SNR dengan noise acak dan batasi dalam rentang realistis
                    it.snr += (Random.nextFloat() - 0.5f) * 4f // Noise SNR ±2 dB -> (0..1)-0.5 = (-0.5..0.5)*4 = (-2..2)
                    it.snr = it.snr.coerceIn(12f, 45f) // Batasi antara 12 dan 45 dB
                }
            }
        }

        /**
         * Membersihkan state SatelliteSimulator. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            satellites.clear()
            lastUpdate = 0L
            XposedBridge.log("[$TAG] SatelliteSimulator: State cleared.")
        }
    }

    // === ENHANCEMENT: System Property Spoofing ===
    // Mengubah beberapa properti sistem yang sering diperiksa untuk mendeteksi root/debugging/Xposed.
    private object SystemPropertySpoofer {
        // Map untuk menyimpan nilai asli properti sebelum diubah, agar bisa dikembalikan jika perlu (opsional)
        private val originalProps = mutableMapOf<String, String?>()
        // Flag untuk memastikan inisialisasi hanya dilakukan sekali
        private var isInitialized = false

        /**
         * Menginisialisasi SystemPropertySpoofer.
         * Mencoba mengubah properti sistem yang umum terkait deteksi.
         * Harus dipanggil sekali saat Module di-load.
         */
        fun init() {
            // Lakukan inisialisasi hanya sekali
            if (isInitialized) {
                // XposedBridge.log("[$TAG] SystemPropertySpoofer: Already initialized.") // Log (opsional)
                return
            }

            XposedBridge.log("[$TAG] SystemPropertySpoofer: Initializing...")

            try {
                // Menggunakan Reflection untuk mengakses kelas SystemProperties dan metode set
                val systemPropertiesClass = Class.forName(decrypt("YW5kcm9pZC5vcy5TeXN0ZW1Qcm9wZXJ0aWVz", useKey = true)) // "android.os.SystemProperties"
                val setMethod = systemPropertiesClass.getMethod(decrypt("c2V0", useKey = true), String::class.java, String::class.java) // "set"

                // Daftar properti yang akan di-spoof beserta nilai palsunya. String keys dan values di-obfuscate dengan key.
                mapOf(
                    decrypt("cm8uYnVpbGQudHlwZQ==", useKey = true) to decrypt("dXNlcg==", useKey = true), // "ro.build.type" to "user" (bukan "userdebug" atau "eng")
                    decrypt("cm8uZGVidWdnYWJsZQ==", useKey = true) to decrypt("MA==", useKey = true), // "ro.debuggable" to "0" (bukan "1")
                    decrypt("cm8uc2VjdXJl", useKey = true) to decrypt("MQ==", useKey = true) // "ro.secure" to "1" (bukan "0")
                    // TODO: Tambahkan properti lain yang ingin di-spoof di sini (di-obfuscate dengan key)
                    // decrypt("cm8ucm9vdGVk", useKey = true) to decrypt("MA==", useKey = true), // "ro.rooted" to "0" (jika ada properti ini)
                    // decrypt("cm8uYnVpbGQudGFncw==", useKey = true) to decrypt("cmVsZWFzZS1rZXlz", useKey = true) // "ro.build.tags" to "release-keys"
                ).forEach { (key, value) ->
                    try {
                        val decodedKey = decrypt(key, useKey = true)
                        // Simpan nilai asli sebelum diubah (opsional)
                        originalProps[key] = System.getProperty(decodedKey)

                        // Ubah properti menggunakan metode set via Reflection
                        setMethod.invoke(null, decodedKey, decrypt(value, useKey = true)) // Decode key dan value sebelum invoke
                        XposedBridge.log("[$TAG] SystemPropertySpoofer: Spoofed property $decodedKey to ${decrypt(value, useKey = true)}")
                    } catch (e: Exception) {
                        // Silent catch atau log jika gagal mengubah properti tertentu
                        XposedBridge.log("[$TAG] SystemPropertySpoofer: Failed to spoof property ${decrypt(key, useKey = true)}: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
                    }
                }
                isInitialized = true // Set flag setelah inisialisasi berhasil
                XposedBridge.log("[$TAG] SystemPropertySpoofer: Initialization complete.")
            } catch (e: Exception) {
                // Silent catch atau log jika gagal mengakses SystemProperties atau metode set
                XposedBridge.log("[$TAG] SystemPropertySpoofer: Failed to initialize: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            }
        }

        // TODO: Tambahkan metode 'restore' jika kamu ingin mengembalikan properti ke nilai asli saat Module di-disable/unloaded (kompleks)
    }

    // === ENHANCEMENT: Random Sleep for Anti-Pattern ===
    // Menambahkan penundaan acak di awal hook callbacks untuk mengganggu deteksi berbasis timing.
    private fun addRandomDelay() {
        // Sesuaikan probabilitas (misal 0.1f = 10% kemungkinan) dan rentang waktu tidur (misal 1-5 ms)
        if (Random.nextFloat() < 0.1f) { // 10% kemungkinan untuk tidur
            try {
                val delayMs = (1..5).random().toLong() // Tidur antara 1 hingga 5 ms
                // XposedBridge.log("[$TAG] addRandomDelay: Sleeping for ${delayMs}ms.") // Log (opsional)
                Thread.sleep(delayMs)
            } catch (_: Exception) {
                // Silent catch jika terjadi error saat tidur (misal: InterruptedException)
                // XposedBridge.log("[$TAG] addRandomDelay: Error during sleep: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message (optional)
            }
        }
    }

    // === ENHANCEMENT: Thread Management for AIDL Calls ===
    // Menggunakan SingleThreadExecutor untuk menjalankan panggilan AIDL secara asynchronous
    // dan ConcurrentHashMap untuk rate-limit panggilan per "key" (misal: per jenis hook yang diminta).
    private val aidlExecutor = Executors.newSingleThreadExecutor()
    // Map untuk menyimpan waktu terakhir kali panggilan AIDL untuk key tertentu berhasil dieksekusi
    private val aidlTimeouts = ConcurrentHashMap<String, Long>()
    private const val MIN_AIDL_CALL_INTERVAL_MS = 100 // Interval minimal (ms) antara panggilan AIDL untuk key yang sama

    /**
     * Mengeksekusi panggilan AIDL pada thread terpisah dengan rate limiting per key.
     /** @param key Kunci unik untuk mengidentifikasi jenis panggilan AIDL (misal: "location", "status").*/
     * @param call Lambda yang berisi kode panggilan AIDL yang sebenarnya.
     */
    @Suppress("SameParameterValue")
    private fun executeAidlCall(key: String, call: () -> Unit) {
        val lastCallTime = aidlTimeouts[key] ?: 0L
        val currentTime = System.currentTimeMillis()

        // Cek rate limit untuk key ini
        if (currentTime - lastCallTime < MIN_AIDL_CALL_INTERVAL_MS) {
            // XposedBridge.log("[$TAG] executeAidlCall: Rate limited for key $key. Skipping execution.") // Log (opsional)
            return // Lewati eksekusi jika masih dalam periode rate limit
        }

        // Submit panggilan AIDL ke executor thread
        aidlExecutor.execute {
            try {
                call() // Jalankan kode panggilan AIDL
                // Update waktu terakhir kali panggilan berhasil untuk key ini
                aidlTimeouts[key] = System.currentTimeMillis()
                // XposedBridge.log("[$TAG] executeAidlCall: AIDL call successful for key $key.") // Log (opsional)
            } catch (e: Exception) {
                // Log error jika panggilan AIDL gagal
                XposedBridge.log("[$TAG] executeAidlCall: AIDL call failed for key $key: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
                // TODO: Pertimbangkan penanganan error spesifik di sini jika perlu
            }
        }
    }

    // === ENHANCEMENT: Magnetic Field Simulation ===
    // Mensimulasikan hook medan magnet bumi untuk extras Location.
    private object MagneticFieldSimulator {
        private var lastMagneticUpdate = 0L // Waktu terakhir kali hook magnetik diperbarui
        private const val UPDATE_INTERVAL = 500L // Interval (ms) untuk memperbarui hook magnetik (misal: setiap 500ms)
        private var currentValues = floatArrayOf(0f, 0f, 0f) // Nilai medan magnet X, Y, Z

        /**
         * Menghasilkan Bundle extras yang berisi hook medan magnet yang disimulasikan.
         * Data medan magnet diperbarui secara berkala berdasarkan UPDATE_INTERVAL.
         * @return Bundle extras dengan hook medan magnet.
         */
        fun generateMagneticData(): Bundle {
            val now = SystemClock.elapsedRealtime() // Menggunakan elapsedRealtime untuk konsistensi

            // Perbarui hook medan magnet jika sudah melewati interval update
            if (now - lastMagneticUpdate > UPDATE_INTERVAL) {
                updateMagneticField()
                lastMagneticUpdate = now
                // XposedBridge.log("[$TAG] MagneticFieldSimulator: Updating magnetic field hook.") // Log (opsional)
            } else {
                // XposedBridge.log("[$TAG] MagneticFieldSimulator: Using cached magnetic field hook.") // Log (optional)
            }

            // Buat Bundle extras dengan hook medan magnet saat ini
            return Bundle().apply {
                // String keys di-obfuscate dengan key
                putFloatArray(decrypt("bWFnbmV0aWNfZmllbGQ=", useKey = true), currentValues.clone()) // "magnetic_field" - gunakan clone() agar array asli tidak dimodifikasi
                putFloat(decrypt("bWFnbmV0aWNfYWNjdXJhY3k=", useKey = true), 3f) // "magnetic_accuracy" - Set akurasi tinggi (3)
            }
        }

        /**
         * Memperbarui hook medan magnet yang disimulasikan dengan sedikit noise acak.
         */
        private fun updateMagneticField() {
            // Simulasi perubahan nilai medan magnet dengan noise acak
            currentValues[0] += (Random.nextFloat() - 0.5f) * 2f // Noise ±1f
            currentValues[1] += (Random.nextFloat() - 0.5f) * 2f // Noise ±1f
            currentValues[2] += (Random.nextFloat() - 0.5f) * 2f // Noise ±1f

            // Batasi nilai dalam rentang realistis (-60 hingga 60 µT)
            currentValues = currentValues.map {
                it.coerceIn(-60f, 60f)
            }.toFloatArray()
        }

        /**
         * Membersihkan state MagneticFieldSimulator. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            currentValues = floatArrayOf(0f, 0f, 0f)
            lastMagneticUpdate = 0L
            XposedBridge.log("[$TAG] MagneticFieldSimulator: State cleared.")
        }
    }

    // === ENHANCEMENT: Network State Consistency ===
    // Mensimulasikan hook status jaringan (misal: Cell Info) untuk extras Location.
    private object NetworkStateSimulator {
        private var lastCellInfo: Bundle? = null // Bundle terakhir yang berisi hook Cell Info

        /**
         * Menghasilkan Bundle extras yang berisi hook status jaringan yang disimulasikan.
         * Saat ini hanya menghasilkan hook Cell Info awal yang statis.
         * @return Bundle extras dengan hook status jaringan.
         */
        fun generateNetworkState(): Bundle {
            // Jika hook Cell Info belum diinisialisasi, buat hook awal
            if (lastCellInfo == null) {
                lastCellInfo = createInitialCellInfo()
                // XposedBridge.log("[$TAG] NetworkStateSimulator: Creating initial cell info.") // Log (opsional)
            }

            // Buat Bundle extras dengan hook status jaringan
            return Bundle().apply {
                // String keys di-obfuscate dengan key
                putBundle(decrypt("Y2VsbF9pbmZv", useKey = true), lastCellInfo?.deepCopy()) // "cell_info" - gunakan deepCopy() agar Bundle asli tidak dimodifikasi
                putBoolean(decrypt("aXNfbmV0d29ya19sb2NhdGlvbg==", useKey = true), false) // "is_network_location" - Set false
                putInt(decrypt("bmV0d29ya190eXBl", useKey = true), 13) // "network_type" - Set ke 13 (LTE)
                // TODO: Tambahkan hook status jaringan lain jika diperlukan (misal: wifi info, signal strength)
            }
        }

        /**
         * Membuat Bundle awal yang berisi hook Cell Info dasar.
         * Data ini bisa diperluas dan disimulasikan perubahannya di masa depan.
         * @return Bundle dengan hook Cell Info awal.
         */
        private fun createInitialCellInfo(): Bundle {
            return Bundle().apply {
                // String keys di-obfuscate dengan key
                putInt(decrypt("bWNj", useKey = true), 310) // "mcc" - Contoh Mobile Country Code (MCC)
                putInt(decrypt("bW5j", useKey = true), 260) // "mnc" - Contoh Mobile Network Code (MNC)
                putInt(decrypt("Y2lk", useKey = true), Random.nextInt(1000, 65535)) // "cid" - Cell ID acak
                putInt(decrypt("bGFj", useKey = true), Random.nextInt(1000, 65535)) // "lac" - Location Area Code acak
                // TODO: Tambahkan hook Cell Info lain jika diperlukan (misal: arfcn, pci, timingAdvance, rsrp, rsrq, snr)
            }
        }

        /**
         * Membersihkan state NetworkStateSimulator. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            lastCellInfo = null
            XposedBridge.log("[$TAG] NetworkStateSimulator: State cleared.")
        }
    }

    // === ENHANCEMENT: Sensor Integration ===
    // Mensimulasikan hook sensor (misal: Accelerometer) dan mengintegrasikan hook sensor lain (Medan Magnet) ke Location extras.
    private object SensorSimulator {
        // Data class untuk menyimpan state simulasi sensor
        private data class SensorState(
            var values: FloatArray, // Nilai sensor (misal: X, Y, Z for Accelerometer)
            var accuracy: Int, // Akurasi sensor
            var timestamp: Long // Timestamp sensor (elapsedRealtimeNanos)
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as SensorState

                if (accuracy != other.accuracy) return false
                if (timestamp != other.timestamp) return false
                if (!values.contentEquals(other.values)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = accuracy
                result = 31 * result + timestamp.hashCode()
                result = 31 * result + values.contentHashCode()
                return result
            }
        }

        // Map untuk menyimpan state simulasi untuk berbagai jenis sensor (key: sensor type int)
        private val sensorStates = mutableMapOf<Int, SensorState>()

        // TODO: Tambahkan interval update dan logika update untuk setiap jenis sensor jika simulasi perubahannya diperlukan.

        /**
         * Memperbarui hook sensor yang disimulasikan dan menambahkannya ke Location extras.
         * Saat ini hanya mensimulasikan Accelerometer dan menambahkan hook Medan Magnet.
         * @param location Objek Location yang akan diperbarui extras-nya.
         */
        fun updateSensorData(location: Location) {
            val nowNanos = SystemClock.elapsedRealtimeNanos() // Timestamp konsisten
            val isSimulatingMotion = MotionPatternSimulator.shouldSimulateMotion()
            // === Simulasi Accelerometer (Type 1) ===
            // Mensimulasikan nilai Accelerometer. Sekarang, sesuaikan noise berdasarkan status simulasi gerakan.
            // Jika isSimulatingMotion true, gunakan noise yang lebih besar. Jika false, noise lebih kecil.
            val accelerometerNoiseMagnitude = if (isSimulatingMotion) 0.5f else 0.1f // Contoh: Noise ±0.25 jika bergerak, ±0.05 jika diam

            // === Simulasi Accelerometer (Type 1) ===
            // Mensimulasikan nilai Accelerometer dengan sedikit noise di sekitar 0,0,9.81 (gravitasi)
            sensorStates[1] = SensorState(
                values = floatArrayOf(
                    (Random.nextFloat() - 0.5f) * accelerometerNoiseMagnitude * 2f, // Noise X (rentang -magnitude hingga +magnitude)
                    (Random.nextFloat() - 0.5f) * accelerometerNoiseMagnitude * 2f, // Noise Y (rentang -magnitude hingga +magnitude)
                    9.81f + (Random.nextFloat() - 0.5f) * accelerometerNoiseMagnitude * 2f // Gravitasi + Noise Z (rentang 9.81-magnitude hingga 9.81+magnitude)
                ),
                accuracy = if (isSimulatingMotion) 2 else 3, // Contoh: Akurasi bisa sedikit rendah jika bergerak (2=SENSOR_STATUS_ACCURACY_MEDIUM, 3=SENSOR_STATUS_ACCURACY_HIGH)
                timestamp = nowNanos // Timestamp saat ini
            )
            // TODO: Tambahkan simulasi untuk jenis sensor lain (misal: Gyroscope, Gravity, Rotation Vector)

            // === Integrasikan hook sensor lain ke Location extras ===
            // Tambahkan hook Medan Magnet yang disimulasikan
            location.extras?.putAll(MagneticFieldSimulator.generateMagneticData())

            // Tambahkan hook status jaringan yang disimulasikan
            location.extras?.putAll(NetworkStateSimulator.generateNetworkState())

            // TODO: Tambahkan hook sensor lain ke extras jika diperlukan dan disimulasikan
            // location.extras.putFloatArray(decrypt("YWNjZWxlcm9tZXRlcg==", useKey = true), sensorStates[1]?.values?.clone()) // "accelerometer"
            // location.extras.putLong(decrypt("YWNjZWxlcm9tZXRlcl90aW1lc3RhbXA=", useKey = true), sensorStates[1]?.timestamp ?: 0L) // "accelerometer_timestamp"
        }

        /**
         * Membersihkan state SensorSimulator. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            sensorStates.clear()
            MagneticFieldSimulator.clearState() // Bersihkan state MagneticFieldSimulator
            NetworkStateSimulator.clearState() // Bersihkan state NetworkStateSimulator
            XposedBridge.log("[$TAG] SensorSimulator: State cleared.")
        }
    }

    // === ENHANCEMENT: Device Motion Pattern ===
    // Mensimulasikan apakah perangkat dianggap sedang bergerak atau diam.
    // Ini bisa digunakan untuk mempengaruhi simulasi sensor atau logika faking lainnya.
    private object MotionPatternSimulator {
        private var lastMotionUpdate = 0L // Waktu terakhir kali status gerakan diubah
        private var isMoving = false // Status gerakan saat ini (true jika bergerak)
        private const val MOTION_CHANGE_PROBABILITY = 0.1f // Probabilitas (0.0-1.0) status gerakan berubah setiap interval
        private const val UPDATE_INTERVAL = 5000L // Interval (ms) untuk mengevaluasi perubahan status gerakan

        /**
         * Mengevaluasi apakah perangkat harus disimulasikan sedang bergerak.
         * Status gerakan bisa berubah secara acak setiap UPDATE_INTERVAL.
         * @return true jika perangkat disimulasikan sedang bergerak, false jika diam.
         */
        fun shouldSimulateMotion(): Boolean {
            val now = SystemClock.elapsedRealtime() // Menggunakan elapsedRealtime untuk konsistensi

            // Evaluasi perubahan status gerakan jika sudah melewati interval update
            if (now - lastMotionUpdate > UPDATE_INTERVAL) {
                // Dengan probabilitas MOTION_CHANGE_PROBABILITY, balik status gerakan
                if (Random.nextFloat() < MOTION_CHANGE_PROBABILITY) {
                    isMoving = !isMoving
                    XposedBridge.log("[$TAG] MotionPatternSimulator: Motion state changed to isMoving=$isMoving")
                }
                lastMotionUpdate = now // Update waktu evaluasi terakhir
            }

            return isMoving // Kembalikan status gerakan saat ini
        }

        /**
         * Membersihkan state MotionPatternSimulator. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            isMoving = false
            lastMotionUpdate = 0L
            XposedBridge.log("[$TAG] MotionPatternSimulator: State cleared.")
        }
    }

    // === ENHANCEMENT: Enhanced Failsafe Mechanism ===
    // Menyimpan lokasi terakhir yang berhasil disuntikkan dan mengembalikannya jika Service AIDL tidak tersedia atau error.
    private object FailsafeManager {
        private var lastFailsafeLocation: Location? = null // Lokasi terakhir yang berhasil disuntikkan
        private const val MAX_FAILSAFE_AGE = 30_000L // Usia maksimum (ms) lokasi failsafe sebelum dianggap kadaluarsa

        /**
         * Mendapatkan lokasi failsafe jika tersedia dan masih valid.
         * @param provider Provider yang diminta (digunakan untuk membuat objek Location baru jika failsafe valid).
         * @return Objek Location failsafe jika valid, atau null.
         */
        fun getFailsafeLocation(provider: String?): Location? {
            val now = SystemClock.elapsedRealtime() // Menggunakan elapsedRealtime untuk konsistensi
            val last = lastFailsafeLocation

            // Cek apakah ada lokasi failsafe DAN apakah usianya masih di bawah MAX_FAILSAFE_AGE
            if (last == null || now - (last.elapsedRealtimeNanos / 1_000_000) > MAX_FAILSAFE_AGE) {
                if (last != null) {
                    // Log jika lokasi failsafe sudah kadaluarsa
                    XposedBridge.log("[$TAG] FailsafeManager: Last failsafe location expired. Age: ${now - (last.elapsedRealtimeNanos / 1_000_000)} ms.")
                }
                return null // Lokasi failsafe tidak valid atau kadaluarsa
            }

            // Jika lokasi failsafe valid, buat objek Location baru dari hook failsafe
            // Menggunakan provider yang diminta oleh aplikasi.
            val failsafeLocation = Location(provider ?: last.provider).apply {
                // Salin semua properti dari lokasi failsafe
                set(last)
                // Pastikan elapsedRealtimeNanos diupdate ke waktu saat ini untuk konsistensi
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                time = elapsedRealtimeNanos / 1_000_000 // Update time juga
                // TODO: Pertimbangkan untuk menambahkan sedikit noise pada lat/lon/akurasi di sini agar tidak terlalu statis
            }
            // XposedBridge.log("[$TAG] FailsafeManager: Returning failsafe location.") // Log (opsional)
            return failsafeLocation // Kembalikan lokasi failsafe yang sudah diperbarui timestamp-nya
        }

        /**
         * Memperbarui lokasi failsafe dengan lokasi yang baru berhasil disuntikkan.
         * @param location Lokasi yang berhasil disuntikkan.
         */
        fun updateFailsafeLocation(location: Location) {
            // Simpan salinan lokasi yang berhasil disuntikkan sebagai failsafe
            lastFailsafeLocation = Location(location).apply {
                // Pastikan timestamp failsafe location menggunakan elapsedRealtimeNanos
                // Ini penting agar cek usia di getFailsafeLocation() konsisten.
                // Jika location asli sudah memiliki elapsedRealtimeNanos, gunakan itu.
                // Jika tidak, gunakan SystemClock.elapsedRealtimeNanos() saat ini.
                elapsedRealtimeNanos = if (location.elapsedRealtimeNanos > 0) {
                    location.elapsedRealtimeNanos
                } else {
                    SystemClock.elapsedRealtimeNanos()
                }
                time = elapsedRealtimeNanos / 1_000_000 // Update time juga
            }
            // XposedBridge.log("[$TAG] FailsafeManager: Failsafe location updated.") // Log (opsional)
        }

        /**
         * Membersihkan state FailsafeManager. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            lastFailsafeLocation = null
            XposedBridge.log("[$TAG] FailsafeManager: State cleared.")
        }
    }

    // === ENHANCEMENT: Location Request Interceptor ===
    // Memblokir panggilan ke metode-metode Location API tertentu dari kelas-kelas tertentu.
    private object LocationRequestInterceptor {
        // Daftar nama metode yang akan diblokir (di-obfuscate dengan key)
        private val blockedMethods = setOf(
            encrypt("requestLocationUpdates", useKey = true),
            encrypt("getLastLocation", useKey = true),
            encrypt("getCurrentLocation", useKey = true),
            encrypt("getLastKnownLocation", useKey = true),
            encrypt("addGpsStatusListener", useKey = true),
            encrypt("addNmeaListener", useKey = true)
            // TODO: Tambahkan nama metode lain yang ingin diblokir (di-obfuscate dengan key)
        )

        // Daftar nama kelas yang dianggap kritis (di-obfuscate dengan key)
        private val criticalLocationApis = setOf(
            encrypt("android.location.LocationManager", useKey = true),
            encrypt("com.google.android.gms.location.FusedLocationProviderClient", useKey = true),
            encrypt("android.location.Criteria", useKey = true),
            encrypt("android.location.GpsStatus", useKey = true)
            // TODO: Tambahkan nama kelas lain yang dianggap kritis (di-obfuscate dengan key)
        )

        /**
         * Memeriksa apakah permintaan lokasi harus diblokir berdasarkan nama metode dan nama kelas pemanggil.
         * @param methodName Nama metode yang dipanggil.
         * @param className Nama kelas yang memanggil metode.
         * @return true jika permintaan harus diblokir, false sebaliknya.
         */
        fun shouldBlockRequest(methodName: String, className: String): Boolean {
            // Cek apakah nama metode yang di-obfuscate ada di daftar blockedMethods
            // DAN apakah nama kelas yang di-obfuscate ada di daftar criticalLocationApis
            val shouldBlock = blockedMethods.contains(encrypt(methodName, useKey = true)) &&
                    criticalLocationApis.contains(encrypt(className, useKey = true))

            if (shouldBlock) {
                XposedBridge.log("[$TAG] LocationRequestInterceptor: Blocking request to method '${methodName}' from class '${className}'.")
            }
            return shouldBlock
        }
    }

    // === ENHANCEMENT: Enhanced Provider Protection ===
    // Mensimulasikan status provider (enabled/disabled) dan propertinya secara lebih realistis.
    private object ProviderProtector {
        // Map untuk menyimpan status enabled/disabled setiap provider
        private val providerStates = mutableMapOf<String, Boolean>()
        // Map untuk menyimpan waktu terakhir kali status provider diubah
        private val lastProviderChange = mutableMapOf<String, Long>()
        // Interval minimal (ms) sebelum status provider bisa diubah lagi
        private const val MIN_CHANGE_INTERVAL = 30_000L // 30 detik (sesuaikan jika perlu)

        /**
         * Mensimulasikan status enabled/disabled untuk provider tertentu.
         * Status bisa berubah secara acak setiap MIN_CHANGE_INTERVAL.
         * @param provider Nama provider (misal: "gps", "network").
         * @return true jika provider disimulasikan enabled, false jika disabled.
         */
        fun isProviderEnabled(provider: String): Boolean {
            val now = SystemClock.elapsedRealtime()
            val lastChange = lastProviderChange[provider] ?: 0L

            // Evaluasi perubahan status provider jika sudah melewati interval update
            if (now - lastChange > MIN_CHANGE_INTERVAL) {
                // Secara acak ubah status provider. Contoh: 90% kemungkinan enabled.
                val newState = Random.nextFloat() > 0.1f // 90% chance enabled
                providerStates[provider] = newState
                lastProviderChange[provider] = now // Update waktu perubahan terakhir
                XposedBridge.log("[$TAG] ProviderProtector: Provider '${provider}' state changed to enabled=$newState") // FIX: Handle nullable provider
            }

            // Kembalikan status provider saat ini, default true jika belum ada hook
            return providerStates[provider] != false
        }

        /**
         * Mensimulasikan properti untuk provider tertentu.
         * Mengembalikan Bundle dengan properti yang terlihat realistis.
         * @param provider Nama provider (misal: "gps", "network").
         * @return Bundle dengan properti provider.
         */
        fun getProviderProperties(provider: String): Bundle {
            // String keys di-obfuscate dengan key
            return Bundle().apply {
                putBoolean(encrypt("requiresNetwork", useKey = true), false) // "requiresNetwork"
                putBoolean(encrypt("requiresSatellite", useKey = true), provider == decrypt("Z3Bz", useKey = true)) // "requiresSatellite" (true hanya untuk "gps")
                putBoolean(encrypt("requiresCell", useKey = true), false) // "requiresCell"
                putBoolean(encrypt("hasMonetaryCost", useKey = true), false) // "hasMonetaryCost"
                putBoolean(encrypt("supportsAltitude", useKey = true), true) // "supportsAltitude"
                putBoolean(encrypt("supportsSpeed", useKey = true), true) // "supportsSpeed"
                putBoolean(encrypt("supportsBearing", useKey = true), true) // "supportsBearing"
                putInt(encrypt("powerRequirement", useKey = true), 1) // "powerRequirement" - 1 (POWER_LOW)
                putInt(encrypt("accuracy", useKey = true), 1) // "accuracy" - 1 (ACCURACY_FINE)
                // TODO: Tambahkan properti lain jika diperlukan (misal: hasAccuracy, hasSpeed, hasBearing)
            }
        }

        /**
         * Membersihkan state ProviderProtector. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            providerStates.clear()
            lastProviderChange.clear()
            XposedBridge.log("[$TAG] ProviderProtector: State cleared.")
        }
    }

    // === ENHANCEMENT: GMS Location Protection ===
    // Menangani hook lokasi yang mungkin berasal dari GMS (Fused Location Provider)
    // dan mengecek apakah permintaan berasal dari package GMS.
    private object GmsLocationProtector {
        // Daftar package GMS umum (di-obfuscate tanpa key)
        private val gmsPackages = setOf(
            encrypt("com.google.android.gms"),
            encrypt("com.google.android.location"),
            encrypt("com.google.android.apps.maps")
            // TODO: Tambahkan package GMS lain jika perlu (di-obfuscate tanpa key)
        )

        /**
         * Memproses objek Location untuk menghapus/menambah extras yang terkait GMS
         * agar terlihat lebih natural saat disuntikkan.
         * @param location Objek Location yang akan diproses.
         */
        fun handleGmsLocation(location: Location) {
            location.apply {
                // Hapus extras yang umum terkait mock location atau GMS internal
                extras?.remove(encrypt("mockLocation", useKey = true)) // "mockLocation"
                extras?.remove(encrypt("noGPSLocation", useKey = true)) // "noGPSLocation"
                extras?.remove(encrypt("gmsLocationFlags", useKey = true)) // "gmsLocationFlags"
                // TODO: Hapus extras GMS lain yang mencurigakan

                // Tambahkan extras yang terlihat realistis untuk GMS
                extras?.putBundle(encrypt("gms", useKey = true), Bundle().apply { // "gms"
                    putInt(encrypt("confidence", useKey = true), 100) // "confidence" - Kepercayaan tinggi
                    putInt(encrypt("timeInterval", useKey = true), 1000) // "timeInterval" - Interval update (contoh 1 detik)
                    putBoolean(encrypt("hasAccuracy", useKey = true), true) // "hasAccuracy"
                    putBoolean(encrypt("hasSpeed", useKey = true), true) // "hasSpeed"
                    putFloat(encrypt("speedAccuracy", useKey = true), 1.0f) // "speedAccuracy" - Akurasi kecepatan (contoh 1 m/s)
                    // TODO: Tambahkan extras GMS lain yang umum
                })
            }
            // XposedBridge.log("[$TAG] GmsLocationProtector: Processed GMS location extras.") // Log (opsional)
        }

        /**
         * Mengecek apakah package name tertentu adalah package GMS.
         * @param packageName Nama package yang akan dicek.
         * @return true jika package adalah GMS, false sebaliknya.
         */
        fun isGmsRequest(packageName: String): Boolean {
            // Cek apakah package name yang di-obfuscate ada di daftar gmsPackages
            return gmsPackages.contains(encrypt(packageName))
        }
    }

    // === ENHANCEMENT: Location Data Validation ===
    // Memvalidasi hook lokasi yang dihasilkan sebelum disuntikkan.
    private object LocationValidator {
        // Data class untuk menyimpan batas validasi (opsional, bisa disesuaikan)
        /*private hook class Bounds(
            val minLat: Double, val maxLat: Double,
            val minLon: Double, val maxLon: Double
        )*/

        private var lastValidLocation: Location? = null // Lokasi terakhir yang berhasil divalidasi
        private const val MAX_JUMP_DISTANCE = 1000.0 // Jarak maksimum (meter) yang dianggap lompatan tidak realistis

        /**
         * Memvalidasi objek Location.
         * Mengecek batas koordinat, akurasi, kecepatan, dan lompatan mendadak.
         * @param location Objek Location yang akan divalidasi.
         * @return true jika lokasi valid, false sebaliknya.
         */
        fun validateLocation(location: Location): Boolean {
            // Cek batas dasar (koordinat, akurasi, kecepatan)
            if (!isWithinWorldBounds(location)) {
                XposedBridge.log("[$TAG] LocationValidator: Basic bounds check failed for location: Lat=${location.latitude}, Lon=${location.longitude}, Acc=${location.accuracy}, Speed=${location.speed}")
                return false
            }

            // Cek untuk lompatan mendadak dari lokasi terakhir yang valid
            val last = lastValidLocation
            if (last != null) {
                val distance = location.distanceTo(last)
                if (distance > MAX_JUMP_DISTANCE) {
                    XposedBridge.log("[$TAG] LocationValidator: Detected suspicious location jump: ${"%.2f".format(distance)} meters from ${last.latitude},${last.longitude} to ${location.latitude},${location.longitude}")
                    return false
                }
            }

            // Jika semua cek lolos, lokasi dianggap valid. Simpan sebagai lokasi terakhir yang valid.
            lastValidLocation = location
            // XposedBridge.log("[$TAG] LocationValidator: Location validated successfully.") // Log (opsional)
            return true
        }

        /**
         * Mengecek apakah properti dasar lokasi berada dalam rentang yang wajar.
         * @param location Objek Location yang akan dicek.
         * @return true jika dalam batas wajar, false sebaliknya.
         */
        private fun isWithinWorldBounds(location: Location): Boolean {
            // Cek rentang Latitude (-90 hingga 90) dan Longitude (-180 hingga 180)
            // Cek rentang Akurasi (misal: 1m hingga 100m) - sesuaikan jika perlu
            // Cek rentang Kecepatan (misal: 0 m/s hingga 50 m/s ~ 180 km/jam) - sesuaikan jika perlu
            return location.latitude in -90.0..90.0 &&
                    location.longitude in -180.0..180.0 &&
                    location.accuracy in 1f..100f && // Contoh rentang akurasi
                    location.speed in 0f..50f // Contoh rentang kecepatan
            // TODO: Tambahkan cek batas untuk Altitude, Bearing jika perlu
        }

        /**
         * Membersihkan state LocationValidator. Dipanggil saat faking berhenti atau Service terputus.
         */
        fun clearState() {
            lastValidLocation = null
            XposedBridge.log("[$TAG] LocationValidator: State cleared.")
        }
    }

    // === ENHANCEMENT: Passive Provider Protection ===
    // Secara spesifik memblokir permintaan untuk provider "passive".
    private object PassiveProviderBlocker {
        /**
         * Memblokir permintaan untuk provider "passive" dengan menyetel hasil hook menjadi null.
         * @param param Parameter hook metode.
         * @return true jika permintaan diblokir, false sebaliknya.
         */
        fun blockPassiveProvider(param: XC_MethodHook.MethodHookParam): Boolean {
            // Ambil parameter provider dari argumen metode hook (asumsi parameter pertama adalah String provider)
            val provider = param.args.getOrNull(0) as? String

            // Cek apakah provider adalah "passive" (di-obfuscate dengan key)
            if (provider == decrypt("cGFzc2l2ZQ==", useKey = true)) { // "passive"
                param.result = null // Set hasil metode menjadi null untuk memblokir
                XposedBridge.log("[$TAG] PassiveProviderBlocker: Blocked passive provider request.")
                return true // Permintaan diblokir
            }
            return false // Permintaan tidak diblokir
        }
    }


    /**
     * Metode yang dipanggil dari HookEntry.onServiceConnected() untuk memberikan referensi AIDL Service.
     * Ini adalah titik sambung antara HookEntry dan LocationHook.
     * @param service Objek IRoxAidlService yang terhubung, atau null jika terputus.
     */
    fun setAidlService(service: IRoxAidlService?) {
        aidlService = service
        if (service != null) {
            XposedBridge.log("[$TAG] AIDL Service reference received. Attempting initial hook update.")
            // Coba update hook pertama kali saat Service terhubung
            updateFakeLocationDataFromService()
            // Reset Circuit Breaker state saat Service terhubung
            aidlFailureCount = 0
            lastAidlFailureTime = 0L
            // Bersihkan state checker saat Service terhubung
            VelocityChecker.clearState()
            AltitudeChecker.clearState()
            SatelliteSimulator.clearState()
            SensorSimulator.clearState()
            MotionPatternSimulator.clearState()
            FailsafeManager.clearState()
            MagneticFieldSimulator.clearState()
            NetworkStateSimulator.clearState()
            ProviderProtector.clearState() // === ENHANCEMENT: Bersihkan state ProviderProtector saat Service terhubung ===
            LocationValidator.clearState() // === ENHANCEMENT: Bersihkan state LocationValidator saat Service terhubung ===

            // TODO: Jika ada metode report status di Service AIDL (misal setHookStatus(true)), panggil di sini setelah Service terhubung.
            try { aidlService?.setHookStatus(true) } catch (e: RemoteException) { XposedBridge.log("[$TAG] RemoteException reporting hook status true: ${e.message ?: "Unknown error"}") } // FIX: Handle nullable message
        } else {
            XposedBridge.log("[$TAG] AIDL Service reference cleared (connection lost?).")
            // Bersihkan hook lokasi palsu jika koneksi terputus
            latestFakeLocationData = null // @Volatile memastikan update ini terlihat
            mLastUpdatedFromService = 0
            LocationCache.clearCache() // Bersihkan cache saat Service terputus
            PatternDetector.clearHistory() // Bersihkan history PatternDetector saat Service terputus
            VelocityChecker.clearState() // Bersihkan state VelocityChecker saat Service terputus
            AltitudeChecker.clearState() // Bersihkan state AltitudeChecker saat Service terputus
            SatelliteSimulator.clearState() // Bersihkan state SatelliteSimulator saat Service terputus
            SensorSimulator.clearState() // Bersihkan state SensorSimulator saat Service terputus
            MotionPatternSimulator.clearState() // Bersihkan state MotionPatternSimulator saat Service terputus
            FailsafeManager.clearState() // Bersihkan state FailsafeManager saat Service terputus
            MagneticFieldSimulator.clearState() // === ENHANCEMENT: Bersihkan state MagneticFieldSimulator saat Service terputus ===
            NetworkStateSimulator.clearState() // === ENHANCEMENT: Bersihkan state NetworkStateSimulator saat Service terputus ===
            ProviderProtector.clearState() // === ENHANCEMENT: Bersihkan state ProviderProtector saat Service terputus ===
            LocationValidator.clearState() // === ENHANCEMENT: Bersihkan state LocationValidator saat Service terputus ===


            // Set Circuit Breaker state saat Service terputus
            aidlFailureCount = AIDL_FAILURE_THRESHOLD // Langsung trip breaker
            lastAidlFailureTime = System.currentTimeMillis()
            // TODO: Laporkan status 'Disconnected' ke aplikasi utama jika ada metode AIDL untuk ini.
            // try { aidlService?.reportHookStatus(HookStatus.Disconnected.javaClass.name) } catch (e: RemoteException) { /* ignore */ }
        }
    }

    /**
     * Metode untuk secara berkala (dengan interval minimal) meminta hook lokasi palsu terbaru dari Service AIDL.
     * Ini dipanggil dari DALAM setiap hook callback untuk memastikan hook yang digunakan selalu yang terbaru dari Service.
     * Data dari Service AIDL sudah mencakup pergerakan, random offset, akurasi, kecepatan, dll.
     * Ditingkatkan dengan cek kesehatan Service, error handling robust, penggunaan Cache Layer, dan Thread Safety.
     * === ENHANCEMENT: Termasuk Rate Limiting dan Circuit Breaker Logic ===
     */
    private fun updateFakeLocationDataFromService() {
        val currentTime = System.currentTimeMillis()

        // === ENHANCEMENT: Circuit Breaker Check ===
        // Jika Circuit Breaker trip DAN belum melewati waktu timeout, jangan panggil AIDL.
        if (aidlFailureCount >= AIDL_FAILURE_THRESHOLD && currentTime - lastAidlFailureTime < AIDL_TIMEOUT_MS) {
            // XposedBridge.log("[$TAG] Circuit Breaker is tripped. Skipping AIDL call.") // Log ini bisa sangat berisik
            // === ENHANCEMENT: Failsafe Mechanism Logging ===
            if (latestFakeLocationData == null) {
                // Log hanya jika hook lokasi palsu lokal juga kosong (indikasi failsafe aktif)
                XposedBridge.log("[$TAG] AIDL Service unreachable (Circuit Breaker tripped). Using failsafe (null location).")
            }
            return // Keluar, jangan coba panggil AIDL
        }

        // === ENHANCEMENT: Cek Cache terlebih dahulu (Rate Limiting Layer 1) ===
        // Jika ada hook valid di cache, gunakan itu dan keluar dari metode.
        val cachedLocation = LocationCache.getCachedLocation() // Provider tidak digunakan untuk cache saat ini
        if (cachedLocation != null) {
            // Jika hook cache valid, set latestFakeLocationData dari cache
            // Perlu membuat FakeLocationData dari Location untuk konsistensi internal state hook
            // ATAU, kita bisa langsung menggunakan cachedLocation di createFakeLocation jika datanya cukup.
            // Untuk kesederhanaan saat ini, kita asumsikan cachedLocation cukup dan logika faking
            // di createFakeLocation akan menggunakan hook dari cachedLocation jika latestFakeLocationData null.
            // Alternatif yang lebih baik: Simpan FakeLocationData di cache, bukan Location.
            // Untuk saat ini, kita biarkan latestFakeLocationData null dan createFakeLocation akan cek null.
            // XposedBridge.log("[$TAG] updateFakeLocationDataFromService: Using cached location.") // Log ini bisa sangat berisik
            return // Keluar, tidak perlu panggil AIDL
        }

        // === ENHANCEMENT: Gunakan synchronized block untuk melindungi akses ke AIDL Service dan state lokal ===
        synchronized(updateLock) { // <<< Synchronize akses ke AIDL dan update state
            // === ENHANCEMENT: Cek kesehatan Service AIDL sebelum mencoba mengambil hook ===
            // Jika Service tidak sehat, reset state lokal dan keluar.
            // Cek ini juga berkontribusi pada Failsafe.
            if (!isServiceHealthy()) {
                resetLocationState() // Reset state lokal jika Service tidak sehat
                XposedBridge.log("[$TAG] updateFakeLocationDataFromService: AIDL Service not healthy. Skipping hook update.")
                // === ENHANCEMENT: Failsafe Mechanism Logging ===
                XposedBridge.log("[$TAG] AIDL Service not healthy. Using failsafe (null location).")
                return // Keluar dari metode
            }

            // === ENHANCEMENT: Cek interval minimal untuk meminta update hook dari Service (Rate Limiting Layer 2) ===
            if (currentTime - mLastUpdatedFromService < LOCATION_UPDATE_INTERVAL_HOOK_CHECK) {
                // XposedBridge.log("[$TAG] Skipping AIDL call, too soon.") // Log ini bisa sangat berisik
                return
            }

            // === ENHANCEMENT: Gunakan Thread Management untuk panggilan AIDL ===
            // Panggil Service AIDL untuk mendapatkan hook lokasi palsu TERBARU pada thread terpisah.
            // Gunakan "location_data" sebagai key untuk rate limiting.
            executeAidlCall("location_data") {
                try {
                    val fakeData: FakeLocationData? = aidlService?.getLatestFakeLocation()

                    // Update state lokal di Hook dengan hook terbaru yang didapat dari Service.
                    // Ini dilakukan di dalam synchronized block karena latestFakeLocationData diakses oleh hook callbacks.
                    synchronized(updateLock) { // <<< Synchronize update state
                        latestFakeLocationData = fakeData // @Volatile memastikan update ini terlihat
                        mLastUpdatedFromService = System.currentTimeMillis() // Update waktu update terakhir

                        if (fakeData != null) {
                            // Log verbose jika hook berhasil diperbarui dari Service
                            // XposedBridge.log("[$TAG] Data updated from Service. Started=${fakeData.isStarted}, Lat=${fakeData.latitude}, Lon=${fakeData.longitude}, Acc=${fakeData.accuracy}, Interval=${fakeData.updateIntervalMs}, Speed=${fakeData.speed}, Bearing=${fakeData.bearing}, Alt=${fakeData.altitude}") // Log ini bisa sangat berisik

                            // === ENHANCEMENT: Reset Circuit Breaker state on success ===
                            aidlFailureCount = 0
                            lastAidlFailureTime = 0L
                            // XposedBridge.log("[$TAG] AIDL call successful. Resetting Circuit Breaker.") // Log (opsional)

                            // === ENHANCEMENT: Update Cache setelah berhasil mendapatkan hook baru ===
                            // Buat objek Location dari fakeData untuk disimpan di cache.
                            // Gunakan provider default "gps" atau pertimbangkan provider dari fakeData jika ada.
                            val locationToCache = createFakeLocation(LocationManager.GPS_PROVIDER) // Buat objek Location untuk cache
                            if (locationToCache != null) {
                                LocationCache.updateCache(locationToCache) // Simpan di cache
                                // XposedBridge.log("[$TAG] updateFakeLocationDataFromService: Cache updated with new hook from Service.") // Log ini bisa sangat berisik
                            } else {
                                // Jika gagal membuat Location dari fakeData (misal faking nonaktif), bersihkan cache
                                LocationCache.clearCache()
                                XposedBridge.log("[$TAG] updateFakeLocationDataFromService: Failed to create Location from fakeData. Clearing cache.")
                            }

                        } else {
                            // Log jika Service mengembalikan null hook (misal, faking tidak aktif di aplikasi utama)
                            XposedBridge.log("[$TAG] AIDL Service returned null hook (faking not active in main app?). Clearing cache.")
                            LocationCache.clearCache() // === ENHANCEMENT: Bersihkan cache jika Service mengembalikan null ===
                            // === ENHANCEMENT: Failsafe Mechanism Logging ===
                            XposedBridge.log("[$TAG] AIDL Service returned null hook. Using failsafe (null location).")
                        }
                    } // Akhir synchronized block update state

                } catch (e: Exception) { // <<< Catch Throwable untuk menangani berbagai error (RemoteException, SecurityException, dll.)
                    // === ENHANCEMENT: Panggil helper handleError untuk memproses error ===
                    // Note: handleError di sini hanya menangani error dari panggilan AIDL di thread terpisah.
                    // Error dari hook callback di thread utama ditangani di hook callback itu sendiri.
                    handleError(e) // <<< as Throwable DIHAPUS DI SINI

                    // === ENHANCEMENT: Update Circuit Breaker state on failure ===
                    val errorTime = System.currentTimeMillis()
                    if (e is RemoteException) {
                        synchronized(updateLock) { // Sinkronisasi saat update aidlFailureCount dan lastAidlFailureTime
                            aidlFailureCount++
                            lastAidlFailureTime = errorTime
                            XposedBridge.log("[$TAG] AIDL call failed (${aidlFailureCount}/${AIDL_FAILURE_THRESHOLD}). Updating Circuit Breaker state.")
                            if (aidlFailureCount >= AIDL_FAILURE_THRESHOLD) {
                                XposedBridge.log("[$TAG] Circuit Breaker tripped. Will retry after ${AIDL_TIMEOUT_MS}ms.")
                            }
                        }
                    }

                    // === ENHANCEMENT: Failsafe Mechanism Logging ===
                    XposedBridge.log("[$TAG] Error calling AIDL Service. Using failsafe (null location).")
                }
            } // Akhir executeAidlCall
        } // Akhir synchronized block utama
    }

    /**
     * Membuat objek Location palsu dari hook terbaru yang tersimpan di latestFakeLocationData.
     * Mengambil hook lengkap dari FakeLocationData dari Service.
     * Ini dipanggil dari DALAM setiap hook callback saat kita akan menyuntikkan lokasi palsu.
     *
     * @param originalProvider Provider Location asli yang diminta (misal "gps", "network", "fused"). Digunakan untuk membuat objek Location palsu.
     * @return Objek Location palsu (dengan semua hook dari Service), atau null jika latestFakeLocationData null atau faking tidak aktif, ATAU jika VelocityChecker atau AltitudeChecker mendeteksi hook tidak realistis, ATAU jika LocationValidator gagal.
     */
    @SuppressLint("PrivateApi") // Anotasi ini menandai penggunaan API internal (setIsFromMockProvider, mIsFromMockProvider)
    private fun createFakeLocation(originalProvider: String?): Location? {
        // === Refined: Concise null checks (Input Validation for fakeData) ===
        // Menggunakan FailsafeManager jika hook dari Service tidak tersedia
        val fakeData = latestFakeLocationData
        val isFakingStarted = fakeData != null && fakeData.isStarted && aidlService != null

        val locationToProcess = if (isFakingStarted) {
            // Jika faking aktif dan hook dari Service ada, gunakan hook tersebut
            // XposedBridge.log("[$TAG] createFakeLocation: Using hook from AIDL Service.") // Log (opsional)
            Location(originalProvider ?: getNextProvider()).apply {
                latitude = fakeData.latitude + (Random.nextDouble() - 0.5) * 0.000002 // Noise ±0.000001 deg (~±0.1m)
                longitude = fakeData.longitude + (Random.nextDouble() - 0.5) * 0.000002 // Noise ±0.000001 deg (~±0.1m)
                accuracy = (fakeData.accuracy + Random.nextFloat() * 0.3f).coerceIn(3f, 15f) // Add noise and coerce between 3m and 15m
                speed = (fakeData.speed + (Random.nextFloat() - 0.5f) * 0.2f).coerceIn(0f, 30f) // Add noise and coerce between 0 m/s and 30 m/s
                bearing = fakeData.bearing // Bearing dari Service AIDL
                altitude = (fakeData.altitude + (Random.nextDouble() - 0.5) * 2).coerceIn(-500.0, 10000.0) // Add noise and coerce (example range)

                // Tambahkan Random Bearing Changes
                addBearingNoise(this)
                maskNetworkState(this)
                // Set waktu yang konsisten
                val consistentTimeNanos = getConsistentTime()
                time = consistentTimeNanos / 1_000_000
                elapsedRealtimeNanos = consistentTimeNanos

                // Tambahkan extras (termasuk hook satelit dan jaringan)
                extras = Bundle().apply {
                    putBoolean(decrypt("bm9HUFNMb2NhdGlvbg==", useKey = true), false) // "noGPSLocation"
                    putAll(SatelliteSimulator.generateSatelliteData()) // Data satelit
                    putInt(decrypt("YWdwcw==", useKey = true), 1) // "agps"
                    // TODO: Tambahkan extras lain dari FakeLocationData jika ada
                }

                // Update hook sensor (menambahkan hook medan magnet dan lainnya ke extras)
                SensorSimulator.updateSensorData(this)

                // Tambahkan ke history PatternDetector
                PatternDetector.addLocation(this)

                // Tambahkan EXTRA noise jika pola terdeteksi
                if (PatternDetector.shouldAddNoise()) {
                    latitude += (Random.nextDouble() - 0.5) * 0.00001
                    longitude += (Random.nextDouble() - 0.5) * 0.00001
                    accuracy = (accuracy + Random.nextFloat() * 1.0f).coerceIn(5f, 25f)
                    speed = (speed + (Random.nextFloat() - 0.5f) * 0.5f).coerceIn(0f, 40f)
                    altitude = (altitude + (Random.nextDouble() - 0.5) * 5).coerceIn(-500.0, 10000.0)
                    addBearingNoise(this)
                    XposedBridge.log("[$TAG] createFakeLocation: Pattern detected, added extra noise.")
                }

                mIsFromMockProviderField?.let { field ->
                    try {
                        // Set field mIsFromMockProvider (boolean) pada objek Location ini menjadi true
                        field.setBoolean(this, true)
                        // Logging opsional jika ingin konfirmasi bahwa flag berhasil disetel via Reflection
                        // XposedBridge.log("[$TAG] createFakeLocation: Successfully set mIsFromMockProvider flag via reflection.")
                    } catch (e: Exception) {
                        // Log error jika gagal menyetel field (misalnya, field tidak ditemukan secara tak terduga
                        // atau ada masalah akses, meskipun isAccessible(true) sudah dipanggil)
                        XposedBridge.log("[$TAG] createFakeLocation: Failed to set mIsFromMockProvider flag via reflection: ${e.message ?: "Unknown error"}")
                    }
                }

                // Cek Altitude Consistency
                if (!AltitudeChecker.isAltitudeRealistic(altitude)) {
                    XposedBridge.log("[$TAG] createFakeLocation: Altitude consistency check failed. Using last valid altitude.")
                    altitude = AltitudeChecker.getLastValidAltitude() ?: altitude
                    XposedBridge.log("[$TAG] Altitude consistency check failed. Using failsafe (last valid altitude: ${"%.2f".format(altitude)}).")
                }

                // Cek Time Consistency
                if (!isTimeConsistent(this)) {
                    XposedBridge.log("[$TAG] createFakeLocation: Generated fake location is time inconsistent. Returning null.")
                    XposedBridge.log("[$TAG] Time consistency check failed. Using failsafe (null location).")
                    return null // Return null jika waktu tidak konsisten
                }

                // Cek Velocity Consistency
                if (!VelocityChecker.isVelocityRealistic(this)) {
                    XposedBridge.log("[$TAG] createFakeLocation: Velocity check failed. Returning null.")
                    XposedBridge.log("[$TAG] Velocity check failed. Using failsafe (null location).")
                    return null // Return null jika kecepatan tidak realistis
                }

                // === ENHANCEMENT: Location Data Validation ===
                // Lakukan validasi hook lokasi yang dihasilkan sebelum disuntikkan
                if (!LocationValidator.validateLocation(this)) {
                    XposedBridge.log("[$TAG] createFakeLocation: Location validation failed. Returning null.")
                    XposedBridge.log("[$TAG] Location validation failed. Using failsafe (null location).")
                    return null // Return null jika validasi gagal
                }


            } // Akhir apply{} untuk location dari Service hook
        } else {
            // Jika faking tidak aktif atau hook dari Service tidak ada, coba gunakan FailsafeManager
            val failsafeLoc = FailsafeManager.getFailsafeLocation(originalProvider)
            if (failsafeLoc != null) {
                XposedBridge.log("[$TAG] createFakeLocation: Faking OFF or no hook. Using failsafe location.")
                // Jika failsafe location valid, tambahkan hook sensor dan network state ke extras
                failsafeLoc.extras = Bundle(failsafeLoc.extras).apply { // Copy extras yang sudah ada
                    putAll(MagneticFieldSimulator.generateMagneticData()) // Data medan magnet
                    putAll(NetworkStateSimulator.generateNetworkState()) // Data status jaringan
                    // TODO: Tambahkan hook sensor lain ke extras jika diperlukan dan disimulasikan
                }
                // Jangan panggil SensorSimulator.updateSensorData di sini karena kita tidak punya hook fakeLocationData untuk simulasi akselerometer yang bergantung padanya.
                // Cukup tambahkan extras yang statis/disimulasikan independen.

                // Jangan panggil PatternDetector.addLocation atau VelocityChecker.isVelocityRealistic
                // karena lokasi failsafe mungkin tidak merepresentasikan pergerakan real time.
                // Logika PatternDetector dan VelocityChecker hanya berlaku untuk hook yang datang dari Service.

                // === ENHANCEMENT: Location Data Validation (untuk failsafe juga) ===
                // Lakukan validasi hook lokasi failsafe sebelum dikembalikan
                if (!LocationValidator.validateLocation(failsafeLoc)) {
                    XposedBridge.log("[$TAG] createFakeLocation: Failsafe location validation failed. Returning null.")
                    XposedBridge.log("[$TAG] Failsafe location validation failed. Using failsafe (null location).")
                    return null // Return null jika validasi failsafe gagal
                }

                return failsafeLoc // Kembalikan lokasi dari failsafe
            } else {
                // Jika failsafe location juga tidak tersedia atau kadaluarsa
                XposedBridge.log("[$TAG] createFakeLocation: Faking OFF, no hook, and failsafe expired. Returning null.")
                return null // Kembalikan null jika tidak ada hook sama sekali
            }
        }

        // Jika lokasi berhasil dibuat (baik dari Service hook atau Failsafe), update FailsafeManager
        FailsafeManager.updateFailsafeLocation(locationToProcess)

        // === ENHANCEMENT: GMS Location Protection ===
        // Jika package pemanggil adalah GMS, proses Location extras agar terlihat lebih natural untuk GMS
        val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
        if (GmsLocationProtector.isGmsRequest(callingPackage)) {
            GmsLocationProtector.handleGmsLocation(locationToProcess)
        }


        // Log verbose objek Location palsu yang dibuat
        // XposedBridge.log("[$TAG] createFakeLocation: Created fake location: Lat=${locationToProcess.latitude}, Lon=${locationToProcess.longitude}, Acc=${locationToProcess.accuracy}, Time=${locationToProcess.time}, ElapsedNanos=${locationToProcess.elapsedRealtimeNanos}, Provider=${locationToProcess.provider ?: "unknown provider"}, Satellites=${locationToProcess.extras?.getInt(decrypt("c2F0ZWxsaXRlcw==", useKey = true))}, Bearing=${locationToProcess.bearing}, Speed=${locationToProcess.speed}, Altitude=${locationToProcess.altitude}, SNR=${locationToProcess.extras?.getFloat(decrypt("bWVhblNuUg==", useKey = true))}, AGPS=${locationToProcess.extras?.getInt(decrypt("YWdwcw==", useKey = true))}") // FIX: Handle nullable provider and extras (optional, can be noisy)
        return locationToProcess // Kembalikan objek Location palsu yang sudah lengkap datanya
    }


    // =====================================================================
    // === FUNGSI HELPER UNTUK MENCARI KELAS DENGAN AMAN ===
    // Membungkus XposedHelpers.findClass dengan try-catch untuk menangani ClassNotFoundError
    // =====================================================================

    /**
     * Mencari kelas dengan nama tertentu menggunakan ClassLoader dari package target.
     * Mengembalikan objek Class jika ditemukan, atau null jika tidak (sambil mencatat log error).
     * @param className Nama lengkap kelas (dengan package) yang dicari.
     * @param classLoader ClassLoader dari package target tempat hook berjalan.
     * @return Objek Class<?> jika ditemukan, atau null.
     */
    private fun findAndroidClassSafely(
        className: String,
        classLoader: ClassLoader
    ): Class<*>? {
        return try {
            // Coba cari kelas menggunakan XposedHelpers.findClass
            XposedHelpers.findClass(className, classLoader)
        } catch (e: ClassNotFoundError) {
            // Tangani jika kelas tidak ditemukan (ClassNotFoundException di Xposed)
            XposedBridge.log("[$TAG] Class not found: $className. ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            null // Kembalikan null jika kelas tidak ditemukan
        } catch (e: Exception) {
            // Tangani error lain yang tidak terduga saat mencari kelas
            XposedBridge.log("[$TAG] Unexpected error finding class $className: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            null // Kembalikan null untuk error lain
        }
    }


    // =====================================================================
    // === Metode Utama Hooking ===
    // Dipanggil dari HookEntry.handleLoadPackage() di package target.
    // Metode ini BERTANGGUNG JAWAB untuk melakukan hooking pada metode-metode Location API.
    // Logika *menentukan* lokasi palsu dan *kapan* menyuntikkan dilakukan di DALAM hook callback
    // dengan mengambil hook dari latestFakeLocationData yang diupdate dari Service AIDL.
    // =====================================================================
    @SuppressLint("NewApi") // Untuk penggunaan API level baru (misal getElapsedRealtimeNanos, HiddenApiBypass)
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("[$TAG] Initializing location hooks for package: ${lpparam.packageName ?: "unknown package"}.") // FIX: Ensure packageName is not null

        // === ENHANCEMENT: Inisialisasi System Property Spoofing ===
        SystemPropertySpoofer.init()

        // === ENHANCEMENT: Hook untuk menyembunyikan keberadaan Xposed dan modul ===
        // Hook ini dipasang di setiap package yang di-load oleh Xposed.
        try {
            // Cari kelas ApplicationPackageManager (nama kelas di-obfuscate dengan key)
            val packageManagerClass = findAndroidClassSafely(decrypt("YW5kcm9pZC5hcHAuQXBwbGljYXRpb25QYWNrYWdlTWFuYWdlcg==", useKey = true), lpparam.classLoader) // "android.app.ApplicationPackageManager"

            if (packageManagerClass != null) {
                // Hook metode getInstalledApplications (nama metode di-obfuscate dengan key)
                XposedHelpers.findAndHookMethod(
                    packageManagerClass,
                    decrypt("Z2V0SW5zdGFsbGVkQXBwbGljYXRpb25z", useKey = true), // "getInstalledApplications"
                    Int::class.javaPrimitiveType, // Parameter: int flags
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // Logika di DALAM hook callback, dipanggil SETELAH metode asli dieksekusi
                            // Kita memanipulasi hasil (param.result) dari metode asli

                            // Ambil hasil asli (daftar ApplicationInfo)
                            @Suppress("UNCHECKED_CAST") // Mengabaikan peringatan tipe casting
                            val applications = param.result as? List<Any> ?: return // Pastikan hasilnya List<*> dan bukan null

                            // Filter daftar: Hapus objek ApplicationInfo yang package name-nya mengandung pola Xposed atau package ID modul kita
                            val filteredApplications = applications.filterNot { appInfo ->
                                // Dapatkan package name dari objek ApplicationInfo menggunakan Reflection/XposedHelpers (nama field di-obfuscate dengan key)
                                val packageName = XposedHelpers.getObjectField(appInfo, decrypt("cGFja2FnZU5hbWU=", useKey = true)) as? String // "packageName"

                                // === Logika filter yang lebih kuat untuk Xposed frameworks ===
                                // Cek apakah package name cocok dengan pola umum atau nama spesifik (string di-obfuscate dengan key)
                                val isXposedRelated = packageName != null && (
                                        packageName.contains(decrypt("eHBvc2Vk", useKey = true), true) || // "xposed"
                                                packageName.contains(decrypt("bHNwb3NlZA==", useKey = true), true) || // "lsposed"
                                                packageName.contains(decrypt("ZWR4cG9zZWQ=", useKey = true), true) || // "edxposed"
                                                packageName == decrypt("ZGUucm9ivi5hbmRyb2lkLngwbG9jY2VkLmluc3RhbGxlcg==", useKey = true) || // "de.robv.android.xposed.installer"
                                                packageName == decrypt("b3JnLmxzcG9zZWQubWFuYWdlcg==", useKey = true) || // "org.lsposed.manager"
                                                packageName == decrypt("b3JnLmVkeHBvc2VkLm1hbmFnZXI=", useKey = true) // "org.edxposed.manager"
                                        // TODO: Add other Xposed/LSPosed/EdXposed package patterns/names if needed (obfuscated with key)
                                        )

                                // Check if the package name is our own module's package ID (obfuscated without key)
                                val isOurModule = packageName == decrypt("Y29tLnJveGdwcw==") // "com.roxgps" // Replace with BuildConfig.APPLICATION_ID if available here

                                // Filter out if it's Xposed-related OR our own module
                                isXposedRelated || isOurModule
                            }

                            // Replace the original method result with the filtered list
                            param.result = filteredApplications
                            // XposedBridge.log("[$TAG] Hooked getInstalledApplications(). Filtered out Xposed/Module packages.") // This log can be very noisy
                        }
                    }
                )
                XposedBridge.log("[$TAG] Successfully hooked getInstalledApplications() for anti-detection.")
            } else {
                XposedBridge.log("[$TAG] Hook getInstalledApplications: Class ApplicationPackageManager not found. Skipping anti-detection hook.")
            }
        } catch (e: NoSuchMethodError) {
            // Log if the getInstalledApplications method is not found (signature different?)
            XposedBridge.log("[$TAG] Hook getInstalledApplications: Method not found: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
        } catch (e: Exception) {
            // Log other unexpected errors during this hook initialization
            XposedBridge.log("[$TAG] Hook getInstalledApplications: Unexpected error during initialization: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
        }
        // === End ENHANCEMENT Hook hiding module presence ===


        // If you want to ignore ALL Location API hooking for a specific package (more efficient),
        // you could do the ignorePkg check here at the beginning of the initHooks method.
        // But since our HookEntry currently only calls initHooks for com.roxgps,
        // the check inside the hook callbacks is more relevant for ignoring calls from *other* packages
        // that might be active in the com.roxgps process (e.g., system services or GMS).


        // =====================================================================
        // === Hooking Application-Level Location APIs (android.location.LocationManager, android.location.Location) ===
        // These are the hooks that run in the target application process (com.roxgps in the case of the current HookEntry).
        // =====================================================================

        // === Hook LocationManager.getLastKnownLocation(String provider) ===
        // Class name is obfuscated with key
        val locationManagerClass = findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true), lpparam.classLoader) // "android.location.LocationManager"

        if (locationManagerClass != null) {
            try {
                // Hook the getLastKnownLocation method (method name is obfuscated with key)
                XposedHelpers.findAndHookMethod(
                    locationManagerClass, // Use the found LocationManager Class object
                    decrypt("Z2V0TGFzdEtub3duTG9jYXRpb24=", useKey = true), // "getLastKnownLocation"
                    String::class.java, // Method parameter: String provider
                    object : XC_MethodHook() { // Hook callback implementation
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // === Logic INSIDE the hook callback, called BEFORE the original method is executed ===

                            // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                            addRandomDelay()

                            // Get the package name calling this location API.
                            val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                            val methodName = decrypt("Z2V0TGFzdEtub3duTG9jYXRpb24=", useKey = true) // "getLastKnownLocation"
                            val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true) // "android.location.LocationManager"


                            // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                            // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
                            if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                // XposedBridge.log("[$TAG] Hooked getLastKnownLocation() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                                if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                    param.result = null // Set result to null if blocked by Interceptor
                                }
                                return // EXIT from beforeHookedMethod. Fake location is NOT injected.
                            }

                            // === ENHANCEMENT: Stack Trace Check ===
                            makeHookingSafer(param)
                            if (param.result == null) { // If makeHookingSafer set result to null
                                // XposedBridge.log("[$TAG] Hooked getLastKnownLocation() -> Stack trace check failed. Returning null.") // Log (optional)
                                return // Exit the hook
                            }

                            // === ENHANCEMENT: Passive Provider Protection ===
                            // If the provider is "passive", block the request
                            if (PassiveProviderBlocker.blockPassiveProvider(param)) {
                                // If blocked, PassiveProviderBlocker has already set param.result = null
                                return // Exit the hook
                            }


                            // If the package is NOT a system app, NOT ignored, AND detection is NOT triggered, continue faking logic:
                            // IMPORTANT: Periodically update the latest fake location hook from the AIDL Service
                            updateFakeLocationDataFromService()

                            // Create a fake Location object from the latest hook (or failsafe)
                            val provider = param.args.getOrNull(0) as? String
                            // === ENHANCEMENT: Input Validation for provider string ===
                            if (provider == null || provider.isBlank()) {
                                XposedBridge.log("[$TAG] Hooked getLastKnownLocation() [${callingPackage}] -> Invalid provider string: '${provider ?: "null"}'. Allowing original.") // FIX: Handle nullable provider
                                return // Let the original method run if the provider is invalid
                            }

                            val fakeLocation = createFakeLocation(provider)

                            // If createFakeLocation returns a Location object (meaning faking is active and hook is available, OR failsafe is valid),
                            // inject this fake location by replacing the original method's result.
                            if (fakeLocation != null) {
                                param.result = fakeLocation // <<< THIS REPLACES THE RESULT

                                // Log that we successfully injected the fake location
                                // XposedBridge.log("[$TAG] Hooked getLastKnownLocation($provider) [${callingPackage}] -> Injecting fake. Lat=${fakeLocation.latitude}, Lon=${fakeLocation.longitude}, Acc=${fakeLocation.accuracy}") // This log can be very noisy
                            } else {
                                // If faking is not active, no fake hook from Service, AND failsafe is also not available/expired
                                // XposedBridge.log("[$TAG] Hooked getLastKnownLocation($provider) [${callingPackage}] -> Faking OFF, no hook, and failsafe expired. Allowing original.")
                                // Option: Explicitly set param.result = null if you want to hide the real location when faking is off
                                // param.result = null
                            }
                        } // End of beforeHookedMethod
                    } // End of XC_MethodHook object
                ) // End of XposedHelpers.findAndHookMethod
                XposedBridge.log("[$TAG] Successfully hooked getLastKnownLocation().")
            } catch (e: NoSuchMethodError) {
                XposedBridge.log("[$TAG] Hook getLastKnownLocation: Method not found: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Hook getLastKnownLocation: Unexpected error: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            }

            // === ENHANCEMENT: Hook LocationManager.getNetworkProvider ===
            // Return null to prevent applications from getting the Network Location Provider.
            // Method name is obfuscated with key
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerClass,
                    decrypt("Z2V0TmV0d29ya1Byb3ZpZGVy", useKey = true), // "getNetworkProvider"
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                            addRandomDelay()

                            val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                            val methodName = decrypt("Z2V0TmV0d29ya1Byb3ZpZGVy", useKey = true) // "getNetworkProvider"
                            val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true) // "android.location.LocationManager"


                            // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                            // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
                            if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                // XposedBridge.log("[$TAG] Hooked getNetworkProvider() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                                if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                    param.result = null // Set result to null if blocked by Interceptor
                                }
                                return // EXIT from beforeHookedMethod.
                            }

                            // === ENHANCEMENT: Stack Trace Check ===
                            makeHookingSafer(param)
                            if (param.result == null) { // If makeHookingSafer set result to null
                                // XposedBridge.log("[$TAG] Hooked getNetworkProvider() -> Stack trace check failed. Returning null.") // Log (optional)
                                return // Exit the hook
                            }

                            // If the package is NOT a system app, NOT ignored, AND detection is NOT triggered, continue faking logic:
                            // Replace the result with null
                            param.result = null // Prevent applications from getting the Network Location Provider
                            // XposedBridge.log("[$TAG] Hooked getNetworkProvider() [${callingPackage}] -> Returning null.") // This log can be very noisy
                        }
                    }
                )
                XposedBridge.log("[$TAG] Successfully hooked getNetworkProvider() for anti-detection.")
            } catch (e: NoSuchMethodError) {
                XposedBridge.log("[$TAG] Hook getNetworkProvider: Method not found: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Hook getNetworkProvider: Unexpected error during initialization: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            }

            // === ENHANCEMENT: Hook LocationManager.isProviderEnabled(String provider) ===
            // Method name is obfuscated with key
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerClass,
                    decrypt("aXNQcm92aWRlckVuYWJsZWQ=", useKey = true), // "isProviderEnabled"
                    String::class.java, // Parameter: String provider
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                            addRandomDelay()

                            val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                            val methodName = decrypt("aXNQcm92aWRlckVuYWJsZWQ=", useKey = true) // "isProviderEnabled"
                            val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true) // "android.location.LocationManager"

                            // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                            // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
                            if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                // XposedBridge.log("[$TAG] Hooked isProviderEnabled() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                                // No need to set result to null if blocked by Interceptor here, let the original method return default value
                                return // EXIT from beforeHookedMethod.
                            }

                            // === ENHANCEMENT: Stack Trace Check ===
                            makeHookingSafer(param)
                            if (param.result == null) { // If makeHookingSafer set result to null
                                // XposedBridge.log("[$TAG] Hooked isProviderEnabled() -> Stack trace check failed. Returning null.") // Log (optional)
                                return // Exit the hook
                            }

                            // If the package is NOT a system app, NOT ignored, AND detection is NOT triggered, continue faking logic:
                            // Use ProviderProtector to simulate the enabled status
                            val provider = param.args.getOrNull(0) as? String
                            // === ENHANCEMENT: Input Validation for provider string ===
                            if (provider == null || provider.isBlank()) {
                                XposedBridge.log("[$TAG] Hooked isProviderEnabled() [${callingPackage}] -> Invalid provider string: '${provider ?: "null"}'. Allowing original.") // FIX: Handle nullable provider
                                return // Let the original method run if the provider is invalid
                            }

                            param.result = ProviderProtector.isProviderEnabled(provider) // <<< Replace the result with the simulated status
                            // XposedBridge.log("[$TAG] Hooked isProviderEnabled(${provider ?: "unknown provider"}) [${callingPackage}] -> Injecting fake enabled=${param.result}") // FIX: Handle nullable provider (optional)
                        }
                    }
                )
                XposedBridge.log("[$TAG] Successfully hooked isProviderEnabled().")
            } catch (e: NoSuchMethodError) {
                XposedBridge.log("[$TAG] Hook isProviderEnabled: Method not found: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Hook isProviderEnabled: Unexpected error: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            }

            // === ENHANCEMENT: Hook LocationManager.getProviderProperties(String provider) ===
            // Method name is obfuscated with key
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerClass,
                    decrypt("Z2V0UHJvdmlkZXJQcm9wZXJ0aWVz", useKey = true), // "getProviderProperties"
                    String::class.java, // Parameter: String provider
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                            addRandomDelay()

                            val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                            val methodName = decrypt("Z2V0UHJvdmlkZXJQcm9wZXJ0aWVz", useKey = true) // "getProviderProperties"
                            val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true) // "android.location.LocationManager"

                            // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                            // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
                            if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                // XposedBridge.log("[$TAG] Hooked getProviderProperties() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                                // No need to set result to null if blocked by Interceptor here
                                return // EXIT from beforeHookedMethod.
                            }

                            // === ENHANCEMENT: Stack Trace Check ===
                            makeHookingSafer(param)
                            if (param.result == null) { // If makeHookingSafer set result to null
                                // XposedBridge.log("[$TAG] Hooked getProviderProperties() -> Stack trace check failed. Returning null.") // Log (optional)
                                return // Exit the hook
                            }

                            // If the package is NOT a system app, NOT ignored, AND detection is NOT triggered, continue faking logic:
                            // Use ProviderProtector to simulate the provider properties
                            val provider = param.args.getOrNull(0) as? String
                            // === ENHANCEMENT: Input Validation for provider string ===
                            if (provider == null || provider.isBlank()) {
                                XposedBridge.log("[$TAG] Hooked getProviderProperties() [${callingPackage}] -> Invalid provider string: '${provider ?: "null"}'. Allowing original.") // FIX: Handle nullable provider
                                return // Let the original method run if the provider is invalid
                            }

                            param.result = ProviderProtector.getProviderProperties(provider) // <<< Replace the result with the simulated properties Bundle
                            // XposedBridge.log("[$TAG] Hooked getProviderProperties(${provider ?: "unknown provider"}) [${callingPackage}] -> Injecting fake properties.") // FIX: Handle nullable provider (optional)
                        }
                    }
                )
                XposedBridge.log("[$TAG] Successfully hooked getProviderProperties().")
            } catch (e: NoSuchMethodError) {
                XposedBridge.log("[$TAG] Hook getProviderProperties: Method not found: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Hook getProviderProperties: Unexpected error during initialization: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            }
        }


        // === Hook Location.getLatitude(), getLongitude(), getAccuracy(), etc. Methods ===
        // Class name is obfuscated with key
        val locationClass = findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true), lpparam.classLoader) // "android.location.Location"

        if (locationClass != null) {
            try {
                // Hook Location.getLatitude() (method name is obfuscated with key)
                XposedHelpers.findAndHookMethod(locationClass, decrypt("Z2V0TGF0aXR1ZGU=", useKey = true), object : XC_MethodHook() { // "getLatitude"
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                        addRandomDelay()

                        val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                        val methodName = decrypt("Z2V0TGF0aXR1ZGU=", useKey = true) // "getLatitude"
                        val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true) // "android.location.Location"

                        // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                        // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
                        if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                            // XposedBridge.log("[$TAG] Hooked Location.getLatitude() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                            if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                param.result = 0.0 // Set result to a default/safe value if blocked
                            }
                            return // EXIT from the callback
                        }

                        // === ENHANCEMENT: Stack Trace Check ===
                        makeHookingSafer(param)
                        if (param.result == null) { // If makeHookingSafer set result to null
                            // XposedBridge.log("[$TAG] Hooked Location.getLatitude() -> Stack trace check failed. Returning null.") // Log (optional)
                            param.result = 0.0 // Set result to a default/safe value
                            return // Exit the hook
                        }

                        // Faking Logic: Update hook from Service, check if faking is active, replace result with fake hook
                        updateFakeLocationDataFromService()
                        val fakeData = latestFakeLocationData

                        if (fakeData != null && fakeData.isStarted && aidlService != null) {
                            param.result = fakeData.latitude
                            // XposedBridge.log("[$TAG] Hooked Location.getLatitude() [${callingPackage}] -> Injecting fake Lat=${fakeData.latitude}")
                        } else {
                            // === ENHANCEMENT: Failsafe Mechanism Logging ===
                            // Log if faking is not active or hook is null when this hook is called
                            XposedBridge.log("[$TAG] Hooked Location.getLatitude() [${callingPackage}] -> Faking OFF or no hook. Allowing original.")
                        }
                    }
                })
                XposedBridge.log("[$TAG] Successfully hooked Location.getLatitude().")

                // Hook Location.getLongitude() - Apply the same pattern: check ignorePkg, update hook, check faking, replace result (method name is obfuscated with key)
                XposedHelpers.findAndHookMethod(locationClass, decrypt("Z2V0TG9uZ2l0dWRl", useKey = true), object : XC_MethodHook() { // "getLongitude"
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                        addRandomDelay()

                        val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                        val methodName = decrypt("Z2V0TG9uZ2l0dWRl", useKey = true) // "getLongitude"
                        val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true) // "android.location.Location"

                        // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                        if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                            // XposedBridge.log("[$TAG] Hooked Location.getLongitude() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                            if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                param.result = 0.0 // Set result to a default/safe value if blocked
                            }
                            return // === Check ignorePkg ===
                        }

                        // === ENHANCEMENT: Stack Trace Check ===
                        makeHookingSafer(param)
                        if (param.result == null) { // If makeHookingSafer set result to null
                            // XposedBridge.log("[$TAG] Hooked Location.getLongitude() -> Stack trace check failed. Returning null.") // Log (optional)
                            param.result = 0.0 // Set result to a default/safe value
                            return // Exit the hook
                        }

                        updateFakeLocationDataFromService()
                        val fakeData = latestFakeLocationData

                        if (fakeData != null && fakeData.isStarted && aidlService != null) {
                            param.result = fakeData.longitude
                            // XposedBridge.log("[$TAG] Hooked Location.getLongitude() [${callingPackage}] -> Injecting fake Lon=${fakeData.longitude}")
                        } else {
                            // === ENHANCEMENT: Failsafe Mechanism Logging ===
                            XposedBridge.log("[$TAG] Hooked Location.getLongitude() [${callingPackage}] -> Faking OFF or no hook. Allowing original.")
                        }
                    }
                })
                XposedBridge.log("[$TAG] Successfully hooked Location.getLongitude().")

                // Hook Location.getAccuracy() - Apply the same pattern: check ignorePkg, update hook, check faking, replace result (method name is obfuscated with key)
                XposedHelpers.findAndHookMethod(locationClass, decrypt("Z2V0QWNjdXJhY3k=", useKey = true), object : XC_MethodHook() { // "getAccuracy"
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                        addRandomDelay()

                        val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                        val methodName = decrypt("Z2V0QWNjdXJhY3k=", useKey = true) // "getAccuracy"
                        val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true) // "android.location.Location"

                        // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                        if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                            // XposedBridge.log("[$TAG] Hooked Location.getAccuracy() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                            if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                param.result = 0.0f // Set result to a default/safe value if blocked
                            }
                            return // === Check ignorePkg ===
                        }

                        // === ENHANCEMENT: Stack Trace Check ===
                        makeHookingSafer(param)
                        if (param.result == null) { // If makeHookingSafer set result to null
                            // XposedBridge.log("[$TAG] Hooked Location.getAccuracy() -> Stack trace check failed. Returning null.") // Log (optional)
                            param.result = 0.0f // Set result to a default/safe value
                            return // Exit the hook
                        }

                        updateFakeLocationDataFromService()
                        val fakeData = latestFakeLocationData

                        if (fakeData != null && fakeData.isStarted && aidlService != null) {
                            param.result = fakeData.accuracy
                            // XposedBridge.log("[$TAG] Hooked Location.getAccuracy() [${callingPackage}] -> Injecting fake Acc=${fakeData.accuracy}")
                        } else {
                            // === ENHANCEMENT: Failsafe Mechanism Logging ===
                            XposedBridge.log("[$TAG] Hooked Location.getAccuracy() [${callingPackage}] -> Faking OFF or no hook. Allowing original.")
                        }
                    }
                })
                XposedBridge.log("[$TAG] Successfully hooked Location.getAccuracy().")

                // TODO: Hook other Location.get... methods (getSpeed, getBearing, getAltitude, getTime, getElapsedRealtimeNanos, getElapsedRealtimeUtc)
                //       with the SAME pattern: findAndHookMethod, create XC_MethodHook, in beforeHookedMethod
                //       1. Get callingPackage.
                //       2. Get methodName and className (obfuscated with key).
                //       3. CHECK isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className) -> return if true (set result to default/safe value if blocked by Interceptor).
                //       4. Call makeHookingSafer(param); if (param.result == null) return (set result to default/safe value).
                //       5. Call updateFakeLocationDataFromService().
                //       6. Get fakeData = latestFakeLocationData.
                //       7. Check fakeData != null && fakeData.isStarted && aidlService != null.
                //       8. If true, replace param.result with fakeData.<appropriate_property>.
                //       9. If false, add Failsafe Mechanism Logging.
                //       REMEMBER: Obfuscate method names and extra key strings if needed (use useKey = true)!

                /*
                // Example hook for getSpeed() with ignorePkg check (method name is obfuscated with key)
                try {
                     XposedHelpers.findAndHookMethod(locationClass, decrypt("Z2V0U3BlZWQ=", useKey = true), object : XC_MethodHook() { // "getSpeed"
                         override fun beforeHookedMethod(param: MethodHookParam) {
                              // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                              addRandomDelay()

                              val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                              val methodName = decrypt("Z2V0U3BlZWQ=", useKey = true) // "getSpeed"
                              val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true) // "android.location.Location"

                              // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                              if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                   // XposedBridge.log("[$TAG] Hooked Location.getSpeed() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                                   if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                       param.result = 0.0f // Set result to a default/safe value if blocked
                                   }
                                   return // === Check ignorePkg ===
                              }

                             // === ENHANCEMENT: Stack Trace Check ===
                             makeHookingSafer(param)
                             if (param.result == null) { // If makeHookingSafer set result to null
                                 // XposedBridge.log("[$TAG] Hooked Location.getSpeed() -> Stack trace check failed. Returning null.") // Log (optional)
                                 param.result = 0.0f // Set result to a default/safe value
                                 return // Exit the hook
                             }

                              updateFakeLocationDataFromService()
                              val fakeData = latestFakeLocationData
                              if (fakeData != null && fakeData.isStarted && aidlService != null) {
                                  param.result = fakeData.speed // Get speed from AIDL Service
                                  // XposedBridge.log("[$TAG] Hooked Location.getSpeed() [${callingPackage}] -> Injecting fake Speed=${fakeData.speed}")
                              } else {
                                   // === ENHANCEMENT: Failsafe Mechanism Logging ===
                                   XposedBridge.log("[$TAG] Hooked Location.getSpeed() [${callingPackage}] -> Faking OFF or no hook. Allowing original.")
                              }
                         }
                     })
                      XposedBridge.log("[$TAG] Successfully hooked Location.getSpeed().")
                } catch (e: Exception) { XposedBridge.log("[$TAG] Failed to hook Location.getSpeed(): ${e.message ?: "Unknown error"}") } // FIX: Handle nullable message
                */


                // TODO: Hook Location.set(Location other) (method name is obfuscated with key)
                //       SAME pattern: findAndHookMethod, create XC_MethodHook, in beforeHookedMethod
                //       1. Get callingPackage.
                //       2. Get methodName and className (obfuscated with key).
                //       3. CHECK isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className) -> return if true (no need to set result as it's void).
                //       4. Call makeHookingSafer(param); if (param.result == null) return.
                //       5. Call updateFakeLocationDataFromService().
                //       6. Get fakeData = latestFakeLocationData.
                //       7. Check fakeData != null && fakeData.isStarted && aidlService != null.
                //       8. If true, create a NEW fake Location object from fakeData (call createFakeLocation(originalOtherLocation.provider)).
                //       9. Replace the FIRST parameter of the set method (index 0) with that NEW fake Location object: param.args[0] = fakeLocationBaru.
                //       10. If false, add Failsafe Mechanism Logging.

                /*
                // Example hook for Location.set(Location) with ignorePkg check (method name is obfuscated with key)
                try {
                      val locationClassForSet = findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true), lpparam.classLoader) // "android.location.Location"
                      if (locationClassForSet != null) {
                          XposedHelpers.findAndHookMethod(
                              locationClassForSet, // Location Class
                              decrypt("c2V0", useKey = true), // "set"
                              Location::class.java, // First parameter: Location other
                              object : XC_MethodHook() {
                                  override fun beforeHookedMethod(param: MethodHookParam) {
                                      // === ENHANCEMENT: Add random delay at the start of each hook callback ===
                                      addRandomDelay()

                                      val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
                                      val methodName = decrypt("c2V0", useKey = true) // "set"
                                      val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbg==", useKey = true) // "android.location.Location"

                                      // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
                                      if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
                                           // XposedBridge.log("[$TAG] Hooked Location.set(Location) -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
                                           // No need to set result as it's void
                                           return // === Check ignorePkg ===
                                      }

                                     // === ENHANCEMENT: Stack Trace Check ===
                                     makeHookingSafer(param)
                                     if (param.result == null) { // If makeHookingSafer set result to null
                                         // XposedBridge.log("[$TAG] Hooked Location.set(Location) -> Stack trace check failed. Returning null.") // Log (optional)
                                         return // Exit the hook
                                     }

                                      updateFakeLocationDataFromService()
                                      val fakeData = latestFakeLocationData
                                      val originalOtherLocation = param.args.getOrNull(0) as? Location // Get the original Location object to be copied

                                      if (fakeData != null && fakeData.isStarted && aidlService != null && originalOtherLocation != null) {
                                          // === ENHANCEMENT: Input Validation for originalOtherLocation parameter ===
                                           if (originalOtherLocation.provider == null || originalOtherLocation.provider.isBlank()) {
                                                XposedBridge.log("[$TAG] Hooked Location.set(Location) [${callingPackage}] -> Invalid original Location parameter provider: '${originalOtherLocation.provider ?: "null"}'. Allowing original.") // FIX: Handle nullable provider
                                                return // Let the original method run if the Location parameter is invalid
                                           }

                                          val fakeLocation = createFakeLocation(originalOtherLocation.provider)

                                          if (fakeLocation != null) {
                                               param.args[0] = fakeLocation // <<< Replacing the parameter of the 'set' method
                                               // XposedBridge.log("[$TAG] Hooked Location.set(Location) [${callingPackage}] -> Replacing parameter with fake location.")
                                          } else {
                                               // === ENHANCEMENT: Failsafe Mechanism Logging ===
                                               XposedBridge.log("[$TAG] Hooked Location.set(Location) [${callingPackage}] -> Faking ON but failed to create fake location for set. Allowing original parameter.")
                                          }
                                      } else {
                                           // === ENHANCEMENT: Failsafe Mechanism Logging ===
                                           XposedBridge.log("[$TAG] Hooked Location.set(Location) [${callingPackage}] -> Faking OFF or no hook. Allowing original parameter.")
                                      }
                                  }
                              }
                          )
                          XposedBridge.log("[$TAG] Successfully hooked Location.set(Location).")
                      }
                 } catch (e: Exception) { XposedBridge.log("[$TAG] Failed to hook Location.set(Location): ${e.message ?: "Unknown error"}") } // FIX: Handle nullable message
                */


            } catch (e: NoSuchMethodError) {
                XposedBridge.log("[$TAG] Hook Location: Could not find expected methods in Location class for hooking: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            } catch (e: Exception) {
                XposedBridge.log("[$TAG] Hook Location: Unexpected error hooking Location class methods: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
            }
        }


        // =====================================================================
        // === Hooking LocationManager.requestLocationUpdates and requestSingleUpdate ===
        // These are important for applications that actively request location updates.
        // The implementation of their callbacks is complex. Make sure to add isSystemApp and ignorePkg checks in beforeHookedMethod.
        // REMEMBER: Obfuscate method names and provider strings (use useKey = true)!
        // =====================================================================
        /*
        val locationManagerClassForRequest = findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true), lpparam.classLoader) // "android.location.LocationManager"
        if (locationManagerClassForRequest != null) {

             // Example hook for requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener) (method name is obfuscated with key)
             // Requires importing LocationListener!
             // try {
             //     val locationListenerClass = findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbkxpc3RlbmVy", useKey = true), lpparam.classLoader) // "android.location.LocationListener"
             //     if (locationListenerClass != null) {
             //         XposedHelpers.findAndHookMethod(
             //             locationManagerClassForRequest,
             //             decrypt("cmVxdWVzdExvY2F0aW9uVXBkYXRlcw==", useKey = true), // "requestLocationUpdates"
             //             String::class.java, // provider
             //             JLong::class.javaPrimitiveType, // Use JLong for java.lang.Long.class
             //             JFloat::class.javaPrimitiveType, // Use JFloat for java.lang.Float.class
             //             locationListenerClass, // listener
             //             object : XC_MethodHook() {
             //                 override fun beforeHookedMethod(param: MethodHookParam) {
             //                     // === ENHANCEMENT: Add random delay at the start of each hook callback ===
             //                     addRandomDelay()

             //                     val callingPackage = AndroidAppHelper.currentPackageName() ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
             //                     val methodName = decrypt("cmVxdWVzdExvY2F0aW9uVXBkYXRlcw==", useKey = true) // "requestLocationUpdates"
             //                     val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true) // "android.location.LocationManager"

             //                     // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
             //                     // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
             //                     if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
             //                          // XposedBridge.log("[$TAG] Hooked requestLocationUpdates() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
             //                          // No need to set result as it's void
             //                          return // === Check ignorePkg ===
             //                     }

             //                    // === ENHANCEMENT: Stack Trace Check ===
             //                    makeHookingSafer(param)
             //                    if (param.result == null) { // If makeHookingSafer set result to null
             //                        // XposedBridge.log("[$TAG] Hooked requestLocationUpdates() -> Stack trace check failed. Returning null.") // Log (optional)
             //                        return // Exit the hook
             //                    }

             //                     // Faking Logic is more complex here (replacing the listener or broadcast)
             //                      XposedBridge.log("[$TAG] Hooked requestLocationUpdates(String, long, float, LocationListener) [${callingPackage}].")
             //                      // updateFakeLocationDataFromService() // Call update if needed
             //                 }
             //             }
             //         )
             //         XposedBridge.log("[$TAG] Successfully hooked requestLocationUpdates (String, long, float, LocationListener).")
             //     }
             // } catch (e: Exception) { XposedBridge.log("[$TAG] Failed to hook requestLocationUpdates (String, long, float, LocationListener): ${e.message ?: "Unknown error"}") } // FIX: Handle nullable message

             // Example hook for requestLocationUpdates with PendingIntent (method name is obfuscated with key)
             // Requires importing PendingIntent!
             // try {
             //      val pendingIntentClass = findAndroidClassSafely(decrypt("YW5kcm9pZC5hcHAuUGVuZGluZ0ludGVudA==", useKey = true), lpparam.classLoader) // "android.app.PendingIntent"
             //      if (pendingIntentClass != null) {
             //           XposedHelpers.findAndHookMethod(
             //                locationManagerClassForRequest,
             //                decrypt("cmVxdWVzdExvY2F0aW9uVXBkYXRlcw==", useKey = true), // "requestLocationUpdates"
             //                String::class.java,
             //                JLong::class.javaPrimitiveType,
             //                JFloat::class.javaPrimitiveType,
             //                pendingIntentClass,
             //                object : XC_MethodHook() {
             //                    override fun beforeHookedMethod(param: MethodHookParam) {
             //                       // === ENHANCEMENT: Add random delay at the start of each hook callback ===
             //                       addRandomDelay()

             //                       val callingPackage = AndroidAppHelper.currentApplication().packageName ?: decrypt("dW5rbm93bl9jYWxsZXI=") // "unknown_caller"
             //                       val methodName = decrypt("cmVxdWVzdExvY2F0aW9uVXBkYXRlcw==", useKey = true) // "requestLocationUpdates"
             //                       val className = decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvbk1hbmFnZXI=", useKey = true) // "android.location.LocationManager"

             //                       // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
             //                       if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
             //                            // XposedBridge.log("[$TAG] Hooked requestLocationUpdates() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
             //                            // No need to set result as it's void
             //                            return // === Check ignorePkg ===
             //                       }
             //                       // === ENHANCEMENT: Stack Trace Check ===
             //                       makeHookingSafer(param)
             //                       if (param.result == null) { // If makeHookingSafer set result to null
             //                           // XposedBridge.log("[$TAG] Hooked requestLocationUpdates() -> Stack trace check failed. Returning null.") // Log (optional)
             //                           return // Exit the hook
             //                       }
             //                        // Faking Logic is also complex here
             //                        XposedBridge.log("[$TAG] Hooked requestLocationUpdates(String, long, float, PendingIntent) [${callingPackage}].")
             //                    }
             //                }
             //           )
             //           XposedBridge.log("[$TAG] Successfully hooked requestLocationUpdates (String, long, float, PendingIntent).")
             //      }
             // } catch (e: Exception) { XposedBridge.log("[$TAG] Failed to hook requestLocationUpdates (String, long, float, PendingIntent): ${e.message ?: "Unknown error"}") } // FIX: Handle nullable message

             // TODO: Hook removeUpdates methods (important for cleaning up listeners/PendingIntents if you're replacing updates) (method name is obfuscated with key)
             // TODO: Hook getCurrentLocation method (modern API) (method name is obfuscated with key)
             // TODO: Hook requestSingleUpdate method (method name is obfuscated with key)
        }
        */


        // =====================================================================
        // === Hooking System-Level Location APIs (com.android.server.location.LocationManagerService, etc.) ===
        // If your HookEntry hooks the "android" process (not just your main app), then the hooks below will run.
        // These are hooks that run in the Android system process.
        // These methods often receive/return Location objects.
        // The logic inside the hook callbacks must also call updateFakeLocationDataFromService(), createFakeLocation(), and check fakeData?.isStarted == true.
        // BE CAREFUL WHEN HOOKING SYSTEM PROCESSES, MISTAKES CAN CAUSE BOOTLOOPS!
        // REMEMBER: Obfuscate class names, method names, and parameter/field strings (use useKey = true)!
        // =====================================================================
        /*
        // Example Hook in the "android" process (if your HookEntry is set to hook "android")
        // FIRST check if this is the "android" system process in HookEntry!
        // if (lpparam.packageName == decrypt("YW5kcm9pZA==", useKey = true)) { // "android" ... (the entire system hook block goes here) ... }

             // Find the LocationManagerService class (class name and package might differ between Android versions!) (class name is obfuscated with key)
             // Use findAndroidClassSafely. You need to adjust the class name based on Build.VERSION.SDK_INT
             // val locationManagerServiceClass = findAndroidClassSafely(decrypt("Y29tLmFuZHJvaWQuc2VydmVyLmxvY2F0aW9uLkxvY2F0aW9uTWFuYWdlclNlcnZpY2U=", useKey = true), lpparam.classLoader) // API >= 33 "com.android.server.location.LocationManagerService"
             // val locationManagerServiceClass = findAndroidClassSafely(decrypt("Y29tLmFuZHJvaWQuc2VydmVyLkxvY2F0aW9uTWFuYWdlclNlcnZpY2U=", useKey = true), lpparam.classLoader) // API < 33 "com.android.server.LocationManagerService"

             // if (locationManagerServiceClass != null) {
             //     try {
             //          // Example Hook for LocationManagerService.getLastLocation (method name is obfuscated with key)
             //          // You need to adjust the parameters of the getLastLocation method EXACTLY according to the Android version here!
             //          // Use findAndroidClassSafely for complex parameter types (e.g., LocationRequest) (class name is obfuscated with key)
             //          XposedHelpers.findAndHookMethod(
             //              locationManagerServiceClass,
             //              decrypt("Z2V0TGFzdEtub3duTG9jYXRpb24=", useKey = true), // "getLastLocation"
             //              // Adjust parameters EXACTLY according to the Android version! Example:
             //              // String::class.java, String::class.java, findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvblJlcXVlc3Q=", useKey = true), lpparam.classLoader), String::class.java, // API >= 33 "android.location.LocationRequest"
             //              // findAndroidClassSafely(decrypt("YW5kcm9pZC5sb2NhdGlvbi5Mb2NhdGlvblJlcXVlc3Q=", useKey = true), lpparam.classLoader), String::class.java, // API < 33 "android.location.LocationRequest"

             //              object : XC_MethodHook() {
             //                  override fun beforeHookedMethod(param: MethodHookParam) {
             //                      // === ENHANCEMENT: Add random delay at the start of each hook callback ===
             //                      addRandomDelay()

             //                      // Here you need a reliable way to get the calling package for this hook from parameters or stacktrace
             //                      // var callingPackage = getCallingPackageFromParams(param) // You need to implement this helper
             //                      val callingPackage = decrypt("dW5rbm93bl9jYWxsZXJfc3lzdGVtX2hvb2s=") // "unknown_caller_system_hook" // Placeholder, you MUST implement a way to get the correct calling package in system hooks!

             //                      val methodName = decrypt("Z2V0TGFzdEtub3duTG9jYXRpb24=", useKey = true) // "getLastLocation"
             //                      // You need a reliable way to get the calling class name in system hooks!
             //                      val className = decrypt("dW5rbm93bl9jbGFzc19zeXN0ZW1faG9vaw==", useKey = true) // "unknown_class_system_hook" // Placeholder

             //                      // === ENHANCEMENT: Anti-detection checks at the start of each hook callback ===
             //                      // If it's a system app, ignored, detection is triggered, OR blocked by Interceptor, let the original method run.
             //                      if (isSystemApp(callingPackage) || ignorePkg.contains(callingPackage) || runDetectionChecks() || LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
             //                           // XposedBridge.log("[$TAG] Hooked LocationManagerService.getLastLocation() -> Skipping for system/ignored/detected/blocked package [${callingPackage}].") // This log can be very noisy
             //                           if (LocationRequestInterceptor.shouldBlockRequest(methodName, className)) {
             //                               param.result = null // Set result to null if blocked by Interceptor
             //                           }
             //                           return // EXIT from the callback
             //                      }

             //                      // === ENHANCEMENT: Stack Trace Check ===
             //                      makeHookingSafer(param)
             //                      if (param.result == null) { // If makeHookingSafer set result to null
             //                          // XposedBridge.log("[$TAG] Hooked LocationManagerService.getLastLocation() -> Stack trace check failed. Returning null.") // Log (optional)
             //                          return // Exit the hook
             //                      }

             //                       // Faking Logic: Update hook from Service, create fake Location, replace result
             //                      updateFakeLocationDataFromService()
             //                      val fakeLocation = createFakeLocation(null) // Provider can be null or specify a default

             //                      if (fakeLocation != null) {
             //                          param.result = fakeLocation // <<< Replace the method result
             //                          // XposedBridge.log("[$TAG] Hooked LocationManagerService.getLastLocation [${callingPackage}] -> Injecting fake.")
             //                      } else {
             //                          // === ENHANCEMENT: Failsafe Mechanism Logging ===
             //                          XposedBridge.log("[$TAG] Hooked LocationManagerService.getLastLocation [${callingPackage}] -> Faking OFF or no hook. Allowing original.")
             //                      }
             //                  }
             //              }
             //          )
             //           XposedBridge.log("[$TAG] Successfully hooked LocationManagerService.getLastLocation().")

             //          // TODO: Hook LocationManagerService.injectLocation (important on API >= 33) (method name is obfuscated with key)
             //          //       Same pattern: findAndHookMethod, beforeHookedMethod, check isSystemApp and ignorePkg and runDetectionChecks, update hook, create fake Location, replace param.args[0]
             //          //       You need to adjust the parameters of the injectLocation method according to the Android version!

             //          // TODO: Hook other methods in LocationManagerService or LocationProvider
             //     } catch (e: Exception) {
             //          XposedBridge.log("[$TAG] Hook LocationManagerService: Error: ${e.message ?: "Unknown error"}") // FIX: Handle nullable message
             //     }
             // }
        // === End of system hook block ===
        */


        XposedBridge.log("[$TAG] Location hooks initialization complete for package: ${lpparam.packageName ?: "unknown package"}.") // FIX: Handle nullable packageName
    } // End of initHooks

    // TODO: Add other methods if needed in this LocationHook object.

} // End of LocationHook object
