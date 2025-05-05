package com.roxgps.xposed // Pastikan package ini sesuai

// =====================================================================
// Import Library LocationHook
// =====================================================================

// https://github.com/rovo89/XposedBridge/wiki/Helpers
import android.annotation.SuppressLint // Untuk suppress lint
import android.app.AndroidAppHelper // Untuk AndroidAppHelper.currentApplication() (Jika masih dipakai)
import android.content.Context // Untuk Context
import android.location.Location // Untuk objek Location standar
import android.location.LocationManager // Untuk LocationManager
import android.location.LocationRequest // Untuk LocationRequest
import android.os.Build // Untuk cek versi Android
import de.robv.android.xposed.IXposedHookLoadPackage // Xposed Hook Interface (Tidak perlu di object hook, tapi di HookEntry)
import de.robv.android.xposed.XC_MethodHook // Method Hook class
import de.robv.android.xposed.XposedBridge // Xposed Logging & Utilities (Tetap perlu untuk hookMethod dll, tapi log-nya pakai FileLogger)
import de.robv.android.xposed.XposedHelpers // Xposed Reflection & Hooking Utilities
import de.robv.android.xposed.callbacks.XC_LoadPackage // Load Package Callback
import com.roxgps.BuildConfig // Import BuildConfig
import org.lsposed.hiddenapibypass.HiddenApiBypass // Untuk HiddenApiBypass
import timber.log.Timber // Untuk Timber logging (jika digunakan)
import java.util.* // Untuk Random
import kotlin.math.cos // Untuk cos
// Import interface AIDL
import com.roxgps.ipc.IRoxGpsService // Import interface AIDL
import com.roxgps.utils.FileLogger // Import FileLogger


// =====================================================================
// Object LocationHook
// =====================================================================

// Object singleton untuk mengelola hook terkait lokasi
object LocationHook {

    // =====================================================================
    // Properti Hook State dan Data Lokasi
    // =====================================================================

    // Lokasi palsu yang akan disuntikkan (latitude dan longitude)
    private var newlat: Double = 45.0000 // Default / Placeholder awal
    private var newlng: Double = 0.0000 // Default / Placeholder awal
    // Akurasi lokasi palsu
    private var accuracy: Float = 0.0f // Default / Placeholder awal
    // Status START (apakah fake GPS sedang aktif) - Akan didapat dari Service AIDL
    private var isStarted: Boolean = false // Default false

    // Instance interface Service AIDL yang terhubung. Akan diset dari HookEntry.
    // Ini yang akan dipakai buat panggil method di RoxGPS App.
    private var aidlService: IRoxGpsService? = null // Awalnya null, diset nanti


    // Properties lain untuk perhitungan random position
    private const val pi = 3.14159265359
    private val rand: Random = Random()
    private const val earth = 6378137.0

    // PERHATIAN: Xshare masih digunakan untuk isRandomPosition dan accuracy.
    // Ini MUNGKIN perlu dipindahkan ke AIDL juga jika logicnya tergantung setting di App.
    // Untuk saat ini, biarkan dulu tergantung Xshare.
    private val settings = Xshare() // <-- Dependency ke Xshare/Prefs lama


    // Timestamp terakhir kali lokasi di-update dari sumber (AIDL atau fallback)
    private var mLastUpdated: Long = 0
    // Interval update minimum (milidetik) sebelum panggil updateLocation lagi
    private val updateInterval: Long = 80 // Atau 200, sesuaikan.

    // Daftar package yang diabaikan (tidak disuntik lokasi palsu)
    private val ignorePkg = arrayListOf("com.android.location.fused", BuildConfig.APPLICATION_ID)

    // Context (Jika dibutuhkan, gunakan Context yang didapat dari parameter hook atau lpparam.classLoader)
    // AndroidAppHelper.currentApplication() bisa nggak reliable di semua hook.
    // private val context by lazy { AndroidAppHelper.currentApplication() as Context } // <-- Hati-hati pakai ini

    // =====================================================================
    // Metode untuk Menerima Instance Service AIDL
    // Dipanggil dari HookEntry setelah Service terhubung.
    // =====================================================================

