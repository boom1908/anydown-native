package com.boom.anydown.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.boom.anydown.model.DownloadedItem
import com.boom.anydown.model.DownloadStatus
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

sealed class StorageRowEntry {
    data class Single(val item: DownloadedItem) : StorageRowEntry()
    data class Batch(
        val batchId: String,
        val title: String,
        val items: List<DownloadedItem>
    ) : StorageRowEntry()
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadedItem>,
    downloadLocation: String,
    onChangeLocation: () -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (DownloadedItem) -> Unit,
    onCancel: (String) -> Unit,
    onGrabAnother: () -> Unit,
    onRetryItem: (String) -> Unit = {},
    onRetryBatch: (String) -> Unit = {}
) {
    // Group into folders for batch downloads, keep single downloads as loose items
    val entries = remember(downloads) {
        val list = mutableListOf<StorageRowEntry>()
        val seenBatchIds = mutableSetOf<String>()

        downloads.forEach { item ->
            val bId = item.batchId
            if (bId.isNullOrBlank()) {
                list.add(StorageRowEntry.Single(item))
            } else if (!seenBatchIds.contains(bId)) {
                seenBatchIds.add(bId)
                val batchItems = downloads.filter { it.batchId == bId }
                val title = item.batchTitle?.ifBlank { null } ?: "Batch Download"
                list.add(StorageRowEntry.Batch(bId, title, batchItems))
            }
        }
        list
    }

    var expandedBatchIds by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().background(AnydownColors.background)) {
        Text(
            "DOWNLOADS & STORAGE",
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 10.dp, shadowOffset = 3.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DOWNLOAD & CONVERT LOCATION",
                    color = AnydownColors.textMuted,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    downloadLocation,
                    color = AnydownColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Direct destination for all downloads & converted files",
                    color = AnydownColors.textMuted,
                    fontSize = 9.5.sp
                )
            }
            TextButton(onClick = onChangeLocation) {
                Text("CHANGE", color = AnydownColors.yellow, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("STORAGE EMPTY.", color = AnydownColors.textMuted, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text("↓", color = AnydownColors.textMuted, fontSize = 28.sp)
            }
        } else {
            Text(
                "← swipe single items to delete · tap folders to expand",
                color = AnydownColors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = {
                    when (it) {
                        is StorageRowEntry.Single -> "single_${it.item.id}"
                        is StorageRowEntry.Batch -> "batch_${it.batchId}"
                    }
                }) { entry ->
                    when (entry) {
                        is StorageRowEntry.Single -> {
                            SwipeableDownloadRow(
                                item = entry.item,
                                onDelete = onDelete,
                                onOpen = onOpen,
                                onCancel = onCancel,
                                onRetry = onRetryItem
                            )
                        }
                        is StorageRowEntry.Batch -> {
                            val isExpanded = expandedBatchIds.contains(entry.batchId)
                            BatchFolderCard(
                                batch = entry,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedBatchIds = if (isExpanded) {
                                        expandedBatchIds - entry.batchId
                                    } else {
                                        expandedBatchIds + entry.batchId
                                    }
                                },
                                onOpen = onOpen,
                                onDelete = onDelete,
                                onCancel = onCancel,
                                onRetryItem = onRetryItem,
                                onRetryBatch = onRetryBatch
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(50.dp)
                .brutalistClickable(
                    onClick = onGrabAnother,
                    cornerRadius = 10.dp,
                    shadowOffset = 5.dp,
                    backgroundColor = AnydownColors.yellow
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("GRAB ANOTHER", color = AnydownColors.onAccentDark, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun BatchFolderCard(
    batch: StorageRowEntry.Batch,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: (DownloadedItem) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onRetryBatch: (String) -> Unit
) {
    val items = batch.items
    val successItems = items.filter { it.status == DownloadStatus.COMPLETED }
    val failedItems = items.filter { it.status == DownloadStatus.FAILED }
    val activeItems = items.filter {
        it.status == DownloadStatus.DOWNLOADING ||
        it.status == DownloadStatus.PROCESSING ||
        it.status == DownloadStatus.QUEUED
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalistBox(cornerRadius = 12.dp, shadowOffset = 5.dp, backgroundColor = AnydownColors.panel)
    ) {
        // Folder Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(AnydownColors.yellow, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = "Folder",
                    tint = AnydownColors.onAccentDark,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "BATCH FOLDER",
                        color = AnydownColors.yellow,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${items.size} ITEMS",
                        color = AnydownColors.textMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    batch.title,
                    color = AnydownColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (successItems.isNotEmpty()) {
                        Text(
                            "${successItems.size} Success",
                            color = AnydownColors.green,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (failedItems.isNotEmpty()) {
                        Text(
                            "${failedItems.size} Failed",
                            color = AnydownColors.danger,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (activeItems.isNotEmpty()) {
                        Text(
                            "${activeItems.size} Downloading",
                            color = AnydownColors.blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = onToggleExpand) {
                Icon(
                    if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = AnydownColors.textPrimary
                )
            }
        }

        // Expanded Folder Contents
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp)
            ) {
                Divider(color = AnydownColors.ink.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                // 1. FAILED SECTION (with Retry All)
                if (failedItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AnydownColors.danger.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = AnydownColors.danger,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "FAILED (${failedItems.size})",
                                color = AnydownColors.danger,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .brutalistClickable(
                                    onClick = { onRetryBatch(batch.batchId) },
                                    cornerRadius = 6.dp,
                                    shadowOffset = 2.dp,
                                    backgroundColor = AnydownColors.yellow
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = AnydownColors.onAccentDark,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "RETRY ALL",
                                    color = AnydownColors.onAccentDark,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    failedItems.forEach { item ->
                        BatchSubItemRow(
                            item = item,
                            onOpen = onOpen,
                            onDelete = onDelete,
                            onCancel = onCancel,
                            onRetry = onRetryItem
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                }

                // 2. IN PROGRESS SECTION
                if (activeItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Downloading,
                            contentDescription = null,
                            tint = AnydownColors.blue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "IN PROGRESS (${activeItems.size})",
                            color = AnydownColors.blue,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    activeItems.forEach { item ->
                        BatchSubItemRow(
                            item = item,
                            onOpen = onOpen,
                            onDelete = onDelete,
                            onCancel = onCancel,
                            onRetry = onRetryItem
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                }

                // 3. SUCCESS SECTION
                if (successItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AnydownColors.green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "SUCCESS (${successItems.size})",
                            color = AnydownColors.green,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    successItems.forEach { item ->
                        BatchSubItemRow(
                            item = item,
                            onOpen = onOpen,
                            onDelete = onDelete,
                            onCancel = onCancel,
                            onRetry = onRetryItem
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchSubItemRow(
    item: DownloadedItem,
    onOpen: (DownloadedItem) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnydownColors.background, RoundedCornerShape(8.dp))
            .clickable(enabled = item.status == DownloadStatus.COMPLETED) { onOpen(item) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 54.dp, height = 36.dp)
                .background(AnydownColors.panel, RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FormatBadge(item.formatId)
                when (item.status) {
                    DownloadStatus.COMPLETED -> {
                        val sz = if (item.sizeMb > 0) "${item.sizeMb} MB" else "Saved"
                        Text(sz, color = AnydownColors.green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.FAILED -> {
                        Text("Failed", color = AnydownColors.danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.DOWNLOADING -> {
                        Text("${item.progress}%", color = AnydownColors.yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.PROCESSING -> {
                        Text("Processing", color = AnydownColors.blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.QUEUED -> {
                        Text("Queued", color = AnydownColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.CANCELLED -> {
                        Text("Cancelled", color = AnydownColors.coral, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PROCESSING) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = if (item.status == DownloadStatus.PROCESSING) AnydownColors.blue else AnydownColors.yellow,
                    trackColor = AnydownColors.panel
                )
            }

            if (item.status == DownloadStatus.FAILED && !item.failureReason.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    item.failureReason,
                    color = AnydownColors.textMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // Action buttons
        when (item.status) {
            DownloadStatus.FAILED -> {
                IconButton(onClick = { onRetry(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Retry",
                        tint = AnydownColors.yellow,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = AnydownColors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.QUEUED -> {
                IconButton(onClick = { onCancel(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel",
                        tint = AnydownColors.coral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = AnydownColors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDownloadRow(
    item: DownloadedItem,
    onDelete: (String) -> Unit,
    onOpen: (DownloadedItem) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item.id)
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AnydownColors.danger, RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AnydownColors.onAccentDark)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 10.dp, shadowOffset = 4.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 68.dp, height = 46.dp)
                    .background(AnydownColors.background, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = item.status == DownloadStatus.COMPLETED) { onOpen(item) }
            ) {
                Text(item.title, color = AnydownColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                FormatBadge(item.formatId)

                when (item.status) {
                    DownloadStatus.QUEUED -> {
                        Spacer(Modifier.height(4.dp))
                        Text("Queued", color = AnydownColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.DOWNLOADING -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.stageText ?: "Downloading... ${item.progress}%",
                            color = AnydownColors.yellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = item.progress / 100f,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = AnydownColors.yellow,
                            trackColor = AnydownColors.background
                        )
                        if (!item.statusDetail.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.statusDetail,
                                color = AnydownColors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    DownloadStatus.PROCESSING -> {
                        Spacer(Modifier.height(4.dp))
                        val defaultText = if (item.formatId == "mp3") "Converting audio to MP3..." else "Merging video & audio with FFmpeg..."
                        Text(
                            item.stageText ?: defaultText,
                            color = AnydownColors.blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = AnydownColors.blue,
                            trackColor = AnydownColors.background
                        )
                        if (!item.statusDetail.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.statusDetail,
                                color = AnydownColors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Spacer(Modifier.height(4.dp))
                        val completedText = if (item.sizeMb > 0) "Completed · ${item.sizeMb} MB" else "Completed"
                        Text(completedText, color = AnydownColors.green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.CANCELLED -> {
                        Spacer(Modifier.height(4.dp))
                        Text("Cancelled", color = AnydownColors.coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    DownloadStatus.FAILED -> {
                        Spacer(Modifier.height(4.dp))
                        Text("Failed", color = AnydownColors.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            item.failureReason ?: "Something went wrong with this one — give it another try",
                            color = AnydownColors.textMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            if (item.status == DownloadStatus.FAILED) {
                IconButton(onClick = { onRetry(item.id) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = AnydownColors.yellow)
                }
            } else if (item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.PROCESSING ||
                item.status == DownloadStatus.QUEUED
            ) {
                IconButton(onClick = { onCancel(item.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = AnydownColors.coral)
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(formatId: String?) {
    val (label, accent, icon) = when (formatId) {
        "mp3" -> Triple("MP3 AUDIO", AnydownColors.yellow, Icons.Filled.MusicNote)
        "audio" -> Triple("M4A AUDIO", AnydownColors.green, Icons.Filled.MusicNote)
        "fast" -> Triple("FAST 720P", AnydownColors.coral, Icons.Filled.Bolt)
        else -> Triple("BEST QUALITY", AnydownColors.blue, Icons.Filled.Movie)
    }

    Row(
        modifier = Modifier
            .background(accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AnydownColors.onAccentDark, modifier = Modifier.size(12.dp))
        Text(label, color = AnydownColors.onAccentDark, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}
