package com.boom.anydown

import android.content.Context
import android.system.Os
import java.io.File

fun getFfmpegBinDir(context: Context): String {
    val binDir = File(context.filesDir, "bin")
    if (!binDir.exists()) binDir.mkdirs()

    val nativeLibDir = context.applicationInfo.nativeLibraryDir
    val ffmpegLink = File(binDir, "ffmpeg")
    val ffprobeLink = File(binDir, "ffprobe")

    if (!ffmpegLink.exists()) Os.symlink("$nativeLibDir/libffmpeg.so", ffmpegLink.absolutePath)
    if (!ffprobeLink.exists()) Os.symlink("$nativeLibDir/libffprobe.so", ffprobeLink.absolutePath)

    return binDir.absolutePath
}
