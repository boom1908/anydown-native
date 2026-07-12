package com.boom.anydown.util
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File

fun saveToDownloads(context: Context, sourceFile: File, mimeType: String): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return ""
    resolver.openOutputStream(uri)?.use { out -> sourceFile.inputStream().copyTo(out) }
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri.toString()
}
