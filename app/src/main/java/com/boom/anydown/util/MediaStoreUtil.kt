package com.boom.anydown.util
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

fun saveToDownloads(context: Context, sourceFile: File, mimeType: String): String {
    val selectedFolder = DownloadLocation.currentUri(context)
    if (selectedFolder == null) {
        CrashLogger.log(
            "DOWNLOAD DESTINATION: using device Downloads for file=${sourceFile.name} mime=$mimeType"
        )
        return saveToMediaStore(context, sourceFile, mimeType)
    }

    return runCatching {
            saveToTree(context.contentResolver, selectedFolder, sourceFile, mimeType)
        }.onSuccess { savedUri ->
            CrashLogger.log(
                "DOWNLOAD DESTINATION: saved file=${sourceFile.name} mime=$mimeType " +
                    "to selectedFolder=$selectedFolder uri=$savedUri"
            )
        }.getOrElse { error ->
            CrashLogger.log(
                "DOWNLOAD DESTINATION: selected folder write failed folder=$selectedFolder " +
                    "file=${sourceFile.name} mime=$mimeType error=${error.stackTraceToString()} " +
                    "falling back to device Downloads"
            )
            saveToMediaStore(context, sourceFile, mimeType)
        }
}

private fun saveToTree(
    resolver: ContentResolver,
    treeUri: Uri,
    sourceFile: File,
    mimeType: String
): String {
    val documentUri = try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    } catch (_: Throwable) {
        treeUri
    }

    val uri = DocumentsContract.createDocument(
        resolver,
        documentUri,
        mimeType,
        sourceFile.name
    ) ?: throw IllegalStateException("Could not create a file in the selected folder")
    try {
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Could not open the selected folder")
        return uri.toString()
    } catch (error: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
}

private fun saveToMediaStore(context: Context, sourceFile: File, mimeType: String): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        CrashLogger.log(
            "DOWNLOAD DESTINATION: device Downloads insert failed file=${sourceFile.name} mime=$mimeType"
        )
        return ""
    }
    CrashLogger.log(
        "DOWNLOAD DESTINATION: created device Downloads item uri=$uri file=${sourceFile.name}"
    )
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Could not open device Downloads")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val ready = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, ready, null, null)
        }
        CrashLogger.log(
            "DOWNLOAD DESTINATION: saved file=${sourceFile.name} mime=$mimeType " +
                "to device Downloads uri=$uri"
        )
        uri.toString()
    } catch (error: Throwable) {
        CrashLogger.log(
            "DOWNLOAD DESTINATION: device Downloads write failed uri=$uri " +
                "file=${sourceFile.name} error=${error.stackTraceToString()}"
        )
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
}
