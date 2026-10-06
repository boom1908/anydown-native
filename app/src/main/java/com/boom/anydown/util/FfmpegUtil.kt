package com.boom.anydown.util

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

private val ffmpegLock = Any()

/**
 * Returns the direct executable path to libffmpeg.so.
 * On Android 10+ (API 29+), executing binaries inside /data/user/0/.../files is blocked
 * by SELinux W^X (noexec). The ONLY path on Android guaranteed to have native execution
 * permissions is context.applicationInfo.nativeLibraryDir (/data/app/.../lib/arm64).
 */
fun getFfmpegExecutablePath(context: Context): String {
    val nativeLibDir = context.applicationInfo.nativeLibraryDir
    val nativeFfmpeg = File(nativeLibDir, "libffmpeg.so")

    // Priority 1: Direct native library directory
    if (nativeFfmpeg.exists() && nativeFfmpeg.length() > 0L) {
        CrashLogger.log("FFMPEG UTIL: Using nativeLibraryDir executable (${nativeFfmpeg.absolutePath}, ${nativeFfmpeg.length()} bytes)")
        ensureBinDir(context, nativeFfmpeg.absolutePath)
        return nativeFfmpeg.absolutePath
    }

    // Priority 2: Extract from installed APK if package manager skipped extraction
    val binDir = File(context.filesDir, "bin")
    if (!binDir.exists()) binDir.mkdirs()
    val fallbackFile = File(binDir, "ffmpeg")

    if (fallbackFile.exists() && fallbackFile.length() > 0L && fallbackFile.canExecute()) {
        CrashLogger.log("FFMPEG UTIL: Using cached internal fallback ($fallbackFile)")
        return fallbackFile.absolutePath
    }

    extractFromApk(context, "libffmpeg.so", fallbackFile)
    return if (fallbackFile.exists()) fallbackFile.absolutePath else nativeFfmpeg.absolutePath
}

fun getFfmpegBinDir(context: Context): String {
    val execPath = getFfmpegExecutablePath(context)
    val execFile = File(execPath)
    ensureBinDir(context, execPath)
    return execFile.parentFile?.absolutePath ?: File(context.filesDir, "bin").absolutePath
}

private fun ensureBinDir(context: Context, sourcePath: String) {
    val binDir = File(context.filesDir, "bin")
    if (!binDir.exists()) binDir.mkdirs()
    val ffmpegDest = File(binDir, "ffmpeg")
    val ffprobeDest = File(binDir, "ffprobe")

    synchronized(ffmpegLock) {
        runCatching {
            if (!ffmpegDest.exists()) {
                try {
                    Os.symlink(sourcePath, ffmpegDest.absolutePath)
                } catch (_: Throwable) {
                    // Fall back to copy if symlink blocked
                    File(sourcePath).inputStream().use { input ->
                        FileOutputStream(ffmpegDest).use { output -> input.copyTo(output) }
                    }
                    ffmpegDest.setExecutable(true, false)
                }
            }
            if (!ffprobeDest.exists()) {
                try {
                    Os.symlink(sourcePath, ffprobeDest.absolutePath)
                } catch (_: Throwable) {
                    File(sourcePath).inputStream().use { input ->
                        FileOutputStream(ffprobeDest).use { output -> input.copyTo(output) }
                    }
                    ffprobeDest.setExecutable(true, false)
                }
            }
        }
    }
}

private fun extractFromApk(context: Context, libName: String, destFile: File): Boolean {
    val apkPath = context.applicationInfo.sourceDir ?: return false
    return runCatching {
        ZipFile(apkPath).use { zip ->
            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val abiCandidates = listOf(primaryAbi, "arm64-v8a", "x86_64", "armeabi-v7a", "x86")
            var foundEntry = zip.getEntry("lib/$primaryAbi/$libName")

            if (foundEntry == null) {
                for (abi in abiCandidates) {
                    foundEntry = zip.getEntry("lib/$abi/$libName")
                    if (foundEntry != null) break
                }
            }

            if (foundEntry == null) {
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name.endsWith(libName)) {
                        foundEntry = e
                        break
                    }
                }
            }

            if (foundEntry != null) {
                destFile.delete()
                zip.getInputStream(foundEntry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.setReadable(true, false)
                destFile.setExecutable(true, false)
                CrashLogger.log("FFMPEG UTIL: Extracted $libName from APK (${foundEntry.name}) -> $destFile")
                true
            } else {
                false
            }
        }
    }.getOrElse { err ->
        CrashLogger.log("FFMPEG UTIL: APK extraction error: ${err.message}")
        false
    }
}
