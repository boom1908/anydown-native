package com.boom.anydown.util

import android.content.Context
import android.system.Os
import java.io.File

private val ffmpegLock = Any()

fun getFfmpegBinDir(context: Context): String {
    val binDir = File(context.filesDir, "bin")
    if (!binDir.exists()) binDir.mkdirs()
    val nativeLibDir = context.applicationInfo.nativeLibraryDir

    synchronized(ffmpegLock) {
        ensureSymlink(File(binDir, "ffmpeg"), "$nativeLibDir/libffmpeg.so")
        ensureSymlink(File(binDir, "ffprobe"), "$nativeLibDir/libffprobe.so")
    }
    return binDir.absolutePath
}

private fun ensureSymlink(link: File, target: String) {
    val current = if (link.exists()) runCatching { Os.readlink(link.absolutePath) }.getOrNull() else null
    if (current == target) return  // already correct — skip delete/recreate entirely
    if (link.exists()) link.delete()
    Os.symlink(target, link.absolutePath)
}
