package io.github.jqssun.gpssetter.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object FileLogger {
    private const val LOG_TAG = "FileLogger"
    private const val LOG_FOLDER = "RoxGPS/log"
    private var logFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        try {
            // Buat direktori RoxGPS/log di internal storage
            val logDir = File(context.filesDir, LOG_FOLDER).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

            // Buat file log dengan format nama: log_YYYY_MM_DD.txt
            val dateFormat = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault())
            val fileName = "log_${dateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)

            // Tulis header jika file baru dibuat
            if (!logFile!!.exists()) {
                writeHeader()
            }

            isInitialized = true
            log("Logger diinisialisasi. File log: ${logFile?.absolutePath}", LOG_TAG, "I")
            
            // Bersihkan log lama
            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menginisialisasi logger: ${e.message}", e)
        }
    }

    private fun writeHeader() {
        try {
            FileWriter(logFile!!, true).use { fw ->
                fw.append("=== RoxGPS Log File ===\n")
                fw.append("Created: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                fw.append("=======================\n\n")
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Gagal menulis header log: ${e.message}")
        }
    }

    @Synchronized
    fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        val logMessage = "$timestamp [$level] [$tag] $message\n"

        // Selalu log ke Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }

        // Tulis ke file hanya jika sudah diinisialisasi
        if (isInitialized) {
            try {
                logFile?.let {
                    FileWriter(it, true).use { fw ->
                        fw.append(logMessage)
                    }
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Gagal menulis log ke file: ${e.message}")
            }
        }
    }

    /**
     * Membersihkan file log yang lebih lama dari 7 hari
     */
    private fun cleanOldLogs(logDir: File) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        logpackage io.github.jqssun.gpssetter.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object FileLogger {
    private const val LOG_TAG = "FileLogger"
    private const val LOG_FOLDER = "RoxGPS/log"
    private var logFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        try {
            // Buat direktori RoxGPS/log di internal storage
            val logDir = File(context.filesDir, LOG_FOLDER).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

            // Buat file log dengan format nama: log_YYYY_MM_DD.txt
            val dateFormat = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault())
            val fileName = "log_${dateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)

            // Tulis header jika file baru dibuat
            if (!logFile!!.exists()) {
                writeHeader()
            }

            isInitialized = true
            log("Logger diinisialisasi. File log: ${logFile?.absolutePath}", LOG_TAG, "I")
            
            // Bersihkan log lama
            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menginisialisasi logger: ${e.message}", e)
        }
    }

    private fun writeHeader() {
        try {
            FileWriter(logFile!!, true).use { fw ->
                fw.append("=== RoxGPS Log File ===\n")
                fw.append("Created: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                fw.append("=======================\n\n")
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Gagal menulis header log: ${e.message}")
        }
    }

    @Synchronized
    fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        val logMessage = "$timestamp [$level] [$tag] $message\n"

        // Selalu log ke Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }

        // Tulis ke file hanya jika sudah diinisialisasi
        if (isInitialized) {
            try {
                logFile?.let {
                    FileWriter(it, true).use { fw ->
                        fw.append(logMessage)
                    }
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Gagal menulis log ke file: ${e.message}")
            }
        }
    }

    /**
     * Membersihkan file log yang lebih lama dari 7 hari
     */
    private fun cleanOldLogs(logDir: File) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        log("File log lama dihapus: ${file.name}", LOG_TAG, "I")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal membersihkan log lama: ${e.message}")
        }
    }

    /**
     * Mendapatkan semua file log yang tersedia
     */
    fun getLogFiles(): List<File> {
        return logFile?.parentFile?.listFiles()?.filter { 
            it.name.startsWith("log_") && it.name.endsWith(".txt") 
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Mendapatkan konten file log tertentu
     */
    fun getLogContent(file: File): String {
        return try {
            file.readText()
        } catch (e: IOException) {
            "Gagal membaca file log: ${e.message}"
        }
    }

    /**
     * Menghapus semua file log
     */
    fun clearAllLogs() {
        try {
            logFile?.parentFile?.listFiles()?.forEach { file ->
                if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                    file.delete()
                }
            }
            log("Semua file log dibersihkan", LOG_TAG, "I")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menghapus semua log: ${e.message}")
        }
    }

    /**
     * Mengecek ukuran total folder log
     */
    fun getLogFolderSize(): Long {
        var size = 0L
        logFile?.parentFile?.listFiles()?.forEach { file ->
            size += file.length()
        }
        return size
    }

    /**
     * Mendapatkan path folder log
     */
    fun getLogFolderPath(): String {
        return logFile?.parentFile?.absolutePath ?: ""
    }
}("File log lama dihapus: ${file.name}", LOG_TAG, "I")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal membersihkan log lama: ${e.message}")
        }
    }

    /**
     * Mendapatkan semua file log yang tersedia
     */
    fun getLogFiles(): List<File> {
        return logFile?.parentFile?.listFiles()?.filter { 
            it.name.startsWith("log_") && it.name.endsWith(".txt") 
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Mendapatkan konten file log tertentu
     */
    fun getLogContent(file: File): String {
        return try {
            file.readText()
        } catch (e: IOException) {
            "Gagal membaca file log: ${e.message}"
        }
    }

    /**
     * Menghapus semua file log
     */
    fun clearAllLogs() {
        try {
            logFile?.parentFile?.listFiles()?.forEach { file ->
                if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                    file.delete()
                }
            }
            log("Semua file log dibersihkan", LOG_TAG, "I")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menghapus semua log: ${e.message}")
        }
    }

    /**
     * Mengecek ukuran total folder log
     */
    fun getLogFolderSize(): Long {
        var size = 0L
        logFile?.parentFile?.listFiles()?.forEach { file ->
     package io.github.jqssun.gpssetter.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object FileLogger {
    private const val LOG_TAG = "FileLogger"
    private const val LOG_FOLDER = "RoxGPS/log"
    private var logFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        try {
            // Buat direktori RoxGPS/log di internal storage
            val logDir = File(context.filesDir, LOG_FOLDER).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

            // Buat file log dengan format nama: log_YYYY_MM_DD.txt
            val dateFormat = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault())
            val fileName = "log_${dateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)

            // Tulis header jika file baru dibuat
            if (!logFile!!.exists()) {
                writeHeader()
            }

            isInitialized = true
            log("Logger diinisialisasi. File log: ${logFile?.absolutePath}", LOG_TAG, "I")
            
            // Bersihkan log lama
            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menginisialisasi logger: ${e.message}", e)
        }
    }

    private fun writeHeader() {
        try {
            FileWriter(logFile!!, true).use { fw ->
                fw.append("=== RoxGPS Log File ===\n")
                fw.append("Created: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                fw.append("=======================\n\n")
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Gagal menulis header log: ${e.message}")
        }
    }

    @Synchronized
    fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        val logMessage = "$timestamp [$level] [$tag] $message\n"

        // Selalu log ke Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }

        // Tulis ke file hanya jika sudah diinisialisasi
        if (isInitialized) {
            try {
                logFile?.let {
                    FileWriter(it, true).use { fw ->
                        fw.append(logMessage)
                    }
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Gagal menulis log ke file: ${e.message}")
            }
        }
    }

    /**
     * Membersihkan file log yang lebih lama dari 7 hari
     */
    private fun cleanOldLogs(logDir: File) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        logpackage io.github.jqssun.gpssetter.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object FileLogger {
    private const val LOG_TAG = "FileLogger"
    private const val LOG_FOLDER = "RoxGPS/log"
    private var logFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        try {
            // Buat direktori RoxGPS/log di internal storage
            val logDir = File(context.filesDir, LOG_FOLDER).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

            // Buat file log dengan format nama: log_YYYY_MM_DD.txt
            val dateFormat = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault())
            val fileName = "log_${dateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)

            // Tulis header jika file baru dibuat
            if (!logFile!!.exists()) {
                writeHeader()
            }

            isInitialized = true
            log("Logger diinisialisasi. File log: ${logFile?.absolutePath}", LOG_TAG, "I")
            
            // Bersihkan log lama
            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menginisialisasi logger: ${e.message}", e)
        }
    }

    private fun writeHeader() {
        try {
            FileWriter(logFile!!, true).use { fw ->
                fw.append("=== RoxGPS Log File ===\n")
                fw.append("Created: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                fw.append("=======================\n\n")
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Gagal menulis header log: ${e.message}")
        }
    }

    @Synchronized
    fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        val logMessage = "$timestamp [$level] [$tag] $message\n"

        // Selalu log ke Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }

        // Tulis ke file hanya jika sudah diinisialisasi
        if (isInitialized) {
            try {
                logFile?.let {
                    FileWriter(it, true).use { fw ->
                        fw.append(logMessage)
                    }
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Gagal menulis log ke file: ${e.message}")
            }
        }
    }

    /**
     * Membersihkan file log yang lebih lama dari 7 hari
     */
    private fun cleanOldLogs(logDir: File) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        log("File log lama dihapus: ${file.name}", LOG_TAG, "I")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal membersihkan log lama: ${e.message}")
        }
    }

    /**
     * Mendapatkan semua file log yang tersedia
     */
    fun getLogFiles(): List<File> {
        return logFile?.parentFile?.listFiles()?.filter { 
            it.name.startsWith("log_") && it.name.endsWith(".txt") 
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Mendapatkan konten file log tertentu
     */
    fun getLogContent(file: File): String {
        return try {
            file.readText()
        } catch (e: IOException) {
            "Gagal membaca file log: ${e.message}"
        }
    }

    /**
     * Menghapus semua file log
     */
    fun clearAllLogs() {
        try {
            logFile?.parentFile?.listFiles()?.forEach { file ->
                if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                    file.delete()
                }
            }
            log("Semua file log dibersihkan", LOG_TAG, "I")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menghapus semua log: ${e.message}")
        }
    }

    /**
     * Mengecek ukuran total folder log
     */
    fun getLogFolderSize(): Long {
        var size = 0L
        logFile?.parentFile?.listFiles()?.forEach { file ->
            size += file.length()
        }
        return size
    }

    /**
     * Mendapatkan path folder log
     */
    fun getLogFolderPath(): String {
        return logFile?.parentFile?.absolutePath ?: ""
    }
}("File log lama dihapus: ${file.name}", LOG_TAG, "I")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal membersihkan log lama: ${e.message}")
        }
    }

    /**
     * Mendapatkan semua file log yang tersedia
     */
    fun getLogFiles(): List<File> {
        return logFile?.parentFile?.listFiles()?.filter { 
            it.name.startsWith("log_") && it.name.endsWith(".txt") 
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Mendapatkan konten file log tertentu
     */
    fun getLogContent(file: File): String {
        return try {
            file.readText()
        } catch (e: IOException) {
            "Gagal membaca file log: ${e.message}"
        }
    }

    /**
     * Menghapus semua file log
     */
    fun clearAllLogs() {
        try {
            logFile?.parentFile?.listFiles()?.forEach { file ->
                if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                    file.delete()
                }
            }
            log("Semua file log dibersihkan", LOG_TAG, "I")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Gagal menghapus semua log: ${e.message}")
        }
    }

    /**
     * Mengecek ukuran total folder log
     */
    fun getLogFolderSize(): Long {
        var size = 0L
        logFile?.parentFile?.listFiles()?.forEach { file ->
            size += file.length()
        }
        return size
    }

    /**
     * Mendapatkan path folder log
     */
    fun getLogFolderPath(): String {
        return logFile?.parentFile?.absolutePath ?: ""
    }
}       size += file.length()
        }
        return size
    }

    /**
     * Mendapatkan path folder log
     */
    fun getLogFolderPath(): String {
        return logFile?.parentFile?.absolutePath ?: ""
    }
}