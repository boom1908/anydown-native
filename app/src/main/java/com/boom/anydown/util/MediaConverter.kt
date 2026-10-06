package com.boom.anydown.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Converts a picked local file with the same ffmpeg binary used by downloads.
 * The work is deliberately outside the network queue: it is an in-screen tool,
 * not a background download.
 */
fun convertToMp3(context: Context, sourceUri: Uri): String {
    val metadata = querySourceMetadata(context, sourceUri)
    val baseName = metadata.displayName
        ?.substringBeforeLast('.', missingDelimiterValue = "converted")
        ?.ifBlank { "converted" }
        ?: "converted"
    val safeName = baseName.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim()
        .ifBlank { "converted" }
    val input = try {
        File.createTempFile("anydown-convert-", ".input", context.cacheDir)
    } catch (error: Throwable) {
        CrashLogger.log(
            "MP3 CONVERSION TEMP INPUT FAILED: uri=$sourceUri name=${metadata.displayName} " +
                "mime=${metadata.mimeType} error=${error.stackTraceToString()}"
        )
        throw IllegalStateException("Anydown couldn't prepare that file for conversion.", error)
    }
    val output = File(context.cacheDir, "$safeName.mp3")

    try {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                input.outputStream().use { outputStream -> stream.copyTo(outputStream) }
            } ?: throw IllegalStateException("Anydown couldn't open that file.")
        } catch (error: Throwable) {
            CrashLogger.log(
                "MP3 CONVERSION INPUT FAILED: uri=$sourceUri name=${metadata.displayName} " +
                    "mime=${metadata.mimeType} error=${error.stackTraceToString()}"
            )
            throw IllegalStateException("Anydown couldn't open that file.", error)
        }

        output.delete()
        val ffmpeg = File(getFfmpegExecutablePath(context))
        val process = try {
            val pb = ProcessBuilder(
                ffmpeg.absolutePath,
                "-y",
                "-i", input.absolutePath,
                "-vn",
                "-codec:a", "libmp3lame",
                "-q:a", "2",
                output.absolutePath
            ).redirectErrorStream(true)
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val binDir = File(context.filesDir, "bin").absolutePath
            pb.environment()["LD_LIBRARY_PATH"] = "$nativeLibDir:$binDir:/system/lib64"
            pb.start()
        } catch (error: Throwable) {
            CrashLogger.log(
                "MP3 CONVERSION PROCESS START FAILED: uri=$sourceUri name=${metadata.displayName} " +
                    "mime=${metadata.mimeType} executable=${ffmpeg.absolutePath} " +
                    "exists=${ffmpeg.exists()} canExecute=${ffmpeg.canExecute()} " +
                    "error=${error.stackTraceToString()}"
            )
            throw IllegalStateException("Anydown couldn't start the audio converter.", error)
        }
        val outputLog: String
        val exitCode: Int
        try {
            outputLog = process.inputStream.bufferedReader().use { it.readText() }
            exitCode = process.waitFor()
        } catch (error: Throwable) {
            CrashLogger.log(
                "MP3 CONVERSION PROCESS READ FAILED: uri=$sourceUri name=${metadata.displayName} " +
                    "mime=${metadata.mimeType} error=${error.stackTraceToString()}"
            )
            throw IllegalStateException("Anydown couldn't finish reading the converter output.", error)
        }
        if (exitCode != 0 || !output.exists() || output.length() == 0L) {
            CrashLogger.log(
                "MP3 CONVERSION FFMPEG FAILED: uri=$sourceUri name=${metadata.displayName} " +
                    "mime=${metadata.mimeType} exitCode=$exitCode outputExists=${output.exists()} " +
                    "outputBytes=${if (output.exists()) output.length() else 0} " +
                    "ffmpegOutput=$outputLog"
            )
            throw IllegalStateException(
                if (outputLog.contains("does not contain any stream", ignoreCase = true)) {
                    "That file does not contain an audio track."
                } else {
                    "This file couldn't be converted. It may be damaged or use an unsupported format."
                }
            )
        }
        return try {
            saveToDownloads(context, output, "audio/mpeg")
        } catch (error: Throwable) {
            CrashLogger.log(
                "MP3 CONVERSION OUTPUT SAVE FAILED: uri=$sourceUri name=${metadata.displayName} " +
                    "mime=${metadata.mimeType} error=${error.stackTraceToString()}"
            )
            throw IllegalStateException(
                "The MP3 was created, but Anydown couldn't save it to the download location.",
                error
            )
        }
    } finally {
        runCatching { input.delete() }
            .onFailure { CrashLogger.log("MP3 CONVERSION INPUT CLEANUP FAILED: ${it.message}") }
        runCatching { output.delete() }
            .onFailure { CrashLogger.log("MP3 CONVERSION OUTPUT CLEANUP FAILED: ${it.message}") }
    }
}

private data class SourceMetadata(
    val displayName: String?,
    val mimeType: String?
)

private fun querySourceMetadata(context: Context, uri: Uri): SourceMetadata {
    return runCatching {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        SourceMetadata(displayName, context.contentResolver.getType(uri))
    }.getOrElse { error ->
        CrashLogger.log(
            "MP3 CONVERSION METADATA FAILED: uri=$uri error=${error.stackTraceToString()}"
        )
        SourceMetadata(null, null)
    }
}