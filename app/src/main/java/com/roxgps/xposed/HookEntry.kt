package com.roxgps.xposed // Sesuaikan package dengan package modul Xposed kamu

// === Import Library untuk AIDL Binding ===
// ========================================

// === Import Library untuk AIDL Binding ===
// ========================================

// === Import Interface AIDL yang Dihasilkan ===
// Pastikan file IRoxAidlService.aidl sudah dicopy ke src/main/aidl di proyek Xposed Module kamu.
// ===============================================

// TODO: Import kelas LocationHook kamu
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.roxgps.BuildConfig
import com.roxgps.IRoxAidlService
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

// =====================================================================
// Kelas Utama Xposed Hook
// Mengimplementasikan IXposedHookLoadPackage untuk menangani pemuatan package.
// Bertanggung jawab untuk:
// 1. Mendeteksi package aplikasi utama.
// 2. Melakukan AIDL Binding ke RoxAidlService.
// 3. Menyediakan objek IRoxAidlService ke LocationHook.
// 4. Memulai proses hooking metode lokasi melalui LocationHook.
// =====================================================================
class HookEntry : IXposedHookLoadPackage {

    // TODO: Ganti dengan package name aplikasi utama kamu (menggunakan BuildConfig lebih aman)
    private val MY_APPLICATION_PACKAGE_NAME = BuildConfig.APPLICATION_ID // <<< Menggunakan BuildConfig.APPLICATION_ID

    // Referensi ke objek IRoxAidlService yang terikat (bound).
    // Ini akan diisi di onServiceConnected dan digunakan oleh LocationHook.
    // Dibuat nullable karena awalnya null.
    private var roxAidlService: IRoxAidlService? = null

    // Implementasi ServiceConnection untuk menangani status binding
    private val serviceConnection = object : ServiceConnection {
        // Dipanggil saat koneksi ke Service berhasil dibuat
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // Mendapatkan objek IRoxAidlService dari IBinder yang diterima
            roxAidlService = IRoxAidlService.Stub.asInterface(service) // Mengkonversi IBinder ke AIDL interface
            XposedBridge.log("RoxGpsXposed: AIDL Service Connected. Binder obtained.") // Log di logcat Xposed

            // === Berikan objek IRoxAidlService ke LocationHook ===
            // LocationHook butuh objek ini untuk memanggil getLatestFakeLocation(), setHookStatus, dll.
            LocationHook.setAidlService(roxAidlService) // <<< Panggil metode di LocationHook untuk set Service

            // === Laporkan status binding berhasil ke aplikasi utama (opsional tapi disarankan) ===
            // Panggil metode di RoxAidlService via AIDL untuk memberi tahu aplikasi utama
            // bahwa binding dari sisi hook berhasil.
            try {
                // Panggil setHookStatus(true) untuk memberi tahu aplikasi utama bahwa hook aktif di proses ini
                roxAidlService?.setHookStatus(true) // Contoh: laporkan hook aktif di proses target
                XposedBridge.log("RoxGpsXposed: Reported hook status (true) via AIDL.")
                // TODO: Jika ada metode lain di AIDL untuk melaporkan status binding spesifik, panggil di sini.
                //       Misal, jika HookStatus.kt juga dicopy ke proyek Xposed:
                //       import com.roxgps.xposed.HookStatus
                //       roxAidlService?.reportHookStatus(HookStatus.BoundAndReady.javaClass.name) // Kirim nama kelas Enum/Sealed Class
            } catch (e: RemoteException) {
                XposedBridge.log("RoxGpsXposed: RemoteException while reporting hook status: ${e.message}")
            } catch (e: Exception) {
                XposedBridge.log("RoxGpsXposed: Exception while reporting hook status: ${e.message}")
            }
            // ================================================================================
        }

