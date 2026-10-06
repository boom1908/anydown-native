package com.boom.anydown

import android.content.Context

/**
 * Compatibility facade for older package-local callers. The file-backed
 * implementation lives in util.CrashLogger so the service, ViewModel, and UI
 * all read and write the same log.
 */
object CrashLogger {
    fun init(context: Context) = com.boom.anydown.util.CrashLogger.init(context)
    fun log(message: String) = com.boom.anydown.util.CrashLogger.log(message)
    suspend fun readLogs(): String = com.boom.anydown.util.CrashLogger.readLogs()
    fun clear() = com.boom.anydown.util.CrashLogger.clear()
}
