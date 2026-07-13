package com.boom.anydown.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.anydown.model.*
import com.boom.anydown.util.*
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AnydownViewModel : ViewModel() {
    var homeState by mutableStateOf<HomeUiState>(HomeUiState.Idle())
        private set
    val downloads = mutableStateListOf<DownloadedItem>()
    private var loadingJob: Job? = null

    fun onLinkChanged(text: String) {
        val idle = homeState as? HomeUiState.Idle ?: return
        homeState = idle.copy(linkInput = text)
    }

    fun onClipboardLinkDetected(link: String) {
        val idle = homeState as? HomeUiState.Idle ?: return
        if (idle.linkInput.isBlank()) {
            homeState = idle.copy(clipboardSuggestion = link)
        }
    }

    fun acceptClipboardSuggestion() {
        val idle = homeState as? HomeUiState.Idle ?: return
        val link = idle.clipboardSuggestion ?: return
        homeState = idle.copy(linkInput = link, clipboardSuggestion = null)
    }

    fun dismissClipboardSuggestion() {
        val idle = homeState as? HomeUiState.Idle ?: return
        homeState = idle.copy(clipboardSuggestion = null)
    }

    fun fetchVideo() {
        val idle = homeState as? HomeUiState.Idle ?: return
        if (idle.linkInput.isBlank() || idle.isLoading) return

        homeState = idle.copy(isLoading = true, loadingStatusText = "Waking up Python engine…")
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val res = py.getModule("downloader").callAttr("fetch_video_info", idle.linkInput).asMap()
                val formatsRaw = res[PyObject.fromJava("formats")]!!.asList()
                val formats = formatsRaw.map { f ->
                    val m = f.asMap()
                    DownloadFormat(
                        id = m[PyObject.fromJava("id")].toString(),
                        label = m[PyObject.fromJava("label")].toString(),
                        subtitle = m[PyObject.fromJava("subtitle")].toString(),
                        sizeText = m[PyObject.fromJava("sizeText")].toString()
                    )
                }
                val video = VideoResult(
                    sourceUrl = idle.linkInput,
                    title = res[PyObject.fromJava("title")].toString(),
                    thumbnailUrl = res[PyObject.fromJava("thumbnailUrl")].toString(),
                    durationText = res[PyObject.fromJava("durationText")].toString(),
                    formats = formats
                )
                withContext(Dispatchers.Main) { homeState = HomeUiState.Result(video) }
            } catch (e: PyException) {
                CrashLogger.log("PYTHON ERROR (fetchVideo): ${e.message}")
                withContext(Dispatchers.Main) { homeState = HomeUiState.Idle(linkInput = idle.linkInput) }
            }
        }
    }

    fun onFormatSelected(format: DownloadFormat, video: VideoResult, context: Context) {
        val itemId = UUID.randomUUID().toString()
        downloads.add(0, DownloadedItem(itemId, video.title, video.thumbnailUrl, 0, "", DownloadStatus.DOWNLOADING, 0))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val ffmpegDir = getFfmpegBinDir(context)
                val outputDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath

                val callback = object : ProgressCallback {
                    override fun onProgress(percent: Int, status: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            val idx = downloads.indexOfFirst { it.id == itemId }
                            if (idx != -1) {
                                val currentStatus = if (status == "processing") DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING
                                downloads[idx] = downloads[idx].copy(progress = percent, status = currentStatus)
                            }
                        }
                    }
                }
                val resultPath = py.getModule("downloader")
                    .callAttr("fetch_video", video.sourceUrl, ffmpegDir, outputDir, format.id, callback)
                    .toString()

                val file = File(resultPath)
                val mime = if (format.id == "audio") "audio/mp3" else "video/mp4"
                val uri = saveToDownloads(context, file, mime)

                withContext(Dispatchers.Main) {
                    val idx = downloads.indexOfFirst { it.id == itemId }
                    if (idx != -1) downloads[idx] = downloads[idx].copy(filePath = uri, status = DownloadStatus.COMPLETED, progress = 100)
                }
            } catch (e: PyException) {
                CrashLogger.log("PYTHON ERROR (download): ${e.message}")
            }
        }
    }

    fun deleteDownload(id: String, context: Context) {
        val item = downloads.find { it.id == id }
        if (item != null && item.filePath.isNotEmpty()) {
            try {
                context.contentResolver.delete(Uri.parse(item.filePath), null, null)
            } catch (e: Exception) {
                CrashLogger.log("Failed to delete file from disk: ${e.message}")
            }
        }
        downloads.removeAll { it.id == id }
    }

    fun grabAnother() {
        loadingJob?.cancel()
        homeState = HomeUiState.Idle()
    }
}
