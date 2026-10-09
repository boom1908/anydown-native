package com.boom.anydown.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boom.anydown.model.*
import com.boom.anydown.service.DownloadQueue
import com.boom.anydown.util.*
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AnydownViewModel(application: Application) : AndroidViewModel(application) {
    var homeState by mutableStateOf<HomeUiState>(HomeUiState.Idle())
        private set

    /**
     * The download list now lives in the background service's queue, not in the
     * ViewModel — the UI just observes it, so downloads keep running when this
     * screen (or the whole app) goes away.
     */
    val downloads: StateFlow<List<DownloadedItem>> = DownloadQueue.items

    /**
     * Playlist selections, keyed by format id ("full" | "audio" | "fast").
     * Each section keeps its own independent set of chosen video ids.
     */
    var playlistSelections by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set

    private var loadingJob: Job? = null

    init {
        DownloadQueue.ensureLoaded(application)
    }

    fun onLinkChanged(text: String) {
        val idle = homeState as? HomeUiState.Idle ?: return
        homeState = idle.copy(linkInput = text)
    }
    fun onClipboardLinkDetected(link: String) {
        val idle = homeState as? HomeUiState.Idle ?: return
        if (idle.linkInput.isBlank()) homeState = idle.copy(clipboardSuggestion = link)
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

        homeState = idle.copy(isLoading = true, loadingStatusText = "Connecting to source…", errorText = null)
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            suspend fun status(text: String) = withContext(Dispatchers.Main) {
                (homeState as? HomeUiState.Idle)?.let {
                    homeState = it.copy(isLoading = true, loadingStatusText = text, errorText = null)
                }
            }

            try {
                val py = Python.getInstance()
                val downloader = py.getModule("downloader")

                // --- Spotify links (public data only, no API credentials) ---
                val spotifyKind = downloader.callAttr("spotify_link_type", idle.linkInput).toString()

                if (spotifyKind == "playlist" || spotifyKind == "album") {
                    status("Analyzing Spotify collection…")
                    // Some "albums" are a single song released as an album — those
                    // should behave exactly like a track link. Anything else (or
                    // any doubt at all) falls back to the explainer popup.
                    val single = try {
                        downloader.callAttr("resolve_spotify_collection_single", idle.linkInput)
                    } catch (e: Throwable) {
                        CrashLogger.log("Spotify collection probe failed: ${e.message}")
                        null
                    }
                    if (single != null && single.toString() != "None") {
                        status("Matching audio source…")
                        val match = single.asMap().toSpotifyMatch(idle.linkInput)
                        if (match.videoUrl.isNotBlank()) {
                            withContext(Dispatchers.Main) { homeState = HomeUiState.SpotifyTrack(match) }
                            return@launch
                        }
                    }
                    // Safety net: record what the page actually looked like so a
                    // missed single-song album can be diagnosed later. Internal
                    // log only — never surfaced to the user.
                    runCatching {
                        CrashLogger.log(
                            downloader.callAttr("spotify_collection_debug", idle.linkInput).toString()
                        )
                    }
                    withContext(Dispatchers.Main) {
                        homeState = HomeUiState.Idle(
                            linkInput = idle.linkInput,
                            spotifyCollectionKind = spotifyKind
                        )
                    }
                    return@launch
                }

                if (spotifyKind == "track") {
                    status("Locating Spotify track…")
                    val m = downloader.callAttr("resolve_spotify_track", idle.linkInput).asMap()
                    status("Matching audio source…")
                    val match = m.toSpotifyMatch(idle.linkInput)
                    withContext(Dispatchers.Main) { homeState = HomeUiState.SpotifyTrack(match) }
                    return@launch
                }

                val isPlaylist = downloader.callAttr("is_playlist", idle.linkInput).toBoolean()

                if (isPlaylist) {
                    status("Scanning playlist items…")
                    val res = downloader.callAttr("fetch_playlist_info", idle.linkInput).asMap()
                    val entries = res[PyObject.fromJava("entries")]!!.asList().map { e ->
                        val m = e.asMap()
                        PlaylistEntry(
                            id = m[PyObject.fromJava("id")].toString(),
                            url = m[PyObject.fromJava("url")].toString(),
                            title = m[PyObject.fromJava("title")].toString(),
                            thumbnailUrl = m[PyObject.fromJava("thumbnailUrl")].toString(),
                            durationText = m[PyObject.fromJava("durationText")].toString()
                        )
                    }
                    if (entries.isEmpty()) throw IllegalStateException("empty playlist")
                    val playlist = PlaylistResult(
                        sourceUrl = idle.linkInput,
                        title = res[PyObject.fromJava("title")].toString(),
                        entries = entries,
                        formats = defaultFormats()
                    )
                    withContext(Dispatchers.Main) {
                        playlistSelections = emptyMap()
                        homeState = HomeUiState.Playlist(playlist)
                    }
                    return@launch
                }

                // Single video — unchanged from before.
                status("Extracting media streams…")
                val res = downloader.callAttr("fetch_video_info", idle.linkInput).asMap()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CrashLogger.log("FETCH FAILED: ${e.message}")
                val message = friendlyError(e)
                withContext(Dispatchers.Main) {
                    homeState = HomeUiState.Idle(linkInput = idle.linkInput, errorText = message)
                }
            }
        }
    }

    /** Turns any failure into one clear, friendly line for the home screen. */
    private fun friendlyError(e: Throwable): String {
        val text = (generateSequence(e) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")).lowercase()
        return when {
            listOf("timed out", "timeout", "unreachable", "network", "connection",
                "resolve host", "urlerror", "temporary failure", "socket")
                .any { text.contains(it) } ->
                "Lost signal for a second 📡 — check your connection and retry."

            listOf("unsupported url", "is not a valid url", "no video", "not available",
                "private", "unavailable", "removed", "404", "does not exist",
                "could not read", "no youtube match", "empty playlist", "unable to extract")
                .any { text.contains(it) } ->
                "Hmm, couldn't find anything there 🕵️ — double check the link and try again."

            else -> "That one didn't work — give it another shot!"
        }
    }

    private fun Map<PyObject, PyObject>.toSpotifyMatch(sourceUrl: String): SpotifyMatch {
        fun str(key: String) = this[PyObject.fromJava(key)]?.toString().orEmpty()
        return SpotifyMatch(
            spotifyUrl = sourceUrl,
            spotifyTitle = str("spotifyTitle"),
            spotifyArtist = str("spotifyArtist"),
            videoUrl = str("url"),
            videoTitle = str("title"),
            channel = str("channel"),
            thumbnailUrl = str("thumbnailUrl"),
            durationText = str("durationText")
        )
    }

    /** Single-video download — now just a batch of one on the background queue. */
    fun onFormatSelected(format: DownloadFormat, video: VideoResult, context: Context) {
        DownloadQueue.enqueue(
            context,
            listOf(
                DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = video.sourceUrl,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    formatId = format.id
                )
            )
        )
    }

    fun dismissSpotifyCollectionDialog() {
        val idle = homeState as? HomeUiState.Idle ?: return
        homeState = idle.copy(spotifyCollectionKind = null)
    }

    fun dismissError() {
        val idle = homeState as? HomeUiState.Idle ?: return
        homeState = idle.copy(errorText = null)
    }

    /**
     * Confirmed Spotify match — user chooses M4A or MP3.
     */
    fun downloadSpotifyMatch(match: SpotifyMatch, context: Context, formatId: String = "audio") {
        DownloadQueue.enqueue(
            context,
            listOf(
                DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = match.videoUrl,
                    title = match.videoTitle,
                    thumbnailUrl = match.thumbnailUrl,
                    formatId = formatId
                )
            )
        )
    }

    // --- playlist selection ------------------------------------------------

    fun selectedIds(formatId: String): Set<String> = playlistSelections[formatId].orEmpty()

    fun selectedCount(formatId: String): Int = selectedIds(formatId).size

    fun toggleSelection(formatId: String, entryId: String) {
        val current = selectedIds(formatId)
        val next = if (current.contains(entryId)) current - entryId else current + entryId
        playlistSelections = playlistSelections + (formatId to next)
    }

    fun setSelection(formatId: String, ids: Set<String>) {
        playlistSelections = playlistSelections + (formatId to ids)
    }

    fun totalSelected(): Int = playlistSelections.values.sumOf { it.size }

    /**
     * Gathers every selection across sections — each in its own
     * quality — and hands the whole batch to the background queue with a batch ID.
     */
    fun downloadSelectedPlaylistItems(context: Context) {
        val state = homeState as? HomeUiState.Playlist ?: return
        val byId = state.playlist.entries.associateBy { it.id }
        val batchId = UUID.randomUUID().toString()
        val batchTitle = state.playlist.title

        val requests = mutableListOf<DownloadRequest>()
        state.playlist.formats.forEach { format ->
            selectedIds(format.id).forEach { entryId ->
                val entry = byId[entryId] ?: return@forEach
                requests.add(
                    DownloadRequest(
                        id = UUID.randomUUID().toString(),
                        url = entry.url,
                        title = entry.title,
                        thumbnailUrl = entry.thumbnailUrl,
                        formatId = format.id,
                        batchId = batchId,
                        batchTitle = batchTitle
                    )
                )
            }
        }
        if (requests.isEmpty()) return
        DownloadQueue.enqueue(context, requests)
        playlistSelections = emptyMap()
    }

    // --- downloads list actions -------------------------------------------

    fun cancelDownload(id: String) {
        DownloadQueue.cancel(getApplication(), id)
    }

    fun deleteDownload(id: String, context: Context) {
        DownloadQueue.remove(context, id)
    }

    fun retryItem(id: String, context: Context) {
        DownloadQueue.retryItem(context, id)
    }

    fun retryBatchFailed(batchId: String, context: Context) {
        DownloadQueue.retryBatchFailed(context, batchId)
    }

    fun grabAnother() {
        loadingJob?.cancel()
        playlistSelections = emptyMap()
        homeState = HomeUiState.Idle()
    }

    private fun defaultFormats() = listOf(
        DownloadFormat("full", "Video + Audio (Best Quality)", "Best available · MP4", "Size depends on quality"),
        DownloadFormat("audio", "Audio Only (M4A)", "Fast · M4A (AAC)", "5-10 MB each"),
        DownloadFormat("mp3", "Audio Only (MP3)", "Universal · 192 kbps MP3", "5-10 MB each"),
        DownloadFormat("fast", "Fast Download", "720p · MP4", "<50 MB each")
    )
}
