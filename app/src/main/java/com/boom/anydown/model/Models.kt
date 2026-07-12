package com.boom.anydown.model

data class DownloadFormat(
    val id: String,
    val label: String,
    val subtitle: String,
    val sizeText: String = "Size unknown",
    val approxSizeMb: Int? = null
)

data class VideoResult(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val durationText: String,
    val formats: List<DownloadFormat>
)

enum class DownloadStatus { DOWNLOADING, COMPLETED }

data class DownloadedItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val sizeMb: Int,
    val filePath: String,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING
)

sealed class HomeUiState {
    data class Idle(
        val linkInput: String = "",
        val clipboardSuggestion: String? = null,
        val isLoading: Boolean = false,
        val loadingStatusText: String = ""
    ) : HomeUiState()
    data class Result(val video: VideoResult) : HomeUiState()
}
