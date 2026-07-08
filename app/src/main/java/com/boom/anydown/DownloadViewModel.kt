package com.boom.anydown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.anydown.ui.FormatType
import com.chaquo.python.PyException
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface ProgressCallback {
    fun onProgress(percent: Int, status: String)
}

class DownloadViewModel : DownloadViewModelParent() {
    var downloadPercent by mutableStateOf(0)
        private set
    var activeFormat by mutableStateOf<FormatType?>(null)
        private set

    fun startDownload(format: FormatType, url: String, ffmpegPath: String) {
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
                py.getModule("downloader").callAttr("fetch_video", url, ffmpegPath, callback)
            } catch (e: PyException) {
                CrashLogger.log("PYTHON ERROR: ${e.message}")
            }
        }
    }
}

open class DownloadViewModelParent : ViewModel()
