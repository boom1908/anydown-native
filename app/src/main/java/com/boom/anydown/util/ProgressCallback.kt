package com.boom.anydown.util

interface ProgressCallback {
    fun onProgress(percent: Int, status: String)
    fun onProgressDetails(
        percent: Int,
        status: String,
        stageText: String,
        statusDetail: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytes: Long
    )
    fun isCancelled(): Boolean
}
