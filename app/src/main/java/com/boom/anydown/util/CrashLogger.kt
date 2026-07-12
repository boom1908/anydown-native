package com.boom.anydown.util
import android.content.Context
import android.util.Log
object CrashLogger {
    fun init(context: Context) {}
    fun log(msg: String) { Log.e("ANYDOWN_CRASH", msg) }
}
