package com.boom.anydown

import android.content.Context
import java.io.File
import java.io.FileOutputStream

fun getFfmpegBinary(context: Context): String {
    val binDir = File(context.filesDir, "bin")
    if (!binDir.exists()) binDir.mkdirs()

    val ffmpegFile = File(binDir, "ffmpeg")
    val ffprobeFile = File(binDir, "ffprobe")

    if (!ffmpegFile.exists()) {
        context.assets.open("ffmpeg").use { input ->
            FileOutputStream(ffmpegFile).use { output -> input.copyTo(output) }
        }
        ffmpegFile.setExecutable(true)
    }

    if (!ffprobeFile.exists()) {
        context.assets.open("ffprobe").use { input ->
            FileOutputStream(ffprobeFile).use { output -> input.copyTo(output) }
        }
        ffprobeFile.setExecutable(true)
    }

    // Return the directory containing both tools so yt-dlp can find them
    return binDir.absolutePath
}
