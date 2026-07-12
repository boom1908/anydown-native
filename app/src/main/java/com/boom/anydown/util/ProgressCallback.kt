package com.boom.anydown.util
interface ProgressCallback {
    fun onProgress(percent: Int, status: String)
}
