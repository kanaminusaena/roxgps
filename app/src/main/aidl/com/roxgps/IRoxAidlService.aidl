package com.roxgps;

// Deklarasikan parcelable untuk FakeLocationData
parcelable com.roxgps.data.FakeLocationData;

interface IRoxAidlService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */

    /**
     * Mengembalikan lokasi palsu terbaru.
     */
    com.roxgps.data.FakeLocationData getLatestFakeLocation();

    /**
     * Mengatur status hook.
     */
    void setHookStatus(boolean hooked);

    /**
     * Melaporkan error hook.
     */
    void reportHookError(String message);

    /**
     * Memberitahu pemeriksaan sistem.
     */
    void notifySystemCheck();

    /**
     * Mendapatkan token terbaru.
     */
    String getLatestToken();

    /**
     * Mengirim setting konfigurasi terbaru dari aplikasi utama ke Service AIDL.
     * Service akan menyimpan setting ini dan menyediakannya untuk LocationHook.
     *
     * @param enabled Status faking yang diinginkan
     */
    void setFakingEnabled(boolean enabled);

    /**
     * Update lokasi palsu terbaru ke Service
     * @param location Data lokasi palsu yang akan digunakan
     */
    void updateFakeLocation(in com.roxgps.data.FakeLocationData location);

    /**
     * Health check untuk memverifikasi bahwa Service AIDL masih berjalan dan responsif.
     *
     * @return true jika Service berfungsi normal
     *         false jika ada masalah dengan Service
     * @throws RemoteException jika terjadi error komunikasi dengan Service
     */
    boolean ping();
}