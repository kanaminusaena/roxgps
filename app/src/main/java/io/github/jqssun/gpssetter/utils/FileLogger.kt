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
    private const val LOG_FILE_NAME = "gps_setter_log.txt"
    private var logFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        logFile = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
        isInitialized = true
        log("Logger initialized. Log file: ${logFile?.absolutePath}", LOG_TAG, "I")
    }

    @Synchronized
    fun log(message: String, tag: String = LOG_TAG, level: String = "D") {
        val timestamp = SimpleDateFormat("dd-MM-yyyy | HH:mm:ss", Locale.getDefault())
            .format(Date())
        val logMessage = "$timestamp [$level] [$tag] $message\n"

        // Always log to Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }

        // Write to file only if initialized
        if (isInitialized) {
            try {
                logFile?.let {
                    FileWriter(it, true).use { fw ->
                        fw.append(logMessage)
                    }
                }
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Failed to write log to file: ${e.message}")
            }
        }
    }
}