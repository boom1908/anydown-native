package com.boom.anydown.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object CrashLogger {
    private const val TAG = "ANYDOWN_CRASH"
    private const val MAX_LOG_BYTES = 200 * 1024
    private const val FATAL_WRITE_TIMEOUT_MS = 500L
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "anydown-crash-log").apply { isDaemon = true }
    }
    private val handlerInstalled = AtomicBoolean(false)
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            logFile = File(context.applicationContext.filesDir, "anydown_log.txt")
        }
        writer.execute { synchronized(lock) { trimIfNeeded(logFile) } }

        if (handlerInstalled.compareAndSet(false, true)) {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    writer.submit { writeNow("FATAL: ${throwable.stackTraceToString()}") }
                        .get(FATAL_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }.onFailure { Log.e(TAG, "Could not persist fatal crash log", it) }
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun log(msg: String) {
        writer.execute { writeNow(msg) }
    }

    /** Returns the file contents newest first for the in-app debug viewer. */
    suspend fun readLogs(): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = logFile
            if (file == null || !file.exists()) return@synchronized "No logs yet."
            val lines = runCatching { file.readLines() }.getOrElse {
                return@synchronized "Unable to read logs."
            }
            lines.asReversed().joinToString("\n").ifBlank { "No logs yet." }
        }
    }

    fun clear() {
        writer.execute {
            synchronized(lock) {
                logFile?.let { file ->
                    runCatching { file.writeText("") }
                        .onFailure { Log.e(TAG, "Could not clear crash log", it) }
                }
            }
        }
    }

    private fun writeNow(msg: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$timestamp] $msg\n"
        synchronized(lock) {
            val file = logFile
            if (file == null) {
                Log.e(TAG, msg)
                return
            }
            runCatching {
                trimForIncomingEntry(file, entry.toByteArray(Charsets.UTF_8).size)
                file.appendText(entry)
            }.onFailure { Log.e(TAG, "Could not write crash log", it) }
        }
    }

    private fun trimIfNeeded(file: File?) {
        if (file == null || !file.exists() || file.length() <= MAX_LOG_BYTES) return
        retainNewestBytes(file, MAX_LOG_BYTES)
    }

    private fun trimForIncomingEntry(file: File, incomingBytes: Int) {
        if (!file.exists() || file.length() + incomingBytes <= MAX_LOG_BYTES) return
        retainNewestBytes(file, (MAX_LOG_BYTES - incomingBytes).coerceAtLeast(0))
    }

    private fun retainNewestBytes(file: File, targetBytes: Int) {
        if (targetBytes == 0) {
            file.writeText("")
            return
        }
        val bytes = file.readBytes()
        val start = (bytes.size - targetBytes).coerceAtLeast(0)
        var firstCompleteLine = start
        while (firstCompleteLine < bytes.size && bytes[firstCompleteLine] != '\n'.code.toByte()) {
            firstCompleteLine++
        }
        if (firstCompleteLine < bytes.size) firstCompleteLine++
        file.writeBytes(bytes.copyOfRange(firstCompleteLine, bytes.size))
    }
}
