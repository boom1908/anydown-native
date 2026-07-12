package com.boom.anydown.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.boom.anydown.model.DownloadFormat
import com.boom.anydown.model.HomeUiState
import com.boom.anydown.model.VideoResult
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.brutalist.rememberPressedShadowOffset
import com.boom.anydown.ui.theme.AnydownColors
import kotlinx.coroutines.delay

@Composable
fun HomeIdleContent(
    state: HomeUiState.Idle,
    onLinkChanged: (String) -> Unit,
    onFetch: () -> Unit,
    onClipboardDetected: (String) -> Unit,
    onAcceptClipboard: () -> Unit,
    onDismissClipboard: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showPortfolioToast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val clip = clipboardManager.getText()?.text.orEmpty()
        if (clip.contains("youtube.com") || clip.contains("youtu.be")) {
            onClipboardDetected(clip)
        }
    }

    LaunchedEffect(showPortfolioToast) {
        if (showPortfolioToast) {
            delay(1600)
            showPortfolioToast = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AnydownColors.background)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(56.dp))

            Row(
                modifier = Modifier
                    .brutalistBox(cornerRadius = 100.dp, shadowOffset = 3.dp, backgroundColor = AnydownColors.green)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    "POWERED BY YT-DLP",
                    color = AnydownColors.onAccentDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("ANYDOWN", color = AnydownColors.textPrimary, fontWeight = FontWeight.Black, fontSize = 36.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Download any YouTube video as full video, audio only, or a fast lightweight file.",
                color = AnydownColors.textMuted,
                fontSize = 14.5.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(36.dp))

            state.clipboardSuggestion?.let { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brutalistClickable(
                            onClick = onAcceptClipboard,
                            cornerRadius = 100.dp,
                            shadowOffset = 4.dp,
                            backgroundColor = AnydownColors.panel
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = AnydownColors.green)
                    Spacer(Modifier.width(8.dp))
                    Text("YT Link Detected. Tap to paste.", color = AnydownColors.textPrimary, fontSize = 12.5.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .brutalistBox(cornerRadius = 10.dp, shadowOffset = 4.dp)
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                if (state.linkInput.isEmpty()) {
                    Text("Paste YouTube video link here", color = AnydownColors.textMuted, fontSize = 13.5.sp)
                }
                BasicTextField(
                    value = state.linkInput,
                    onValueChange = onLinkChanged,
                    textStyle = TextStyle(color = AnydownColors.textPrimary, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(AnydownColors.blue),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(22.dp))

            val fetchInteractionSource = remember { MutableInteractionSource() }
            val fetchOffset by rememberPressedShadowOffset(fetchInteractionSource, restingOffset = 6.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .brutalistClickable(
                        onClick = onFetch,
                        cornerRadius = 10.dp,
                        shadowOffset = fetchOffset,
                        backgroundColor = AnydownColors.yellow,
                        enabled = !state.isLoading
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.isLoading) state.loadingStatusText else "FETCH",
                    color = AnydownColors.onAccentDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (state.isLoading) 12.sp else 15.sp
                )
            }

            // extra bottom room so the floating widget never overlaps content
            Spacer(Modifier.height(100.dp))
        }

        // Floating "know more about the developer" widget — pinned to the
        // bottom of the Home tab regardless of scroll position.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .brutalistClickable(
                    onClick = { showPortfolioToast = true },
                    cornerRadius = 100.dp,
                    shadowOffset = 4.dp,
                    backgroundColor = AnydownColors.panel
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "Know more about the developer",
                color = AnydownColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showPortfolioToast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 3 },
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .brutalistBox(cornerRadius = 8.dp, shadowOffset = 3.dp, backgroundColor = AnydownColors.textPrimary)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Portfolio coming soon", color = AnydownColors.onAccentDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HomeResultContent(
    video: VideoResult,
    onFormatSelected: (DownloadFormat, Offset) -> Unit,
    onGrabAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AnydownColors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        // MOCK: thumbnailUrl/title/durationText come from VideoResult, which
        // is currently produced by AnydownViewModel.mockVideoResult().
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .brutalistBox(cornerRadius = 10.dp, shadowOffset = 6.dp)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = AnydownColors.onAccentDark,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .background(AnydownColors.ink, RoundedCornerShape(50))
                    .padding(6.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(AnydownColors.ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(video.durationText, color = AnydownColors.onAccentDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(video.title, color = AnydownColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Video found · 1080p, 720p & audio available", color = AnydownColors.textMuted, fontSize = 12.5.sp)

        Spacer(Modifier.height(24.dp))
        Text(
            "CHOOSE DOWNLOAD OPTION",
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))

        video.formats.forEach { format ->
            FormatCard(format = format, onSelected = onFormatSelected)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .brutalistClickable(
                    onClick = onGrabAnother,
                    cornerRadius = 10.dp,
                    shadowOffset = 5.dp,
                    backgroundColor = AnydownColors.panel
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("GRAB ANOTHER", color = AnydownColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormatCard(
    format: DownloadFormat,
    onSelected: (DownloadFormat, Offset) -> Unit
) {
    var cardCenter by remember { mutableStateOf(Offset.Zero) }
    val accent = when (format.id) {
        "full" -> AnydownColors.blue
        "audio" -> AnydownColors.green
        else -> AnydownColors.coral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                cardCenter = Offset(bounds.center.x, bounds.center.y)
            }
            .brutalistClickable(
                onClick = { onSelected(format, cardCenter) },
                cornerRadius = 10.dp,
                shadowOffset = 5.dp
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (format.id) { "full" -> "🎬"; "audio" -> "🎵"; else -> "⚡" },
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(format.label, color = AnydownColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(format.subtitle, color = AnydownColors.textMuted, fontSize = 11.sp)
        }
        Text(
            text = format.approxSizeMb?.let { "Approx. $it MB" } ?: "Size unknown",
            color = AnydownColors.green,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
