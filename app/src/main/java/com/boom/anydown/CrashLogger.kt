package com.boom.anydown

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private lateinit var logFile: File

    fun init(context: Context) {
        logFile = File(context.filesDir, "anydown_log.txt")
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            log("FATAL: ${throwable.stackTraceToString()}")
        }
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logFile.appendText("[$timestamp] $message\n")
    }

    fun readLogs(): String = if (logFile.exists()) logFile.readText() else "No logs yet."

    fun clear() {
        if (logFile.exists()) logFile.writeText("")
    }
}