    /**
     * Metode untuk menyetel instance Service AIDL yang terhubung.
     * Dipanggil dari HookEntry.onServiceConnected atau onServiceDisconnected.
     *
     * @param service Instance [IRoxGpsService] yang terhubung, atau null jika terputus.
     */
    fun setAidlService(service: IRoxGpsService?) {
        aidlService = service // Menyimpan instance service
        if (service == null) {
            // Opsional: Laporkan ke log atau set status internal bahwa service tidak tersedia
            FileLogger.log("LocationHook", "AIDL Service tidak tersedia.", "XposedLog", "W") // Menggunakan FileLogger
            // XposedBridge.log("LOCATION_HOOK: AIDL Service tidak tersedia.") // Dihapus, pakai FileLogger
             // Atur status START jadi false secara default jika service tidak ada
             isStarted = false
        } else {
             FileLogger.log("LocationHook", "AIDL Service diterima.", "XposedLog", "I") // Menggunakan FileLogger
             // XposedBridge.log("LOCATION_HOOK: AIDL Service diterima.") // Dihapus, pakai FileLogger
             // Coba update lokasi awal segera setelah service terhubung? Opsional.
             // updateLocation() // Panggil updateLocation() untuk mendapatkan data awal
        }
    }

    // =====================================================================
    // Metode untuk Update Data Lokasi (Mengambil dari Service AIDL)
    // Dipanggil di dalam hook sebelum mengembalikan lokasi.
    // =====================================================================

    /**
     * Mengupdate data lokasi palsu dari Service AIDL.
     * Dipanggil secara berkala di dalam hook jika interval update sudah lewat.
     */
    private fun updateLocation() {
        // Hanya update jika service AIDL tersedia
        if (aidlService != null) {
            try {
                // Panggil method di Service AIDL untuk mendapatkan lokasi palsu dan status
                // Asumsi getLatestFakeLocation() mengembalikan doubleArray [lat, lon, isStartedDouble]
                val locationData = aidlService?.getLatestFakeLocation()

                if (locationData != null && locationData.size >= 3) {
                    // Data diterima dari Service AIDL, update state di LocationHook
                    newlat = locationData[0] // Latitude
                    newlng = locationData[1] // Longitude
                    isStarted = locationData[2] > 0.5 // Status START (konversi Double ke Boolean)

                    // Optional: Ambil akurasi dan random position dari Service AIDL juga?
                    // Saat ini masih baca dari settings/Xshare
                    // accuracy = locationData[3]?.toFloat() ?: settings.accuracy!!.toFloat() // Contoh kalau data akurasi di index 3
                    // val randomPosFlag = locationData[4] ?: settings.isRandomPosition // Contoh kalau random pos di index 4

                    // Jika random position masih dihitung di hook, gunakan settings/Xshare
                    if (settings.isRandomPosition) { // <-- Masih tergantung Xshare
                         val x = (rand.nextInt(50) - 15).toDouble()
                         val y = (rand.nextInt(50) - 15).toDouble()
                         val dlat = x / earth
                         val dlng = y / (earth * cos(pi * newlat / 180.0)) // Pakai newlat hasil dari AIDL
                         newlat += (dlat * 180.0 / pi)
                         newlng += (dlng * 180.0 / pi)
                    }
                    accuracy = settings.accuracy!!.toFloat() // <-- Masih tergantung Xshare


                    mLastUpdated = System.currentTimeMillis() // Update timestamp update
                    FileLogger.log("LocationHook", "Lokasi palsu diperbarui dari AIDL: Lat=$newlat, Lon=$newlng, Started=$isStarted", "XposedLog", "I") // Menggunakan FileLogger
                    // XposedBridge.log("LOCATION_HOOK: Lokasi diperbarui dari AIDL: Lat=$newlat, Lon=$newlng, Started=$isStarted") // Dihapus, pakai FileLogger

                } else {
                    // Service tersedia tapi data null atau format salah?
                    FileLogger.log("LocationHook", "Data lokasi dari AIDL Service null atau format salah.", "XposedLog", "W") // Menggunakan FileLogger
                    // XposedBridge.log("LOCATION_HOOK: Data lokasi dari AIDL Service null atau format salah.") // Dihapus, pakai FileLogger
                    // Opsional: Laporkan error ke Service RoxGPS via AIDL?
                    // aidlService?.reportHookError("Data lokasi dari AIDL Service null atau format salah.")
                    // Atur status START jadi false atau gunakan lokasi default jika data tidak valid
                    isStarted = false
                }

            } catch (e: Exception) {
                // Error saat memanggil method AIDL
                FileLogger.log("LocationHook", "Error saat panggil AIDL Service: ${e.message}", "XposedLog", "E") // Menggunakan FileLogger
                // XposedBridge.log("LOCATION_HOOK: Error saat panggil AIDL Service: ${e.message}") // Dihapus, pakai FileLogger
                // Laporkan error ini ke Service RoxGPS via AIDL
                aidlService?.reportHookError("Error AIDL Hook Lokasi: ${e.message}") // Panggil method AIDL reportHookError()
                // Atur status START jadi false atau gunakan lokasi default jika error
                isStarted = false // Tidak menyuntik lokasi palsu jika ada error komunikasi
            }
        } else {
            // Service AIDL belum terhubung atau sudah terputus
             // Log ini bisa terlalu sering kalau Service sering tidak tersedia.
             // FileLogger.log("LocationHook", "AIDL Service belum/tidak tersedia saat updateLocation dipanggil.", "XposedLog", "W") // Menggunakan FileLogger
             // XposedBridge.log("LOCATION_HOOK: AIDL Service belum/tidak tersedia.") // Dihapus, pakai FileLogger
             // Atur status START jadi false jika service tidak ada
             isStarted = false // Tidak menyuntik lokasi palsu jika service tidak ada
        }
    }