        // Dipanggil saat koneksi ke Service terputus secara tidak terduga (misal, proses aplikasi utama crash)
        override fun onServiceDisconnected(name: ComponentName?) {
            roxAidlService = null // Set objek AIDL menjadi null karena koneksi putus
            XposedBridge.log("RoxGpsXposed: AIDL Service Disconnected unexpectedly.") // Log di logcat Xposed

            // === Beri tahu LocationHook bahwa koneksi terputus ===
            // LocationHook perlu tahu agar tidak mencoba memanggil metode AIDL yang null.
            LocationHook.setAidlService(null) // <<< Beri tahu LocationHook bahwa Service null

            // TODO: Laporkan status koneksi terputus ke aplikasi utama jika memungkinkan (sulit jika Service mati/proses crash)
        }
        // TODO: Pertimbangkan override onBindingDied (API 26+) dan onNullBinding (API 28+)
    }


    // Metode utama yang dipanggil oleh Xposed Framework saat sebuah package dimuat
    @SuppressLint("PrivateApi")
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Log nama package yang sedang dimuat
        XposedBridge.log("RoxGpsXposed: Loaded package: ${lpparam.packageName}")

        // === Cek apakah package yang dimuat adalah aplikasi utama kita (target hook) ===
        if (lpparam.packageName == MY_APPLICATION_PACKAGE_NAME) {
            XposedBridge.log("RoxGpsXposed: Target application package found: $MY_APPLICATION_PACKAGE_NAME. Attempting AIDL binding and location hooking.")

            // === Implementasikan AIDL Binding ke RoxAidlService (di dalam blok ini) ===

            // 1. Buat Intent untuk Service AIDL
            val serviceIntent = Intent(IRoxAidlService::class.java.name).apply {
                component = ComponentName(MY_APPLICATION_PACKAGE_NAME, "com.roxgps.service.RoxAidlService") // <<< Set ComponentName ke Service di aplikasi utama
                // Optional: Tambahkan hook extra jika Service AIDL membutuhkannya saat binding
            }

            // 2. Dapatkan Context aplikasi target (gunakan cara yang paling stabil di Xposed)
            // Menggunakan refleksi (ActivityThread.currentApplication()) adalah cara umum,
            // meskipun ada warning API internal. Metode lain mungkin menggunakan XposedHelpers atau XposedBridge.
            val appContext: Context? = try {
                // Ini cara refleksi yang tadi kita bahas, seringkali berhasil di Xposed
                @Suppress("PrivateApi")
                lpparam.classLoader.loadClass("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as Context
            } catch (e: Exception) {
                XposedBridge.log("RoxGpsXposed: Failed to get application context for binding: ${e.message}")
                null
            }
            // Opsi lain (jika Xposed/library bantu menyediakan): AndroidAppHelper.currentApplication()


            // 3. Lakukan Binding ke Service
            if (appContext != null) {
                try {
                    // Bind ke Service. Gunakan flag BIND_AUTO_CREATE agar Service dibuat jika belum berjalan.
                    val bindSuccess = appContext.bindService(
                        serviceIntent, // Intent Service
                        serviceConnection, // Implementasi ServiceConnection
                        Context.BIND_AUTO_CREATE // Flag binding: buat Service jika belum ada
                    )
                    if (bindSuccess) {
                        XposedBridge.log("RoxGpsXposed: bindService call successful. Waiting for onServiceConnected...")
                        // onServiceConnected akan dipanggil jika binding berhasil
                    } else {
                        XposedBridge.log("RoxGpsXposed: bindService call returned false. Binding failed immediately.")
                        LocationHook.setAidlService(null) // Pastikan LocationHook tidak punya referensi null
                        // TODO: Laporkan error binding ke aplikasi utama jika memungkinkan (sulit dari sini)
                    }
                } catch (e: Exception) {
                    XposedBridge.log("RoxGpsXposed: Exception during bindService call: ${e.message}")
                    LocationHook.setAidlService(null) // Pastikan LocationHook tidak punya referensi null
                    // TODO: Laporkan error binding ke aplikasi utama jika memungkinkan
                }
            } else {
                XposedBridge.log("RoxGpsXposed: Application context is null, cannot bind to Service.")
                LocationHook.setAidlService(null) // Pastikan LocationHook tidak punya referensi null
                // TODO: Laporkan error binding ke aplikasi utama jika memungkinkan
            }

            // === Mulai proses hooking metode lokasi melalui LocationHook (di dalam blok ini) ===
            // LocationHook akan menggunakan objek roxAidlService yang diset di onServiceConnected.
            LocationHook.initHooks(lpparam) // <<< Panggil metode inisialisasi hook di LocationHook

        } else {
            // Jika package yang dimuat BUKAN aplikasi utama kita
            // Lakukan hooking lain jika diperlukan di package ini
            // XposedBridge.log("RoxGpsXposed: Not target package, skipping AIDL binding and location hooking.")
        }
    }

    // TODO: Tambahkan metode lain jika diperlukan di kelas HookEntry ini.
    // Contoh: Metode untuk unbind Service saat proses aplikasi utama dimatikan (jarang perlu, sistem biasanya handle)

    // Catatan: Pastikan proyek Xposed Module kamu punya dependency yang benar
    // untuk Xposed API dan juga AIDL interface yang dihasilkan dari file .aidl aplikasi utama.
}