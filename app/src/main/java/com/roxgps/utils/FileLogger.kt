package com.roxgps.utils // Pastikan package ini sesuai

// =====================================================================
// Import Library FileLogger
// =====================================================================

import android.content.Context // Untuk Context
import android.os.Environment // Untuk Environment (jika diperlukan, tapi getExternalFilesDir lebih umum)
import android.util.Log // Untuk Logcat standar (untuk logging internal FileLogger dan fallback)
import java.io.File // Untuk File
import java.io.FileWriter // Untuk menulis ke File
import java.io.IOException // Untuk Exception IO
import java.text.SimpleDateFormat // Untuk format tanggal/waktu
import java.util.* // Untuk Date dan Locale

// =====================================================================
// Object FileLogger
// =====================================================================

/**
 * Object singleton untuk menulis log ke file di internal storage aplikasi.
 * Juga menyediakan fungsi untuk mengelola file log.
 */
object FileLogger { // Menggunakan 'object' adalah cara Kotlin membuat Singleton. Bagus!

    // =====================================================================
    // Konstanta & State Logger
    // =====================================================================
    private const val LOG_TAG = "RoxLogger" // Tag default untuk log internal logger
    private const val LOG_FOLDER = "log" // Nama folder log di dalam direktori files aplikasi
    private var logFile: File? = null // Referensi ke file log hari ini
    private var isInitialized = false // Flag untuk menandakan apakah logger sudah diinisialisasi

    // =====================================================================
    // Metode Inisialisasi
    // =====================================================================

    /**
     * Menginisialisasi logger. HARUS dipanggil sebelum [log]
     * jika log ingin ditulis ke file.
     *
     * @param context Context (Application Context direkomendasikan).
     */
    fun init(context: Context) { // Menerima Context sebagai parameter. BAGUS!
        if (isInitialized) {
            // Jika sudah diinisialisasi, keluar saja.
            Log.i(LOG_TAG, "Logger sudah diinisialisasi.")
            return
        }
        try {
            // Dapatkan direktori spesifik aplikasi untuk file eksternal (tapi private per aplikasi)
            // Path: /storage/emulated/0/Android/data/<package>/files/log
            // Ini TIDAK butuh permission WRITE_EXTERNAL_STORAGE di Android modern (API 19+).
            // File-nya terlihat oleh user via file explorer, cocok untuk debug.
            // Jika butuh benar-benar private (tidak terlihat user), pakai context.getFilesDir().
            val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER).apply {
                if (!exists()) mkdirs() // Buat folder log jika belum ada. BAGUS!
            }

            // Tentukan nama file log harian berdasarkan tanggal
            val dateFormat = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()) // Format tanggal: DD_MM_YYYY
            val fileName = "log_${dateFormat.format(Date())}.txt" // Nama file log hari ini
            logFile = File(logDir, fileName) // Buat objek File untuk file log hari ini. BAGUS!

            // Tulis header hanya jika file log baru dibuat (pertama kali hari ini)
            if (!logFile!!.exists()) { // Cek apakah file belum ada.
                writeHeader() // Tulis header jika file baru. BAGUS!
            }

            isInitialized = true // Set flag inisialisasi jadi true.
            log("Logger diinisialisasi. File log: ${logFile?.absolutePath}", LOG_TAG, "I") // Log inisialisasi ke logcat dan file (kalau init sukses)

