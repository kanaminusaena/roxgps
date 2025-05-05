package com.roxgps.service // Atau package lain untuk Service lo (misal com.roxgps.ipc.service)

// =====================================================================
// Import Library untuk RoxGpsAidlService (Service AIDL)
// =====================================================================

import android.app.Service // Base Service class
import android.content.Intent // Untuk Intent
import android.os.IBinder // Interface untuk objek Binder
import android.os.RemoteException // Exception untuk pemanggilan lintas proses
import dagger.hilt.android.AndroidEntryPoint // Anotasi Hilt
import javax.inject.Inject // Untuk Inject
import com.roxgps.ipc.IRoxGpsService // <-- Import interface AIDL yang sudah di-generate
import com.roxgps.utils.PrefManager // Import PrefManager (untuk ambil lokasi palsu)
import com.roxgps.repository.HookStatusRepository // <-- Import Repository Hook Status
import timber.log.Timber // Logging
// Import untuk Parcelable custom
import com.roxgps.data.FakeLocationData // <-- Import FakeLocationData

// Import XposedHelpers untuk memanggil method di Module (Call Balik)
import de.robv.android.xposed.XposedHelpers // <-- Import XposedHelpers

// Import nama kelas hooker token untuk dipanggil balik
// Pastikan nama package dan nama class ini BENAR sesuai di project Xposed Module
import com.roxgps.xposed.hookers.KampretTokenHooker // <-- IMPORT KELAS HOOKER TOKEN DARI MODULE!


// =====================================================================
// Service untuk Menghost Implementasi AIDL IRoxGpsService
// Service ini akan berjalan di proses aplikasi utama dan di-bind oleh Xposed Module.
// Setelah diupdate: Mengimplementasikan getLatestGojekToken() untuk mengambil token dari Module.
// ======================================================================================================

@AndroidEntryPoint // Agar Hilt bisa meng-inject dependencies ke Service ini
class RoxGpsAidlService : Service() { // <-- Pastikan nama kelas ini konsisten

    // =====================================================================
    // Dependencies (Di-inject oleh Hilt)
    // =====================================================================
    // Kita perlu akses PrefManager untuk mendapatkan lokasi palsu terbaru yang disimpan user.
    @Inject // Inject PrefManager
    lateinit var prefManager: PrefManager

    // Inject HookStatusRepository untuk mengupdate status hook dan error yang dilaporkan Module.
    @Inject // Inject Repository Hook Status
    lateinit var hookStatusRepository: HookStatusRepository