    // =====================================================================
    // Metode initHooks - Mendaftarkan Semua Hook
    // =====================================================================

    // Metode ini dipanggil dari HookEntry untuk mendaftarkan semua hook lokasi.
    @SuppressLint("NewApi") // Suppress warning NewApi karena ada cek Build.VERSION.SDK_INT
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {

        // === Hook Sistem Android (Package "android") ===
        if (lpparam.packageName == "android") {
            FileLogger.log("LocationHook", "Hooking system server", "XposedLog", "I") // Menggunakan FileLogger
            // XposedBridge.log("LOCATION_HOOK: Hooking system server") // Dihapus, pakai FileLogger

             // PERHATIAN: updateLocation() di sini akan dipanggil HANYA saat kondisi ini terpenuhi.
             // Pastikan interval update cukup atau updateLocation dipanggil juga di tempat lain
             // jika kondisi ini tidak selalu terpenuhi sebelum hook lain dipanggil.
            if (System.currentTimeMillis() - mLastUpdated > updateInterval) { // Gunakan properti updateInterval
                updateLocation() // <-- Ambil lokasi dari AIDL
            }

            // Hook untuk Android < API 33 (Tiramisu) - Cek konstanta yang benar
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Hook LocationManagerService di System Server
                val LocationManagerServiceClass = XposedHelpers.findClass(
                    "com.android.server.LocationManagerService",
                    lpparam.classLoader
                )

                // Hook getLastLocation
                XposedHelpers.findAndHookMethod(
                    LocationManagerServiceClass, "getLastLocation",
                    LocationRequest::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // Pastikan updateLocation dipanggil sebelum menggunakan newlat/newlng
                             if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                 updateLocation() // <-- Ambil lokasi dari AIDL
                             }
                            // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                            // isStarted didapat dari updateLocation() dari Service AIDL
                            if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                val location = Location(LocationManager.GPS_PROVIDER)
                                location.time = System.currentTimeMillis() - 300 // Set waktu
                                location.latitude = newlat // Gunakan newlat hasil dari updateLocation()
                                location.longitude = newlng // Gunakan newlng hasil dari updateLocation()
                                location.altitude = 0.0
                                location.speed = 0F
                                location.accuracy = accuracy // Gunakan accuracy hasil dari updateLocation()
                                // location.speedAccuracyMetersPerSecond = 0F // Optional, tergantung API/kebutuhan
                                param.result = location // Gantukan hasil asli dengan lokasi palsu
                                FileLogger.log("LocationHook", "Hook getLastLocation (System <33) -> Lat=$newlat, Lon=$newlng", "XposedLog", "I") // Menggunakan FileLogger
                                // XposedBridge.log("LOCATION_HOOK: Hook getLastLocation (System <33) -> Lat=$newlat, Lon=$newlng") // Dihapus
                            }
                             // Jika isStarted false, param.result tidak diubah, method asli akan jalan.
                        }
                    }
                )

                // Hook method-method Gnss/Batching di System Server (butuh disesuaikan)
                // Tujuan: kemungkinan untuk memblokir update lokasi asli.
                 // Perlu hati-hati, hook ini bisa merusak fungsionalitas GPS asli kalau tidak pas.
                for (method in LocationManagerServiceClass.declaredMethods) {
                     // Cek nama method dan return type (butuh disesuaikan dengan versi Android spesifik)
                    if (method.returnType == Boolean::class.java) { // Asumsi return type Boolean untuk beberapa metode
                        if (method.name == "addGnssBatchingCallback" ||
                            method.name == "addGnssMeasurementsListener" ||
                            method.name == "addGnssNavigationMessageListener" ||
                            method.name == "addGnssStatusListener" || // Contoh method lain
                            method.name == "requestLocationUpdates" // Contoh hook request updates di service level
                        ) {
                            // Hook sebelum method asli dipanggil
                            XposedBridge.hookMethod(
                                method,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam) {
                                        // Mengembalikan false atau null bisa memblokir pemanggilan asli.
                                        // Log atau cek kondisi sebelum memblokir.
                                        FileLogger.log("LocationHook", "Blocking method: ${method.name}", "XposedLog", "I") // Menggunakan FileLogger
                                        // XposedBridge.log("LOCATION_HOOK: Blocking method: ${method.name}") // Dihapus
                                        param.result = false // Contoh memblokir dengan mengembalikan false
                                        // Atau param.result = null // Jika return type method adalah objek
                                    }
                                }
                            )
                        }
                    } else if (method.returnType == Void::class.java) { // Asumsi return type Void untuk metode lain
                          if (method.name == "removeUpdates" // Contoh hook removeUpdates
                            ) {
                              // Hook sebelum method asli dipanggil
                              XposedBridge.hookMethod(
                                  method,
                                  object : XC_MethodHook() {
                                      override fun beforeHookedMethod(param: MethodHookParam) {
                                          // Log atau cek kondisi sebelum memblokir/mengubah argumen.
                                          FileLogger.log("LocationHook", "Blocking method: ${method.name}", "XposedLog", "I") // Menggunakan FileLogger
                                          // XposedBridge.log("LOCATION_HOOK: Blocking method: ${method.name}") // Dihapus
                                          param.result = null // Contoh memblokir method Void
                                      }
                                  }
                              )
                          }
                    }
                }

                // Hook callLocationChangedLocked (dipanggil saat ada update lokasi baru dari provider)
                // PERHATIAN: Pastikan nama class Receiver ini benar sesuai versi Android target.
                XposedHelpers.findAndHookMethod(
                    "com.android.server.LocationManagerService.Receiver", // com.android.server.LocationManagerService$Receiver
                    lpparam.classLoader,
                    "callLocationChangedLocked",
                    Location::class.java, // Parameter: Location object
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // Pastikan updateLocation dipanggil sebelum menggunakan newlat/newlng
                             if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                 updateLocation() // <-- Ambil lokasi dari AIDL
                             }
                             // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                             // isStarted didapat dari updateLocation() dari Service AIDL
                             if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                // Mengambil lokasi asli dari argumen method
                                lateinit var originalLocation: Location
                                if (param.args[0] is Location) {
                                    originalLocation = param.args[0] as Location
                                    // Buat Location object baru dengan data palsu tapi provider dari asli
                                    val fakeLocation = Location(originalLocation.provider)
                                    fakeLocation.time = originalLocation.time // Gunakan timestamp asli
                                    fakeLocation.accuracy = accuracy // Gunakan akurasi palsu
                                    fakeLocation.bearing = originalLocation.bearing // Gunakan bearing asli
                                    fakeLocation.bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees // Gunakan bearing accuracy asli
                                    fakeLocation.elapsedRealtimeNanos = originalLocation.elapsedRealtimeNanos // Gunakan elapsed time asli
                                    fakeLocation.verticalAccuracyMeters = originalLocation.verticalAccuracyMeters // Gunakan vertical accuracy asli
                                    // Set lat/lon palsu
                                    fakeLocation.latitude = newlat
                                    fakeLocation.longitude = newlng
                                    fakeLocation.altitude = originalLocation.altitude // Gunakan altitude asli atau set 0.0
                                    fakeLocation.speed = originalLocation.speed // Gunakan speed asli atau set 0.0F
                                    fakeLocation.speedAccuracyMetersPerSecond = originalLocation.speedAccuracyMetersPerSecond // Gunakan speed accuracy asli
                                    // Set isFromMockProvider ke false (jika perlu menyembunyikan status mock)
                                     try {
                                         HiddenApiBypass.invoke(
                                             fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false
                                         )
                                     } catch (e: Exception) {
                                         FileLogger.log("LocationHook", "unable to set mock: $e", "XposedLog", "E") // Menggunakan FileLogger
                                         // XposedBridge.log("LOCATION_HOOK: unable to set mock $e") // Dihapus
                                         // Opsional: laporkan error ke Service AIDL
                                         // aidlService?.reportHookError("Failed to setIsFromMockProvider: $e")
                                     }
                                    // Gantukan argumen method dengan lokasi palsu
                                    param.args[0] = fakeLocation
                                    FileLogger.log("LocationHook", "Hook callLocationChangedLocked (System <33) -> Menyuntik Lokasi Palsu.", "XposedLog", "I") // Menggunakan FileLogger
                                    // XposedBridge.log("LOCATION_HOOK: Hook callLocationChangedLocked (System <33) -> Menyuntik Lokasi Palsu.") // Dihapus
                                } else {
                                     // Argumen null atau bukan Location
                                     FileLogger.log("LocationHook", "callLocationChangedLocked (System <33) - arg[0] null atau bukan Location.", "XposedLog", "W") // Menggunakan FileLogger
                                     // XposedBridge.log("LOCATION_HOOK: callLocationChangedLocked (System <33) - arg[0] null atau bukan Location.") // Dihapus
                                }
                            }
                             // Jika isStarted false, argumen tidak diubah, update lokasi asli akan diteruskan.
                        }
                    }
                )
            // Hook untuk Android >= API 33 (Tiramisu) - Cek konstanta yang benar
            } else { // >= API 33
                 // Hook LocationManagerService (nama class bisa beda sedikit)
                val LocationManagerServiceClass = XposedHelpers.findClass(
                    "com.android.server.location.LocationManagerService", // Nama class bisa beda di versi lain
                    lpparam.classLoader
                )
                // Hook getLastLocation (Signature method bisa beda di versi lain)
                for (method in LocationManagerServiceClass.declaredMethods) {
                     // Cari method getLastLocation yang mengembalikan Location
                    if (method.name == "getLastLocation" && method.returnType == Location::class.java) {
                        // Parameter signature mungkin beda, perlu disesuaikan.
                        // Contoh: String::class.java, LastLocationRequest::class.java, String::class.java, String::class.java
                        // Hook method jika signature cocok.
                        // XposedHelpers.findAndHookMethod(LocationManagerServiceClass, "getLastLocation", ...) // Pakai findAndHookMethod kalau signature spesifik
                         XposedBridge.hookMethod( // Hook berdasarkan objek Method jika signature rumit
                             method,
                             object : XC_MethodHook() {
                                 override fun beforeHookedMethod(param: MethodHookParam) {
                                     // Pastikan updateLocation dipanggil sebelum menggunakan newlat/newlng
                                     if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                         updateLocation() // <-- Ambil lokasi dari AIDL
                                     }
                                     // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                                     if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                         val location = Location(LocationManager.GPS_PROVIDER) // Provider bisa didapat dari argumen kalau ada
                                         location.time = System.currentTimeMillis() - 300
                                         location.latitude = newlat
                                         location.longitude = newlng
                                         location.altitude = 0.0
                                         location.speed = 0F
                                         location.accuracy = accuracy
                                         // location.speedAccuracyMetersPerSecond = 0F // Optional
                                         param.result = location // Gantukan hasil asli
                                         FileLogger.log("LocationHook", "Hook getLastLocation (System >=33) -> Lat=$newlat, Lon=$newlng", "XposedLog", "I") // Menggunakan FileLogger
                                         // XposedBridge.log("LOCATION_HOOK: Hook getLastLocation (System >=33) -> Lat=$newlat, Lon=$newlng") // Dihapus
                                     }
                                     // Jika isStarted false, method asli jalan
                                 }
                             }
                         )
                         // Jika ada beberapa method getLastLocation, hook yang sesuai kebutuhan
                         break // Hentikan loop setelah menemukan method yang tepat
                    } else if (method.returnType == Void::class.java) { // Hook method void (Gnss/Batching)
                        // Contoh method yang mungkin diblokir
                        if (method.name == "startGnssBatch" ||
                            method.name == "addGnssAntennaInfoListener" ||
                            method.name == "addGnssMeasurementsListener" ||
                            method.name == "addGnssNavigationMessageListener" ||
                            method.name == "requestLocationUpdates" // Contoh hook request updates
                        ) {
                            XposedBridge.hookMethod(
                                method,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam) {
                                        FileLogger.log("LocationHook", "Blocking method: ${method.name}", "XposedLog", "I") // Menggunakan FileLogger
                                        // XposedBridge.log("LOCATION_HOOK: Blocking method: ${method.name}") // Dihapus
                                        param.result = null // Memblokir method Void
                                    }
                                }
                            )
                        }
                    }
                }
                 // Hook injectLocation (method yang dipanggil oleh FusedLocationProviderClient dkk)
                 // PERHATIAN: Nama class dan signature method bisa beda di versi lain.
                 // Ini hook penting untuk menyuntikkan lokasi palsu saat aplikasi meminta update.
                XposedHelpers.findAndHookMethod(
                    LocationManagerServiceClass, // Gunakan class yang ditemukan
                    "injectLocation", // Nama method, bisa beda
                    Location::class.java, // Parameter: Location object
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // Pastikan updateLocation dipanggil sebelum menggunakan newlat/newlng
                            if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                updateLocation() // <-- Ambil lokasi dari AIDL
                            }
                            // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                            if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                // Mengambil lokasi asli dari argumen method
                                lateinit var originalLocation: Location
                                if (param.args[0] is Location) {
                                    originalLocation = param.args[0] as Location
                                     // Buat Location object baru dengan data palsu tapi provider dari asli
                                    val fakeLocation = Location(originalLocation.provider)
                                    fakeLocation.time = originalLocation.time // Gunakan timestamp asli
                                    fakeLocation.accuracy = accuracy // Gunakan akurasi palsu
                                    fakeLocation.bearing = originalLocation.bearing // Gunakan bearing asli
                                    fakeLocation.bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees // Gunakan bearing accuracy asli
                                    fakeLocation.elapsedRealtimeNanos = originalLocation.elapsedRealtimeNanos // Gunakan elapsed time asli
                                    fakeLocation.verticalAccuracyMeters = originalLocation.verticalAccuracyMeters // Gunakan vertical accuracy asli
                                     // Set lat/lon palsu
                                    fakeLocation.latitude = newlat
                                    fakeLocation.longitude = newlng
                                    fakeLocation.altitude = originalLocation.altitude // Gunakan altitude asli atau set 0.0
                                    fakeLocation.speed = originalLocation.speed // Gunakan speed asli atau set 0.0F
                                    fakeLocation.speedAccuracyMetersPerSecond = originalLocation.speedAccuracyMetersPerSecond // Gunakan speed accuracy asli
                                    // Set isFromMockProvider ke false (jika perlu menyembunyikan status mock)
                                    try {
                                         HiddenApiBypass.invoke(
                                             fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false
                                         )
                                     } catch (e: Exception) {
                                         FileLogger.log("LocationHook", "unable to set mock: $e", "XposedLog", "E") // Menggunakan FileLogger
                                         // Opsional: laporkan error ke Service AIDL
                                         // aidlService?.reportHookError("Failed to setIsFromMockProvider: $e")
                                     }
                                     // Gantukan argumen method dengan lokasi palsu
                                    param.args[0] = fakeLocation
                                    FileLogger.log("LocationHook", "Hook injectLocation (System >=33) -> Menyuntik Lokasi Palsu.", "XposedLog", "I") // Menggunakan FileLogger
                                    // XposedBridge.log("LOCATION_HOOK: Hook injectLocation (System >=33) -> Menyuntik Lokasi Palsu.") // Dihapus
                                } else {
                                     // Argumen null atau bukan Location
                                     FileLogger.log("LocationHook", "injectLocation (System >=33) - arg[0] null atau bukan Location.", "XposedLog", "W") // Menggunakan FileLogger
                                     // XposedBridge.log("LOCATION_HOOK: injectLocation (System >=33) - arg[0] null atau bukan Location.") // Dihapus
                                }
                            }
                            // Jika isStarted false, argumen tidak diubah, update lokasi asli akan diteruskan.
                        }
                    }
                )
            }
        // === Hook Aplikasi Biasa (Bukan Package "android") ===
        // Hook method di class android.location.Location dan android.location.LocationManager
        } else { // application hook (lpparam.packageName != "android")

            // Update lokasi minimal setiap interval saat hook dipicu
            // if (System.currentTimeMillis() - mLastUpdated > updateInterval) { // Ini sudah ada di setiap hook
            //     updateLocation() // <-- Ambil lokasi dari AIDL
            // }

            val LocationClass = XposedHelpers.findClass(
                "android.location.Location",
                lpparam.classLoader
            )

            // Hook getter method di class Location (getLatitude, getLongitude, getAccuracy)
            // Ini metode brute force yang bisa makan resource, tergantung seberapa sering aplikasi target panggil getter ini.
            for (method in LocationClass.declaredMethods) {
                if (method.name == "getLatitude" && method.returnType == Double::class.java) { // Pastikan return type Double
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                // Pastikan updateLocation dipanggil sebelum menggunakan newlat/newlng
                                if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                    updateLocation() // <-- Ambil lokasi dari AIDL
                                }
                                // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                                // isStarted didapat dari updateLocation() dari Service AIDL
                                if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                    param.result = newlat // Gantukan hasil asli dengan newlat
                                    // Log ini bisa spam kalau dipanggil sering. Pakai WARN atau ERROR atau hapus.
                                    // FileLogger.log("LocationHook", "Hook getLatitude -> Lat=$newlat", "XposedLog", "I") // Menggunakan FileLogger
                                }
                                // Jika isStarted false, method asli jalan
                            }
                        }
                    )
                } else if (method.name == "getLongitude" && method.returnType == Double::class.java) { // Pastikan return type Double
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                    updateLocation() // <-- Ambil lokasi dari AIDL
                                }
                                if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                    param.result = newlng // Gantukan hasil asli dengan newlng
                                     // Log ini bisa spam
                                    // FileLogger.log("LocationHook", "Hook getLongitude -> Lon=$newlng", "XposedLog", "I") // Menggunakan FileLogger
                                }
                            }
                        }
                    )
                } else if (method.name == "getAccuracy" && method.returnType == Float::class.java) { // Pastikan return type Float
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                                    updateLocation() // <-- Ambil lokasi dari AIDL
                                }
                                if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                                    param.result = accuracy // Gunakan accuracy hasil dari updateLocation()
                                    // Log ini bisa spam
                                    // FileLogger.log("LocationHook", "Hook getAccuracy -> Accuracy=$accuracy", "XposedLog", "I") // Menggunakan FileLogger
                                }
                            }
                        }
                    )
                }
            }

            // Hook method set(Location location) di class Location
            // Dipanggil saat satu Location object di-set ke Location object lain.
            // Tujuannya untuk memastikan data palsu tetap ada meskipun Location object di-copy.
            XposedHelpers.findAndHookMethod(
                LocationClass, // Gunakan class Location
                "set", // Nama method
                Location::class.java, // Parameter: Location object
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                            updateLocation() // <-- Ambil lokasi dari AIDL
                        }
                         // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                        if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                             // Mengambil lokasi asli dari argumen method (location yang akan di-set)
                            lateinit var originalLocation: Location
                            if (param.args[0] is Location) {
                                originalLocation = param.args[0] as Location
                                // Buat Location object baru dengan data palsu tapi provider dari asli
                                val fakeLocation = Location(originalLocation.provider)
                                fakeLocation.time = originalLocation.time // Gunakan timestamp asli
                                fakeLocation.accuracy = accuracy // Gunakan akurasi palsu
                                fakeLocation.bearing = originalLocation.bearing // Gunakan bearing asli
                                fakeLocation.bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees // Gunakan bearing accuracy asli
                                fakeLocation.elapsedRealtimeNanos = originalLocation.elapsedRealtimeNanos // Gunakan elapsed time asli
                                fakeLocation.verticalAccuracyMeters = originalLocation.verticalAccuracyMeters // Gunakan vertical accuracy asli
                                // Set lat/lon palsu
                                fakeLocation.latitude = newlat
                                fakeLocation.longitude = newlng
                                fakeLocation.altitude = originalLocation.altitude // Gunakan altitude asli atau set 0.0
                                fakeLocation.speed = originalLocation.speed // Gunakan speed asli atau set 0.0F
                                fakeLocation.speedAccuracyMetersPerSecond = originalLocation.speedAccuracyMetersPerSecond // Gunakan speed accuracy asli
                                // Set isFromMockProvider ke false (jika perlu menyembunyikan status mock)
                                try {
                                     HiddenApiBypass.invoke(
                                         fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false
                                     )
                                 } catch (e: Exception) {
                                     FileLogger.log("LocationHook", "unable to set mock: $e", "XposedLog", "E") // Menggunakan FileLogger
                                     // Opsional: laporkan error ke Service AIDL
                                     // aidlService?.reportHookError("Failed to setIsFromMockProvider: $e")
                                 }
                                // Gantukan argumen method dengan lokasi palsu
                                param.args[0] = fakeLocation
                                FileLogger.log("LocationHook", "Hook Location.set -> Menyuntik Lokasi Palsu.", "XposedLog", "I") // Menggunakan FileLogger
                            } else {
                                // Argumen null atau bukan Location
                                FileLogger.log("LocationHook", "Location.set - arg[0] null atau bukan Location.", "XposedLog", "W") // Menggunakan FileLogger
                            }
                        }
                         // Jika isStarted false, argumen tidak diubah, data asli akan diteruskan.
                    }
                }
            )

            // Hook getLastKnownLocation di LocationManager
            // Ini salah satu hook yang paling umum digunakan untuk mendapatkan lokasi terakhir.
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager", // Nama class LocationManager
                lpparam.classLoader,
                "getLastKnownLocation", // Nama method
                String::class.java, // Parameter: provider (String)
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (System.currentTimeMillis() - mLastUpdated > updateInterval) {
                            updateLocation() // <-- Ambil lokasi dari AIDL
                        }
                        // Suntikkan lokasi palsu HANYA JIKA isStarted TRUE dan package tidak diabaikan
                        // isStarted didapat dari updateLocation() dari Service AIDL
                        if (isStarted && !ignorePkg.contains(lpparam.packageName)) {
                            val provider = param.args[0] as? String ?: LocationManager.GPS_PROVIDER // Dapatkan provider dari argumen
                            val location = Location(provider) // Buat Location object palsu
                            location.time = System.currentTimeMillis() - 300 // Set waktu
                            location.latitude = newlat // Gunakan newlat hasil dari updateLocation()
                            location.longitude = newlng // Gunakan newlng hasil dari updateLocation()
                            location.altitude = 0.0 // Set altitude
                            location.speed = 0F // Set speed
                            location.accuracy = accuracy // Gunakan accuracy hasil dari updateLocation()
                            // location.speedAccuracyMetersPerSecond = 0F // Optional
                            // Set isFromMockProvider ke false (jika perlu menyembunyikan status mock)
                            try {
                                HiddenApiBypass.invoke(
                                    location.javaClass, location, "setIsFromMockProvider", false
                                )
                            } catch (e: Exception) {
                                FileLogger.log("LocationHook", "unable to set mock: $e", "XposedLog", "E") // Menggunakan FileLogger
                                // Opsional: laporkan error ke Service AIDL
                                // aidlService?.reportHookError("Failed to setIsFromMockProvider: $e")
                            }
                            param.result = location // Gantukan hasil asli method
                            FileLogger.log("LocationHook", "Hook getLastKnownLocation -> Lat=$newlat, Lon=$newlng", "XposedLog", "I") // Menggunakan FileLogger
                        }
                        // Jika isStarted false, method asli jalan dan mengembalikan lokasi asli terakhir.
                    }
                }
            )

             // Mungkin perlu hook method lain seperti requestLocationUpdates, removeUpdates, dsb.
             // XposedHelpers.findAndHookMethod("android.location.LocationManager", lpparam.classLoader, "requestLocationUpdates", ...)
             // XposedHelpers.findAndHookMethod("android.location.LocationManager", lpparam.classLoader, "removeUpdates", ...)
             // Tujuan hook ini: bisa mencegat atau memodifikasi request/remove update lokasi asli.
             // Atau memblokir update lokasi asli agar aplikasi target hanya terima lokasi dari hook.
        }
    }

    // =====================================================================
    // Metode Lain Hook (Utility atau Internal)
    // =====================================================================

    // Metode updateLocation() sudah ada di atas.

     // Xshare class - Perlu direview. Idealnya data ini dari AIDL juga.
     // private val settings = Xshare()

}
