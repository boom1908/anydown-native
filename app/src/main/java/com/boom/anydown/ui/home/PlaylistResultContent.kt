package com.boom.anydown.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.boom.anydown.model.DownloadFormat
import com.boom.anydown.model.PlaylistResult
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

fun accentForFormat(formatId: String): Color = when (formatId) {
    "full" -> AnydownColors.blue
    "audio" -> AnydownColors.green
    else -> AnydownColors.coral
}

/**
 * Playlist variant of the results screen. Same three format cards, but each is
 * a section you tap into to pick videos instead of an instant download.
 */
@Composable
fun HomePlaylistContent(
    playlist: PlaylistResult,
    selectedCounts: Map<String, Int>,
    onOpenSection: (DownloadFormat) -> Unit,
    onDownloadSelected: (Offset) -> Unit,
    onGrabAnother: () -> Unit
) {
    var downloadButtonCenter by remember { mutableStateOf(Offset.Zero) }
    val totalSelected = selectedCounts.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AnydownColors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .brutalistBox(cornerRadius = 10.dp, shadowOffset = 6.dp)
        ) {
            AsyncImage(
                model = playlist.entries.firstOrNull()?.thumbnailUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(AnydownColors.ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "${playlist.entries.size} VIDEOS",
                    color = AnydownColors.onAccentDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            playlist.title,
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text("Playlist found · pick videos per format", color = AnydownColors.textMuted, fontSize = 12.5.sp)

        Spacer(Modifier.height(24.dp))
        Text(
            "CHOOSE A SECTION",
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))

        playlist.formats.forEach { format ->
            SectionCard(
                format = format,
                selectedCount = selectedCounts[format.id] ?: 0,
                onClick = { onOpenSection(format) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    downloadButtonCenter = Offset(bounds.center.x, bounds.center.y)
                }
                .brutalistClickable(
                    onClick = { onDownloadSelected(downloadButtonCenter) },
                    cornerRadius = 10.dp,
                    shadowOffset = 6.dp,
                    backgroundColor = if (totalSelected > 0) AnydownColors.yellow else AnydownColors.panel,
                    enabled = totalSelected > 0
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (totalSelected > 0) "DOWNLOAD SELECTED ($totalSelected)" else "DOWNLOAD SELECTED",
                color = if (totalSelected > 0) AnydownColors.onAccentDark else AnydownColors.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
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
private fun SectionCard(
    format: DownloadFormat,
    selectedCount: Int,
    onClick: () -> Unit
) {
    val accent = accentForFormat(format.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalistClickable(onClick = onClick, cornerRadius = 10.dp, shadowOffset = 5.dp)
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
        if (selectedCount > 0) {
            Box(
                modifier = Modifier
                    .background(accent, RoundedCornerShape(100.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    "$selectedCount selected",
                    color = AnydownColors.onAccentDark,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text("TAP TO PICK", color = AnydownColors.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
