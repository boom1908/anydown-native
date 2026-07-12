package com.boom.anydown.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.boom.anydown.model.DownloadedItem
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

@Composable
fun DownloadsScreen(
    downloads: List<DownloadedItem>,
    onDelete: (String) -> Unit,
    onOpen: (DownloadedItem) -> Unit,
    onGrabAnother: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(AnydownColors.background)) {
        Text(
            "DOWNLOADS",
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            modifier = Modifier.padding(20.dp)
        )

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
                "← swipe an item to delete",
                color = AnydownColors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    SwipeableDownloadRow(item = item, onDelete = onDelete, onOpen = onOpen)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDownloadRow(
    item: DownloadedItem,
    onDelete: (String) -> Unit,
    onOpen: (DownloadedItem) -> Unit
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
                .padding(10.dp)
                .then(Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MOCK: thumbnailUrl/size come from the mock DownloadedItem added
            // in AnydownViewModel.onFormatSelected(). Real filePath needed for
            // the ACTION_VIEW intent in AnydownApp.kt to actually open anything.
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
                    .clickable { onOpen(item) }
            ) {
                Text(item.title, color = AnydownColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1)
                Text(text = if (item.status == com.boom.anydown.model.DownloadStatus.DOWNLOADING) "Downloading..." else "Completed", color = if (item.status == com.boom.anydown.model.DownloadStatus.DOWNLOADING) com.boom.anydown.ui.theme.AnydownColors.yellow else com.boom.anydown.ui.theme.AnydownColors.green, fontSize = 11.sp)
            }
        }
    }
}