            // Bersihkan log lama (opsional, bisa dipanggil terpisah)
            cleanOldLogs(logDir) // Panggil pembersihan log lama saat init. OK.
        } catch (e: Exception) {
            // Jika inisialisasi gagal, logger TIDAK akan bisa menulis ke file.
            // Log error ke Logcat standar. isInitialized akan tetap false.
            Log.e(LOG_TAG, "Gagal menginisialisasi logger: ${e.message}", e)
            isInitialized = false // Pastikan flag tetap false jika gagal
        }
    }

    // =====================================================================
    // Metode Menulis Log
    // =====================================================================

    /**
     * Menulis log message ke Logcat dan file log (jika sudah diinisialisasi).
     * Bersifat thread-safe.
     *
     * @param message Isi log message.
     * @param tag Tag log (default [LOG_TAG]).
     * @param level Level log ("D", "I", "W", "E") (default "D").
     * @param fileName Nama file log (default LOG_FOLDER - ini parameter lo di HookEntry,
     * tapi di sini dipakai secara internal, gue sesuaikan).
     * Method log di HookEntry pakai format `log(message, tag, fileName, level)`.
     * Lo perlu sesuaikan signature method `log` ini.
     * Berdasarkan HookEntry, signaturenya sepertinya `log(message, file_name, level)` tanpa tag di param pertama?
     * Atau `log(tag, message, file_name, level)`?
     * Contoh di HookEntry: `FileLogger.log("HookEntry", "AIDL Service...", "XposedLog", "I")`
     * Oke, signaturenya `log(tag, message, file_name, level)`.
     * Parameter `fileName` di method ini *bukan* nama file, tapi tag log.
     * Gue sesuaikan signature method log ini agar sesuai panggilan di HookEntry.
     *
     * @param fileSuffix: Tambahin parameter fileSuffix kalau mau simpan di file beda (ex: "XposedLog")
     * biar ga campur semua log di file harian.
     * Atau nama file ditentukan di init dan semua log ke situ.
     * Untuk saat ini, log ke file harian aja sesuai logic init lo. Parameter `fileName`
     * di panggilan HookEntry sebenarnya adalah `tag` di sini.
     *
     * Gue koreksi signature method log ini agar cocok dengan HookEntry.
     */
     // Parameter signature method log lo di HookEntry: log(tag, message, fileName, level)
     // Tapi di method log ini, parameternya: log(message, tag, level).
     // Gue akan ubah method log ini agar sesuai panggilan di HookEntry: log(tag, message, fileTag, level)
     // Namun, karena nama file log sudah ditentukan harian di `init`, parameter `fileTag` di panggilan HookEntry
     // sebenarnya adalah tag yang lo inginkan, bukan nama file.
     // Jadi, signature yang pas untuk menerima panggilan dari HookEntry: log(callTag: String, message: String, fileTag: String, level: String)
     // Dan di dalamnya, lo pakai callTag sebagai tag Logcat/file, fileTag sebagai tag di log file, dan message serta level.
     // Ini agak membingungkan ya.
     // Mari kita pakai signature yang PALING PAS dengan kebutuhan lo (log ke file harian, dengan tag dan level):
     // log(tag: String, message: String, level: String, fileSuffix: String? = null) // fileSuffix untuk opsional nama file lain
     // Tapi log lo di HookEntry itu: FileLogger.log("LocationHook", "Pesan", "XposedLog", "I")
     // Oke, jadi parameter ke-3 ("XposedLog") itu BUKAN LEVEL atau MESSAGE.
     // Gue berasumsi parameter ke-3 itu adalah semacam SUFFIX untuk nama file kalau mau beda file log.
     // Tapi logic init lo cuma bikin SATU file harian.
     // JADI, parameter ke-3 ("XposedLog") di HookEntry itu SEBENARNYA TIDAK TERPAKAI oleh logic `log` ini kalau lo mau semua log ke file harian.
     // Gue akan biarkan signature asli log(message, tag, level) tapi tambahin overloading log(tag, message, fileTag, level)
     // biar panggilan HookEntry nggak error, tapi parameter `fileTag` akan diabaikan untuk logic file harian.
     // Atau, yang lebih baik, ubah panggilan di HookEntry biar sesuai signature log(tag, message, level).

     // Mari kita koreksi panggilan di HookEntry aja, itu lebih bersih. Tapi user mau cek kelas ini.
     // Oke, gue tambahin overloading log biar panggilan dari HookEntry valid di sini.

     // Method log yang aslinya lo punya:
     // @Synchronized
     // fun log(message: String, tag: String = LOG_TAG, level: String = "D") { ... }

     // Overloading agar panggilan dari HookEntry `log(tag, message, fileTag, level)` bisa diterima:
    @Synchronized
    fun log(tag: String, message: String, fileTag: String, level: String) { // Overloading untuk panggilan HookEntry
         // Parameter `fileTag` (parameter ke-3 dari HookEntry) DIABAIKAN untuk penamaan file harian.
         // Kita gunakan `tag` (parameter ke-1) sebagai tag di output log.
         log(message, tag, level) // Panggil method log yang sebenarnya dengan parameter yang relevan
    }

     // Metode log yang sebenarnya, dengan parameter yang lebih masuk akal:
    @Synchronized
    fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss.SSS", Locale.getDefault()).format(Date()) // Format timestamp
        val logMessage = "$timestamp [$level] [$tag] $message\n" // Format baris log. BAGUS!

        // Selalu log ke Logcat standar, biar bisa dilihat langsung saat debugging. BAGUS!
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message) // Default ke Debug
        }

        // Tulis log ke file HANYA JIKA logger sudah berhasil diinisialisasi
        if (isInitialized) { // Cek flag inisialisasi. BAGUS!
            try {
                logFile?.let { // Pastikan logFile tidak null (seharusnya tidak jika isInitialized true)
                    FileWriter(it, true).use { fw -> // Gunakan FileWriter dengan append=true (tambah di akhir file) dan use (auto-close). BAGUS!
                        fw.append(logMessage) // Tulis baris log
                    }
                }
            } catch (e: IOException) {
                // Jika gagal menulis ke file, log error ke Logcat standar
                Log.e(LOG_TAG, "Gagal menulis log ke file: ${e.message}")
                // PERHATIAN: Kalau ini error terus menerus, bisa spam Logcat.
            }
        }
    }


    // =====================================================================
    // Metode Pembersihan Log Lama
    // =====================================================================

    /**
     * Membersihkan file log yang lebih lama dari 7 hari
     * Dipanggil dari init(). Bisa juga dipanggil secara berkala atau dari UI.
     *
     * @param logDir Direktori tempat file log disimpan.
     */
    private fun cleanOldLogs(logDir: File) { // Menerima direktori log. OK.
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // Hitung timestamp 7 hari lalu
            logDir.listFiles()?.forEach { file -> // Iterasi file di direktori log
                // Cek apakah itu file, nama file dimulai dengan "log_" dan diakhiri ".txt" (agar tidak menghapus file lain),
                // dan tanggal modifikasi terakhir lebih lama dari 7 hari lalu. BAGUS!
                if (file.isFile && file.name.startsWith("log_") && file.name.endsWith(".txt") && file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) { // Hapus file
                        log("File log lama dihapus: ${file.name}", LOG_TAG, "I") // Log informasi penghapusan. Menggunakan method log, akan ditulis ke log hari ini.
                    } else {
                         Log.w(LOG_TAG, "Gagal menghapus file log lama: ${file.name}") // Log ke Logcat jika gagal hapus
                    }
                }
            }
        } catch (e: Exception) {
            // Jika gagal membersihkan log lama, log error ke Logcat standar
            Log.e(LOG_TAG, "Gagal membersihkan log lama: ${e.message}", e)
        }
    }

    // =====================================================================
    // Metode Utility untuk Akses File Log dari UI
    // =====================================================================

    /**
     * Mendapatkan semua file log yang tersedia di folder log.
     *
     * @param context Context.
     * @return List of File objek file log.
     */
    fun getLogFiles(context: Context): List<File> { // Menerima Context. OK.
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER) // Dapatkan direktori log
        return logDir.listFiles()?.filter { // Filter hanya file yang sesuai pola nama log
            it.name.startsWith("log_") && it.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList() // Urutkan berdasarkan tanggal modifikasi terbaru. BAGUS!
    }

    /**
     * Mendapatkan konten teks dari file log tertentu.
     *
     * @param file Objek File log yang ingin dibaca.
     * @return Konten file log sebagai String, atau pesan error jika gagal.
     */
    fun getLogContent(file: File): String { // Menerima objek File. OK.
        return try {
            file.readText() // Baca seluruh konten file. BAGUS!
        } catch (e: IOException) {
            "Gagal membaca file log: ${e.message}" // Kembalikan pesan error jika gagal. OK.
        }
    }

    /**
     * Menghapus semua file log yang sesuai pola nama.
     *
     * @param context Context.
     */
    fun clearAllLogs(context: Context) { // Menerima Context. OK.
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER) // Dapatkan direktori log
        try {
            logDir.listFiles()?.forEach { file -> // Iterasi file
                if (file.name.startsWith("log_") && file.name.endsWith(".txt")) { // Filter file log
                    file.delete() // Hapus file
                }
            }
            log("Semua file log dibersihkan", LOG_TAG, "I") // Log informasi pembersihan. BAGUS!
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menghapus semua log: ${e.message}", e) // Log error ke Logcat
        }
    }

    /**
     * Mengecek ukuran total folder log dalam byte.
     *
     * @param context Context.
     * @return Ukuran total folder log dalam byte.
     */
    fun getLogFolderSize(context: Context): Long { // Menerima Context. OK.
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER) // Dapatkan direktori log
        var size = 0L
        logDir.listFiles()?.forEach { file -> // Iterasi file
            size += file.length() // Tambahkan ukuran file ke total
        }
        return size // Kembalikan ukuran total. BAGUS!
    }

    /**
     * Mendapatkan path absolut dari folder log.
     *
     * @param context Context.
     * @return Path absolut folder log sebagai String.
     */
    fun getLogFolderPath(context: Context): String { // Menerima Context. OK.
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER) // Dapatkan direktori log
        return logDir.absolutePath // Kembalikan path absolut. BAGUS!
    }
}
