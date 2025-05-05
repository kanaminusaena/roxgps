// Di RoxGPS App: app/src/main/aidl/com/roxgps/ipc/ILokasiPalsuService.aidl
package com.roxgps.ipc; // Ini package untuk file AIDL, harus sama di kedua project

// Jika butuh kirim/terima data custom yang kompleks, datanya harus Parcelable.
// Definisikan Parcelable di file .aidl terpisah
import com.roxgps.data.FakeLocationData; // Contoh: import Parcelable

interface ILokasiPalsuService {
    // Method yang bisa dipanggil Xposed Module ke Service RoxGPS:

    // Module panggil ini untuk mendapatkan lokasi palsu terakhir
    FakeLocationData getLatestFakeLocation(); // Contoh jika butuh tipe data custom Parcelable
    double[] getLatestFakeLocation(); // Contoh sederhana: kembalikan array double [lat, lon]

    // Module panggil ini untuk melaporkan status hook (berhasil/gagal)
    void setHookStatus(boolean hooked);

    // Module panggil ini untuk melaporkan error yang terjadi di sisi hook
    void reportHookError(String message);

    // Method lain sesuai kebutuhan komunikasi Module -> App
    void notifySystemCheck(); // Contoh: lapor kalau sistem deteksi sesuatu
    
     // =====================================================================
    // Metode Baru untuk RoxGPS App Meminta Token dari Module
    // =====================================================================
    // RoxGPS App (Service AIDL) panggil ini untuk mendapatkan token Gojek terakhir
    String getLatestGojekToken(); // <-- METHOD BARU! Mengembalikan String (token)
}
