package com.boom.anydown.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.boom.anydown.model.PlaylistEntry
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

/**
 * Checklist of every video in the playlist for one format section.
 * Selections are owned by the ViewModel, so switching between the three
 * sections preserves what was picked in each.
 */
@Composable
fun PlaylistSelectionScreen(
    sectionLabel: String,
    accent: androidx.compose.ui.graphics.Color,
    entries: List<PlaylistEntry>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: () -> Unit
) {
    val allSelected = entries.isNotEmpty() && selectedIds.size >= entries.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnydownColors.background)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(
                sectionLabel.uppercase(),
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${selectedIds.size} of ${entries.size} selected",
                color = AnydownColors.textMuted,
                fontSize = 12.5.sp
            )
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .brutalistClickable(
                        onClick = { if (allSelected) onDeselectAll() else onSelectAll() },
                        cornerRadius = 100.dp,
                        shadowOffset = 4.dp,
                        backgroundColor = accent
                    )
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    if (allSelected) "DESELECT ALL" else "SELECT ALL",
                    color = AnydownColors.onAccentDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    checked = selectedIds.contains(entry.id),
                    accent = accent,
                    onToggle = { onToggle(entry.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(52.dp)
                .brutalistClickable(
                    onClick = onConfirm,
                    cornerRadius = 10.dp,
                    shadowOffset = 5.dp,
                    backgroundColor = AnydownColors.yellow
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "SELECT",
                color = AnydownColors.onAccentDark,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: PlaylistEntry,
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalistClickable(
                onClick = onToggle,
                cornerRadius = 10.dp,
                shadowOffset = 4.dp,
                backgroundColor = if (checked) AnydownColors.panel else AnydownColors.background
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(84.dp, 50.dp)
                .background(AnydownColors.panel, RoundedCornerShape(6.dp))
        ) {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(AnydownColors.ink, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    entry.durationText,
                    color = AnydownColors.onAccentDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            entry.title,
            color = AnydownColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    if (checked) accent else AnydownColors.background,
                    RoundedCornerShape(5.dp)
                )
                .brutalistBox(
                    cornerRadius = 5.dp,
                    shadowOffset = 0.dp,
                    backgroundColor = if (checked) accent else AnydownColors.background
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = AnydownColors.onAccentDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
