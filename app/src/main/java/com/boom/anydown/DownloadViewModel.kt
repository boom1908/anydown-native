package com.boom.anydown

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boom.anydown.ui.FormatType
import com.chaquo.python.PyException
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

interface ProgressCallback {
    fun onProgress(percent: Int, status: String)
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    var downloadPercent by mutableStateOf(0)
        private set
    var activeFormat by mutableStateOf<FormatType?>(null)
        private set

    fun startDownload(context: Context, format: FormatType, url: String, ffmpegPath: String, outputDir: String) {
        activeFormat = format
        viewModelScope.launch(Dispatchers.IO) {
            val callback = object : ProgressCallback {
                override fun onProgress(percent: Int, status: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadPercent = percent
                        if (status == "done") activeFormat = null
                    }
                }
            }
            try {
                val py = Python.getInstance()
                // Python downloads to private storage and returns the final filepath
                val resultPath = py.getModule("downloader").callAttr("fetch_video", url, ffmpegPath, outputDir, callback).toString()
                
                // Copy the finished file to the public Downloads folder safely
                saveToDownloads(context, File(resultPath))
            } catch (e: PyException) {
                // Log and surface the error to the UI
                CrashLogger.log("PYTHON ERROR: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Python error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                activeFormat = null
            }
        }
    }

    private fun saveToDownloads(context: Context, sourceFile: File) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { out -> sourceFile.inputStream().copyTo(out) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(it, values, null, null)
            
            // Clean up the private hidden file to save storage space!
            sourceFile.delete()
        }
    }
}
