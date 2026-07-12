package com.boom.anydown.model

/**
 * One selectable download option shown on the Result screen
 * (e.g. "Video + Audio (Best Quality)").
 */
data class DownloadFormat(
    val id: String,
    val label: String,
    val subtitle: String,      // e.g. "Up to 8K · MP4"
    val approxSizeMb: Int? = null
)

/**
 * The fetched video info + its available formats.
 * NOTE: In this file, values of this type are produced by
 * AnydownViewModel.mockVideoResult() as placeholder data.
 * Replace that function with a real yt-dlp lookup — nothing else
 * needs to change, since the UI only depends on this shape.
 */
data class VideoResult(
    val title: String,
    val thumbnailUrl: String,
    val durationText: String,
    val formats: List<DownloadFormat>
)

/** A single row in the Downloads tab. */
data class DownloadedItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val sizeMb: Int,
    val filePath: String // TODO: real on-device file path/URI from the backend
)

/**
 * Home tab has exactly two states. This sealed class is the single
 * source of truth for whether Home shows the input screen or the
 * result screen — switching bottom-nav tabs never resets it, only
 * ViewModel.grabAnother() does.
 */
sealed class HomeUiState {
    data class Idle(
        val linkInput: String = "",
        val clipboardSuggestion: String? = null,
        val isLoading: Boolean = false,
        val loadingStatusText: String = ""
    ) : HomeUiState()

    data class Result(val video: VideoResult) : HomeUiState()
}