    // =====================================================================
    // Implementasi AIDL Interface (Binder)
    // Ini adalah objek yang akan dikembalikan oleh onBind().
    // Mengimplementasikan semua method di IRoxGpsService.
    // =====================================================================
    private val binder = object : IRoxGpsService.Stub() { // Implementasi dari class Stub

        // Implementasi method getLatestFakeLocation() dari AIDL
        // Dipanggil oleh Xposed Module untuk mendapatkan lokasi palsu LENGKAP.
        // MENGEMBALIKAN FakeLocationData.
        @Throws(RemoteException::class)
        override fun getLatestFakeLocation(): FakeLocationData { // <-- Return FakeLocationData (sesuai AIDL terbaru)
            Timber.d("AIDL: getLatestFakeLocation() called by client")
            // Ambil lat/lon palsu terbaru dari PrefManager
            val lat = prefManager.getLat.value ?: 0.0 // Gunakan nilai default jika null/belum ada
            val lon = prefManager.getLng.value ?: 0.0 // Gunakan nilai default jika null/belum ada

            // TODO: Implementasi cara mendapatkan/menentukan nilai REALISTIS untuk properti lain!
            // Lo perlu tambahin logic di aplikasi utama untuk menghitung/menyimpan speed, bearing, dll.
            // jika user pakai joystick atau simulasi gerakan.
            // Untuk sementara, set nilai placeholder yang umum.
            // PROPSAL: Values ini bisa disimpan di PrefManager atau di StateFlow di ViewModel yang di-update oleh UI/logic gerakan.

            // Buat objek FakeLocationData dengan nilai lengkap
            // Contoh sederhana: isStarted bisa didapat dari status hook di HookStatusRepository
            val isStartedStatus = hookStatusRepository.isModuleHooked.value // <-- Ambil status dari Repo
            val fakeData = FakeLocationData(
                latitude = lat,
                longitude = lon,
                accuracy = 1.0f, // Akurasi palsu (misal 1 meter) - sesuaikan jika perlu
                speed = 0.0f, // Kecepatan (misal 0 m/s jika lokasi statis) - hitung jika simulasi gerakan
                bearing = 0.0f, // Arah (misal 0 jika statis) - hitung jika simulasi gerakan
                altitude = 0.0, // Ketinggian (misal 0) - sesuaikan jika perlu
                time = System.currentTimeMillis(), // Waktu saat ini (penting untuk realisme)
                elapsedRealtimeNanos = System.nanoTime(), // Waktu elapsed real time saat ini (penting untuk realisme)
                provider = if (isStartedStatus) "gps" else "real" // Provider: "gps" jika started, "real" jika tidak (atau sesuaikan logic)
            )
            Timber.d("Returning FakeLocationData: $fakeData")

            // Kembalikan objek FakeLocationData lengkap
            return fakeData
        }

        // Implementasi method setHookStatus() dari AIDL
        // Dipanggil oleh Xposed Module untuk melaporkan status hook.
        // Melaporkan status ke HookStatusRepository.
        @Throws(RemoteException::class)
        override fun setHookStatus(hooked: Boolean) {
            Timber.d("AIDL: setHookStatus() called by client with hooked=$hooked")
            // Update status di HookStatusRepository (menggunakan Repository yang di-inject)
            hookStatusRepository.updateHookStatus(hooked) // <-- Panggil method Repository
        }

        // Implementasi method reportHookError() dari AIDL
        // Dipanggil oleh Xposed Module untuk melaporkan error.
        // Melaporkan error ke HookStatusRepository.
        @Throws(RemoteException::class)
        override fun reportHookError(message: String) {
            Timber.e("AIDL: reportHookError() called by client: $message")
            // Update error di HookStatusRepository (menggunakan Repository yang di-inject)
            hookStatusRepository.updateHookError(message) // <-- Panggil method Repository
        }

        // Implementasi method notifySystemCheck() dari AIDL
        @Throws(RemoteException::class)
        override fun notifySystemCheck() {
             Timber.d("AIDL: notifySystemCheck() called by client")
            // TODO: Logic saat Module melaporkan hasil cek sistem
        }

        // =====================================================================
        // Implementasi Metode getLatestGojekToken() dari AIDL (METODE BARU)
        // Dipanggil oleh komponen di aplikasi utama (misal, ViewModel/Activity)
        // untuk mendapatkan token Gojek dari Xposed Module (di proses Gojek).
        // =====================================================================
        @Throws(RemoteException::class) // Ini method Binder, jadi perlu RemoteException
        override fun getLatestGojekToken(): String? {
             Timber.d("AIDL: getLatestGojekToken() called by RoxGPS App.")
             // PENTING: Metode ini dipanggil DI PROSES RoxGPS!
             // Untuk mendapatkan token yang tersimpan di PROSES Gojek,
             // kita perlu memanggil BALIK ke metode static di Xposed Module
             // yang berjalan di proses Gojek menggunakan XposedHelpers.callStaticMethod.

             // Pastikan nama package Gojek dan nama kelas KampretTokenHooker serta nama method static-nya BENAR!
             val gojekPackageName = "com.gojek.app" // <-- Pastikan nama package Gojek yang BENAR
             val hookerClassNameInGojekProcess = KampretTokenHooker::class.java.name // Menggunakan reflection untuk nama kelas

             try {
                 // Gunakan XposedHelpers.callStaticMethod untuk memanggil static method di KampretTokenHooker
                 // yang berjalan di proses Gojek (Xposed Framework yang memfasilitasinya).
                 // Method yang dipanggil adalah getStoredToken() yang sudah kita buat di KampretTokenHooker.
                 val gojekToken = XposedHelpers.callStaticMethod(
                     XposedHelpers.findClass(hookerClassNameInGojekProcess, this::class.java.classLoader), // Cari kelas KampretTokenHooker menggunakan ClassLoader RoxGPS App
                     "getStoredToken" // Nama method static di KampretTokenHooker yang akan dipanggil
                 ) as? String // Cast hasil kembalian ke String?

                 Timber.d("AIDL: getLatestGojekToken() retrieved token: ${gojekToken?.take(5)}...") // Log beberapa karakter awal token
                 return gojekToken // Kembalikan token yang didapat dari proses Gojek

             } catch (e: XposedHelpers.FindClassException) {
                 Timber.e(e, "AIDL: Failed to find KampretTokenHooker class in Gojek process.")
                 // Ini bisa terjadi jika module tidak aktif di package Gojek, nama kelas salah, dll.
                 // Laporkan error ke Repository (jika perlu)
                 hookStatusRepository.reportHookError("AIDL Error: Cannot find KampretTokenHooker class in Gojek process.")
                 throw RemoteException("Failed to get token: Hooker class not found.") // Lemparkan exception AIDL
             } catch (e: NoSuchMethodError) {
                 Timber.e(e, "AIDL: Failed to find getStoredToken method in KampretTokenHooker.")
                 // Nama method static salah
                 hookStatusRepository.reportHookError("AIDL Error: Cannot find getStoredToken method in KampretTokenHooker.")
                 throw RemoteException("Failed to get token: Hooker method not found.") // Lemparkan exception AIDL
             } catch (e: Throwable) { // Tangkap Throwable umum untuk safety
                 Timber.e(e, "AIDL: Unexpected error calling getStoredToken via XposedHelpers.")
                 hookStatusRepository.reportHookError("AIDL Error: Unexpected error getting token: ${e.message}")
                 throw RemoteException("Failed to get token: ${e.message}") // Lemparkan exception AIDL
             }
             // Catatan: this::class.java.classLoader mendapatkan ClassLoader dari Service RoxGPS itu sendiri,
             // bukan ClassLoader Gojek. XposedHelpers.callStaticMethod secara internal akan menemukan
             // ClassLoader yang tepat untuk menjalankan method static tersebut di proses target (Gojek).
        }

    }

    // =====================================================================
    // Metode Lifecycle Service
    // =====================================================================

    override fun onCreate() {
        super.onCreate()
        Timber.d("RoxGpsAidlService created")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("RoxGpsAidlService onBind() called for intent: ${intent?.action}")
        // Pastikan intent action-nya sesuai dengan NAMA INTERFACE AIDL dan Manifest.
        // Nama interface AIDL kita adalah com.roxgps.ipc.IRoxGpsService
        return if (intent?.action == IRoxGpsService::class.java.name) { // <-- Gunakan nama IRoxGpsService
            binder // Mengembalikan objek Binder (implementasi AIDL kita)
        } else {
            Timber.w("RoxGpsAidlService received unexpected bind intent action: ${intent?.action}")
            null // Tolak binding jika action tidak sesuai
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("RoxGpsAidlService destroyed")
        // Opsi: Laporkan ke module bahwa service akan mati?
        // runCatching { hookStatusRepository.reportHookError("RoxGPS AIDL Service is shutting down.") }
    }

    // Metode Internal Service (Getter untuk state internal DIHAPUS)

}
