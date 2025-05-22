// File: com/roxgps/xposed/XposedHookManagerImpl.kt
package com.roxgps.xposed // Sesuaikan package hook kamu

// =====================================================================
// Import Library untuk XposedHookManagerImpl
// =====================================================================

// === Import Hilt ===
// ===================

// === Import Coroutine Flow ===
// =============================

// === Import Logging ===
// ======================


// Import Interface IXposedHookManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.roxgps.IRoxAidlService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


// =====================================================================
// XposedHookManagerImpl (Implementasi IXposedHookManager)
// =====================================================================

// Implementasi Singleton dari Manager Hook Xposed.
// Bertanggung jawab mengelola koneksi ke Service AIDL dan status hook.
@Singleton // Scope Singleton level Aplikasi
class XposedHookManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context // Context level Aplikasi di-inject
    // TODO: Inject dependensi lain jika diperlukan (misal CoroutineScope, Logger, Repository Status)
) : IXposedHookManager { // Implementasikan Interface IXposedHookManager

    private val TAG = "XposedHookManagerImpl" // TAG untuk logging

    // === State Internal (diimplementasikan dengan MutableStateFlow) ===

    // MutableStateFlow internal untuk menyimpan status hook saat ini (default NotActive)
    private val _hookStatus = MutableStateFlow<HookStatus>(HookStatus.NotActive)

    // StateFlow publik yang diekspos (read-only) untuk diamati oleh komponen lain (UI/ViewModel)
    override val hookStatus: StateFlow<HookStatus> = _hookStatus.asStateFlow() // <<< Implementasi properti hookStatus


    // === State untuk Binding Service AIDL ===

    // Referensi ke objek ServiceConnection
    private var serviceConnection: ServiceConnection? = null

    // Referensi ke AIDL binder (objek Remote dari Service)
    private var aidlService: IRoxAidlService? = null


    // === Implementasi Metode Mengontrol Hook Lifecycle ===

    /** Memulai proses binding ke Xposed Hook Service AIDL. */
    override fun startHookServiceBinding() {
        Timber.d("$TAG: startHookServiceBinding called.")

        // Jika ServiceConnection sudah ada (binding sedang berjalan atau sudah terhubung), lewati.
        if (serviceConnection != null) {
            Timber.d("$TAG: Service binding already initiated or active. Skipping.")
            return
        }

        // Buat ServiceConnection baru
        val connection = object : ServiceConnection {
            // Dipanggil saat koneksi ke Service berhasil (binder diterima)
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Timber.i("$TAG: AIDL Service connected: $name")
                // Cast binder menjadi tipe AIDL interface
                aidlService = IRoxAidlService.Stub.asInterface(service)
                // Laporkan status berhasil terhubung (BoundAndReady)
                _hookStatus.value = HookStatus.BoundAndReady // <<< Update status
                Timber.i("$TAG: HookStatus updated to BoundAndReady.")
                // TODO: Jika ada aksi yang perlu dilakukan setelah terhubung, panggil di sini (misal, kirim config awal jika perlu)

                // Optional: Panggil metode AIDL di Service untuk memberi tahu bahwa klien (Manager) sudah terhubung
                // aidlService?.setHookStatus(true) // Panggil metode di Service AIDL jika ada
            }

            // Dipanggil saat koneksi ke Service terputus secara tidak terduga
            override fun onServiceDisconnected(name: ComponentName?) {
                Timber.w("$TAG: AIDL Service disconnected: $name")
                // Bersihkan referensi AIDL binder dan ServiceConnection
                aidlService = null
                serviceConnection = null
                // Laporkan status terputus
                _hookStatus.value = HookStatus.NotActive // <<< Update status
                Timber.w("$TAG: HookStatus updated to NotActive (Disconnected).")
            }

            // Dipanggil saat binder dari Service null (misal, Service crash atau alasan lain)
            override fun onNullBinding(name: ComponentName?) {
                Timber.e("$TAG: AIDL Service onNullBinding: $name")
                // Bersihkan referensi
                aidlService = null
                serviceConnection = null
                // Laporkan status error atau tidak aktif
                _hookStatus.value = HookStatus.Error("Null binding from AIDL Service") // <<< Update status Error
                Timber.e("$TAG: HookStatus updated to Error (Null Binding).")
                super.onNullBinding(name) // Panggil implementasi default
            }

            // Dipanggil saat proses Service crash atau terhenti.
            override fun onBindingDied(name: ComponentName?) {
                Timber.e("$TAG: AIDL Service onBindingDied: $name")
                // Bersihkan referensi
                aidlService = null
                serviceConnection = null
                // Laporkan status error atau tidak aktif
                _hookStatus.value = HookStatus.Error("AIDL Service binding died") // <<< Update status Error
                Timber.e("$TAG: HookStatus updated to Error (Binding Died).")
                super.onBindingDied(name) // Panggil implementasi default
            }
        }

        // Simpan referensi ServiceConnection
        serviceConnection = connection
        // Laporkan status sedang mencoba binding
        _hookStatus.value = HookStatus.Binding // <<< Update status

        // Buat Intent untuk mengikat ke Service AIDL (RoxAidlService)
        val serviceIntent = Intent("com.roxgps.IRoxAidlService").apply { // Action Intent sesuai dengan AndroidManifest Service
            // Atau, gunakan ComponentName jika target package/class sudah pasti:
            // component = ComponentName(context.packageName, "com.roxgps.service.RoxAidlService") // Sesuaikan package/class Service kamu
            setPackage(context.packageName) // Penting untuk binding Intent implisit
        }

        // Mulai proses binding ke Service
        // Menggunakan context level Aplikasi
        val bound = context.bindService(
            serviceIntent,
            connection, // Gunakan ServiceConnection yang baru dibuat
            Context.BIND_AUTO_CREATE // Buat Service jika belum berjalan
        )

        if (bound) {
            Timber.i("$TAG: Successfully sent bindService intent for AIDL Service.")
        } else {
            Timber.e("$TAG: Failed to bind to AIDL Service.")
            // Jika bind gagal, langsung laporkan status error
            _hookStatus.value = HookStatus.Error("Failed to bind to AIDL Service") // <<< Update status Error
            serviceConnection = null // Bersihkan ServiceConnection jika gagal bind awal
        }
    }

    /** Menghentikan proses binding ke Xposed Hook Service AIDL. */
    override fun stopHookServiceBinding() {
        Timber.d("$TAG: stopHookServiceBinding called.")
        // Lepas binding Service jika ServiceConnection ada dan Service AIDL terhubung
        if (serviceConnection != null) {
            // Menggunakan context level Aplikasi untuk unbind
            context.unbindService(serviceConnection!!)
            Timber.i("$TAG: UnbindService called for AIDL Service.")
        }

        // Bersihkan referensi ServiceConnection dan AIDL binder
        aidlService = null
        serviceConnection = null

        // Laporkan status tidak aktif
        _hookStatus.value = HookStatus.NotActive // <<< Update status
        Timber.i("$TAG: HookStatus updated to NotActive.")
    }


    // === Implementasi Metode Melaporkan Status dari Hook (Dipanggil oleh RoxAidlService) ===
    // Metode ini dipanggil oleh Service AIDL untuk menerima update status dari Xposed Module.
    // Tugasnya hanya mengupdate StateFlow _hookStatus.

    /**
     * Melaporkan status koneksi hook (terhubung/tidak).
     * Dipanggil oleh RoxAidlService saat setHookStatus dari hook.
     * Mengupdate StateFlow [hookStatus].
     */
    override fun setHookConnected(connected: Boolean) { // <<< Implementasi metode laporan status
        Timber.d("$TAG: setHookConnected($connected) called by RoxAidlService.")
        // Update status berdasarkan laporan koneksi.
        // Jika terhubung, set status ke BoundAndReady (kecuali sudah ActiveFaking atau Error).
        // Jika tidak terhubung, set status ke NotActive.
        _hookStatus.value = if (connected) {
            // Jika laporan 'terhubung', set status ke BoundAndReady.
            // Hati-hati jika status saat ini sudah ActiveFaking. Jangan diturunkan.
            // Opsi: Jika saat ini NotActive atau Binding, set ke BoundAndReady. Jika sudah ActiveFaking/Error, biarkan.
            when(_hookStatus.value) {
                HookStatus.NotActive, HookStatus.Binding -> HookStatus.BoundAndReady
                else -> _hookStatus.value // Biarkan status saat ini
            }
        } else {
            // Jika laporan 'tidak terhubung', set status ke NotActive.
            HookStatus.NotActive
        }
        Timber.i("$TAG: HookStatus updated to ${_hookStatus.value}.")
    }

    /**
     * Melaporkan pesan error dari Xposed Hook Module.
     * Dipanggil oleh RoxAidlService saat reportHookError dari hook.
     * Mengupdate StateFlow [hookStatus].
     */
    override fun reportHookErrorFromHook(message: String) { // <<< Implementasi metode laporan status
        Timber.e("$TAG: reportHookErrorFromHook called by RoxAidlService: $message")
        // Set status ke Error
        _hookStatus.value = HookStatus.Error(message) // <<< Update status Error dengan pesan
        Timber.e("$TAG: HookStatus updated to Error: $message.")
    }

    /**
     * Melaporkan status cek sistem hook (misal, hook aktif/tidak, compatibility).
     * Dipanggil oleh RoxAidlService saat notifySystemCheck dari hook.
     * Mengupdate StateFlow [hookStatus].
     */
    override fun notifySystemCheckCompleted() { // <<< Implementasi metode laporan status
        Timber.d("$TAG: notifySystemCheckCompleted called by RoxAidlService.")
        // Ini menandakan cek sistem di hook selesai.
        // Jika status saat ini Binding, ini bisa berarti binding berhasil dan siap.
        // Update status ke BoundAndReady (jika belum ActiveFaking atau Error)
        when(_hookStatus.value) {
            HookStatus.Binding -> HookStatus.BoundAndReady // Jika sebelumnya Binding, sekarang BoundAndReady
            HookStatus.NotActive -> { // Jika sebelumnya NotActive, mungkin Service baru restart? Set ke BoundAndReady.
                _hookStatus.value = HookStatus.BoundAndReady
                Timber.i("$TAG: HookStatus updated to BoundAndReady after system check.")
            }
            else -> {
                // Jika status sudah BoundAndReady, ActiveFaking, atau Error, biarkan saja.
                Timber.d("$TAG: HookStatus remains ${_hookStatus.value} after system check.")
            }
        }
    }

    /**
     * Memberi tahu Xposed Hook Module (melalui Service AIDL) untuk mengaktifkan atau menonaktifkan mekanisme faking.
     * Dipanggil dari komponen aplikasi utama (misal, GoogleLocationHelperImpl saat start/stop faking).
     *
     * @param enabled True untuk mengaktifkan faking di hook, False untuk menonaktifkan.
     */
    override fun enableFakingMechanism(enable: Boolean) { // <<< IMPLEMENTASIKAN METODE INI
        Timber.d("$TAG: enableFakingMechanism($enable) called.")
        // Cek apakah binder AIDL sudah terhubung sebelum memanggil metode remote
        val service = aidlService
        if (service != null) {
            try {
                // Panggil metode di Service AIDL untuk memberi tahu hook
                // Asumsikan ada metode setFakingEnabled(Boolean) di IRoxAidlService.aidl
                service.setFakingEnabled(enable) // <<< Panggil metode di Service AIDL melalui binder
                Timber.i("$TAG: Called setFakingEnabled($enable) on AIDL Service.")
            } catch (e: RemoteException) {
                Timber.e(e, "$TAG: Failed to call setFakingEnabled($enable) on AIDL Service.")
                // Laporkan error ke status hook
                _hookStatus.value = HookStatus.Error("RemoteException calling setFakingEnabled: ${e.message}")
            }
        } else {
            Timber.w("$TAG: AIDL Service not connected. Cannot call setFakingEnabled($enable).")
            // Status hook mungkin belum BoundAndReady atau sudah terputus.
            // Error ini mungkin sudah dilaporkan oleh ServiceConnection callback atau metode pelaporan status lain.
        }
    }

    // Di dalam kelas XposedHookManagerImpl { ... }

    // ... (deklarasi StateFlow, ServiceConnection, aidlService, metode start/stop binding, dan enableFakingMechanism di atas) ...

    // === Implementasi Metode Melaporkan Status dari Hook (Dipanggil oleh RoxAidlService) ===
    // Metode ini dipanggil oleh Service AIDL untuk menerima update status dari Xposed Module.
    // Ini adalah metode 'utama' atau agregat untuk melaporkan status.

    /**
     * Melaporkan status koneksi dan fungsionalitas Xposed Hook.
     * Dipanggil oleh RoxAidlService.
     * Mengupdate StateFlow [hookStatus].
     *
     * @param status Objek HookStatus yang merepresentasikan status terbaru hook.
     */
    override fun reportHookStatus(status: HookStatus) { // <<< IMPLEMENTASIKAN METODE INI
        Timber.d("$TAG: reportHookStatus($status) called by RoxAidlService.")
        // Langsung update StateFlow _hookStatus dengan status yang diterima
        _hookStatus.value = status // <<< Update StateFlow dengan status dari parameter
        Timber.i("$TAG: HookStatus updated to $status.")

        // TODO: Jika diperlukan, tambahkan logika tambahan di sini
        //       Misal, jika status adalah Error(message), mungkin log error message ke tempat lain.
        if (status is HookStatus.Error) {
            Timber.e("$TAG: Received HookStatus.Error with message: ${status.message}")
            // Mungkin simpan pesan error ini di SharedFlow terpisah jika UI butuh menampilkan error detail.
            // _latestHookError.emit(status.message) // Jika properti latestHookError ada
        }
    }
    // TODO: Implementasikan properti lain jika perlu mengekspos informasi lain dari hook (misal pesan error terakhir)
    // val latestHookError: SharedFlow<String> = MutableSharedFlow() // Contoh properti error
    // Implementasi reportHookErrorFromHook akan memancarkan nilai ke SharedFlow ini.
}