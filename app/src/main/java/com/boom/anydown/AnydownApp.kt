package com.boom.anydown

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boom.anydown.ui.AnydownColors
import com.boom.anydown.ui.HomeScreen
import com.boom.anydown.ui.ResultsScreen
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun isValidYoutubeLink(url: String): Boolean {
    return url.contains("youtube.com") || url.contains("youtu.be") || url.contains("shorts")
}

fun fetchVideoInfo(url: String): AnydownVideoInfo {
    val py = Python.getInstance()
    val module = py.getModule("downloader")
    val res = module.callAttr("fetch_video_info", url).asMap()
    return AnydownVideoInfo(
        title = res[com.chaquo.python.PyObject.fromJava("title")]?.toString() ?: "Unknown Video",
        channel = res[com.chaquo.python.PyObject.fromJava("channel")]?.toString() ?: "Unknown Channel",
        duration = res[com.chaquo.python.PyObject.fromJava("duration")]?.toString() ?: "0:00",
        thumbnail = res[com.chaquo.python.PyObject.fromJava("thumbnail")]?.toString() ?: ""
    )
}

data class AnydownVideoInfo(val title: String, val channel: String, val duration: String, val thumbnail: String)

@Composable
fun AnydownApp() {
    var state by remember { mutableStateOf<AnydownState>(AnydownState.Idle) }
    var linkText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val downloadVm: DownloadViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(AnydownColors.Background)
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 12 })
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "screen-switch"
        ) { screenState ->
            when (screenState) {
                is AnydownState.Idle, is AnydownState.Loading -> {
                    HomeScreen(
                        state = screenState,
                        linkText = linkText,
                        onLinkChange = { linkText = it; showError = false },
                        showError = showError,
                        onFetch = {
                            if (!isValidYoutubeLink(linkText)) {
                                showError = true
                                return@HomeScreen
                            }
                            scope.launch {
                                state = AnydownState.Loading(progress = 0f)
                                val steps = listOf(0.3f, 0.7f, 1.0f)
                                for (target in steps) {
                                    animate(initialValue = 0f, targetValue = target, animationSpec = tween(500)) { value, _ ->
                                        state = AnydownState.Loading(value)
                                    }
                                }
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val info = fetchVideoInfo(linkText)
                                        scope.launch(Dispatchers.Main) {
                                            state = AnydownState.Results(info.title, info.channel, info.duration, info.thumbnail)
                                        }
                                    } catch (e: Exception) {
                                        CrashLogger.log("FETCH ERROR: ${e.message}")
                                        scope.launch(Dispatchers.Main) { state = AnydownState.Idle }
                                    }
                                }
                            }
                        }
                    )
                }
                is AnydownState.Results -> {
                    ResultsScreen(
                        title = screenState.title,
                        channel = screenState.channel,
                        thumbnailUrl = screenState.thumbnailUrl,
                        onBack = { state = AnydownState.Idle; linkText = "" },
                        onDownload = { format ->
                            val outputDir = context.getExternalFilesDir(null)?.absolutePath ?: ""
                            downloadVm.startDownload(context = context, format = format, url = linkText, ffmpegPath = getFfmpegBinDir(context), outputDir = outputDir)
                        },
                        downloadingFormat = downloadVm.activeFormat,
                        downloadPercent = downloadVm.downloadPercent
                    )
                }
            }
        }
    }
}
