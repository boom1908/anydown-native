package com.boom.anydown.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.anydown.model.DownloadFormat
import com.boom.anydown.model.DownloadedItem
import com.boom.anydown.model.HomeUiState
import com.boom.anydown.model.VideoResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single ViewModel instance shared by both bottom-nav tabs (hoisted above
 * the NavHost in AnydownApp). Because Home/Downloads read from this instead
 * of from navigation-scoped state, switching tabs never resets Home — only
 * grabAnother() does, satisfying the "preserve ResultState across tabs" rule.
 */
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
        loadingJob = viewModelScope.launch {
            val statuses = listOf(
                "Waking up Python engine…",
                "Negotiating with YouTube…",
                "Extracting metadata…"
            )
            for (status in statuses) {
                val current = homeState as? HomeUiState.Idle ?: return@launch
                homeState = current.copy(loadingStatusText = status)
                delay(650)
            }

            // ------------------------------------------------------------
            // MOCK RESULT — replace mockVideoResult() with a real yt-dlp
            // lookup. Nothing else in the UI needs to change: it only
            // depends on the VideoResult shape (title, thumbnailUrl,
            // durationText, formats).
            // ------------------------------------------------------------
            homeState = HomeUiState.Result(mockVideoResult())
        }
    }

    fun onFormatSelected(format: DownloadFormat, video: VideoResult) {
        // TODO: kick off the real download (yt-dlp + FFmpeg) here instead
        // of this simulated delay + mock DownloadedItem.
        viewModelScope.launch {
            delay(550) // gives the fly-to-tab aura time to land first
            downloads.add(
                0,
                DownloadedItem(
                    id = UUID.randomUUID().toString(),
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    sizeMb = format.approxSizeMb ?: 0,
                    filePath = "" // TODO: real on-device file path from the backend
                )
            )
        }
    }

    fun deleteDownload(id: String) {
        downloads.removeAll { it.id == id }
    }

    /** The only thing allowed to reset Home back to IdleState. */
    fun grabAnother() {
        loadingJob?.cancel()
        homeState = HomeUiState.Idle()
    }

    private fun mockVideoResult() = VideoResult(
        title = "Sample Video Title",
        thumbnailUrl = "https://picsum.photos/seed/anydown/400/225",
        durationText = "10:24",
        formats = listOf(
            DownloadFormat("full", "Video + Audio (Best Quality)", "Up to 8K · MP4", approxSizeMb = 92),
            DownloadFormat("audio", "Audio Only", "M4A", approxSizeMb = 5),
            DownloadFormat("fast", "Fast Download", "720p · MP4", approxSizeMb = 28)
        )
    )
}
