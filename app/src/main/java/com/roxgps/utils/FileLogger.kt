package com.roxgps.utils

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Object singleton untuk menulis log ke file di internal storage aplikasi.
 * Menyediakan interface yang mirip dengan Timber untuk kemudahan penggunaan.
 */
object Relog {
    private const val LOG_TAG = "roxlogger"
    private const val LOG_FOLDER = "log"
    private var logFile: File? = null
    private var isInitialized = false

    /**
     * Menginisialisasi logger. HARUS dipanggil sebelum menggunakan fungsi logging.
     */
    fun init(context: Context) {
        if (isInitialized) {
            i("Logger sudah diinisialisasi.")
            return
        }
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER).apply {
                if (!exists()) mkdirs()
            }

            val dateFormat = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault())
            val fileName = "log_${dateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)

            if (!logFile!!.exists()) {
                writeHeader()
            }

            isInitialized = true
            i("Logger diinisialisasi. File log: ${logFile?.absolutePath}")

            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Timber.tag(LOG_TAG).e(e, "Gagal menginisialisasi logger: ${e.message}")
            isInitialized = false
        }
    }

    // =====================================================================
    // Timber-like Interface Methods
    // =====================================================================

    /**
     * Log error message (level E).
     */
    fun e(message: String, tag: String = LOG_TAG) = log(message, tag, "E")

    /**
     * Log error dengan throwable.
     */
    fun e(throwable: Throwable, message: String? = null, tag: String = LOG_TAG) {
        val errorMessage = message ?: throwable.message ?: "Unknown error"
        log("$errorMessage\n${throwable.stackTraceToString()}", tag, "E")
    }

    /**
     * Log warning message (level W).
     */
    fun w(message: String, tag: String = LOG_TAG) = log(message, tag, "W")

    /**
     * Log info message (level I).
     */
    fun i(message: String, tag: String = LOG_TAG) = log(message, tag, "I")

    /**
     * Log debug message (level D).
     */
    fun d(message: String, tag: String = LOG_TAG) = log(message, tag, "D")

    /**
     * Log verbose message (level V).
     */
    fun v(message: String, tag: String = LOG_TAG) = log(message, tag, "V")

    // Overloaded methods for supporting old implementation calls
    @Synchronized
    fun log(tag: String, message: String, fileTag: String, level: String) {
        log(message, tag, level)
    }

    // =====================================================================
    // Internal Implementation
    // =====================================================================

    @Synchronized
    private fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "$timestamp [$level] [$tag] $message\n"

        when (level) {
            "E" -> Timber.tag(tag).e(message)
            "W" -> Timber.tag(tag).w(message)
            "I" -> Timber.tag(tag).i(message)
            "V" -> Timber.tag(tag).v(message)
            else -> Timber.tag(tag).d(message)
        }

        if (isInitialized) {
            try {
                logFile?.let {
                    FileWriter(it, true).use { fw ->
                        fw.append(logMessage)
                    }
                }
            } catch (e: IOException) {
                Timber.tag(LOG_TAG).e("Gagal menulis log ke file: ${e.message}")
            }
        }
    }

    private fun writeHeader() {
        try {
            logFile?.let {
                FileWriter(it, true).use { fw ->
                    fw.append("=== Log Started at ${Date()} ===\n")
                }
            }
        } catch (e: IOException) {
            Timber.tag(LOG_TAG).e("Gagal menulis header log: ${e.message}")
        }
    }

    private fun cleanOldLogs(logDir: File) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("log_") &&
                    file.name.endsWith(".txt") && file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        i("File log lama dihapus: ${file.name}")
                    } else {
                        w("Gagal menghapus file log lama: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            e(e, "Gagal membersihkan log lama")
        }
    }

    // =====================================================================
    // Utility Methods
    // =====================================================================

    fun getLogFiles(context: Context): List<File> {
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER)
        return logDir.listFiles()?.filter {
            it.name.startsWith("log_") && it.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getLogContent(file: File): String {
        return try {
            file.readText()
        } catch (e: IOException) {
            e(e, "Gagal membaca file log")
            "Gagal membaca file log: ${e.message}"
        }
    }

    fun clearAllLogs(context: Context) {
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER)
        try {
            logDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                    file.delete()
                }
            }
            i("Semua file log dibersihkan")
        } catch (e: Exception) {
            e(e, "Gagal menghapus semua log")
        }
    }

    fun getLogFolderSize(context: Context): Long {
        val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER)
        return logDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getLogFolderPath(context: Context): String {
        return File(context.getExternalFilesDir(null), LOG_FOLDER).absolutePath
    }
}