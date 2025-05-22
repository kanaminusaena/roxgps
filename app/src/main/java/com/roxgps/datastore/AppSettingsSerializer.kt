// File: com/roxgps/datastore/AppSettingsSerializer.kt
package com.roxgps.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettings> { // Menggunakan AppSettings dari .proto

    override val defaultValue: AppSettings = AppSettings.getDefaultInstance()
    // Atau jika Anda ingin default yang lebih spesifik, bangun seperti ini:
    /*
    override val defaultValue: AppSettings = AppSettings.newBuilder().apply {
        isFakingStarted = false
        currentLatitude = 0.0f
        currentLongitude = 0.0f
        isSystemHooked = false
        isRandomPositionEnabled = false
        accuracyLevelSetting = ""
        randomPositionRangeMeters = 50
        updateIntervalMs = 1000L
        simulatedSpeedMps = 0.0f
        simulatedBearingDegrees = 0.0f
        simulatedAltitudeMeters = 0.0
        simulatedProviderName = ""
        mapType = 0
        darkThemeMode = 0
        isJoystickControlEnabled = false
        isUpdateCheckDisabled = false
    }.build()
    */


    override suspend fun readFrom(input: InputStream): AppSettings {
        try {
            // INI ADALAH PERBAIKAN UTAMA untuk readFrom:
            // Anda harus membaca dan mem-parse InputStream ke objek Protobuf Anda.
            // Gunakan metode parseFrom() yang disediakan oleh kelas Protobuf yang dihasilkan.
            return AppSettings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            // Jika ada masalah parsing (file rusak, dll.), lempar CorruptionException.
            // Ini akan memicu CorruptionHandler yang Anda definisikan di DataStore.
            throw CorruptionException("Cannot read AppSettings proto.", exception)
        }
        // Hapus blok catch (Exception) yang mengembalikan defaultValue,
        // karena itu membypass CorruptionHandler. Biarkan CorruptionException yang dilempar.
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        // PERBAIKAN untuk writeTo:
        // Pola standar adalah memanggil .writeTo(output) langsung pada objek Protobuf.
        // Jika 't' adalah objek Protobuf yang sudah dibangun, ini lebih efisien.
        t.writeTo(output)

        // output.flush() tidak selalu diperlukan di sini karena DataStore menanganinya,
        // tetapi tidak ada salahnya jika Anda ingin memaksanya.
    }

}