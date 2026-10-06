package com.boom.anydown.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * The user-selected destination for downloads and converted files.
 *
 * A tree URI is persisted by Android, so it remains usable after process
 * restarts and app updates. An empty value means "use the normal Downloads
 * collection", preserving the original behaviour for existing users.
 */
object DownloadLocation {
    private const val PREFS = "download_location"
    private const val TREE_URI = "tree_uri"

    fun currentUri(context: Context): Uri? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TREE_URI, null)
            ?: return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TREE_URI, uri.toString())
            .apply()
    }

    fun displayName(context: Context): String {
        val uri = currentUri(context) ?: return "Device Downloads"
        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull()
        val readable = documentId
            ?.substringAfter(':', documentId)
            ?.replace('_', ' ')
            ?.takeIf { it.isNotBlank() }
        return readable ?: "Selected folder"
    }
}