package com.boom.anydown.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FormatType { VIDEO, AUDIO, FAST }

@Composable
fun ResultsScreen(
    title: String,
    channel: String,
    onBack: () -> Unit,
    onDownload: (FormatType) -> Unit,
    downloadingFormat: FormatType?,
    downloadPercent: Int
) {
    TerminalShell(title = "anydown — results.sh") {
        TextButton(onClick = onBack) {
            Text("← New Link", color = AnydownColors.Ink, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, fontFamily = SpaceGroteskFamily)
        }

        Row(modifier = Modifier
            .fillMaxWidth()
            .background(AnydownColors.Panel, RoundedCornerShape(6.dp))
            .border(3.dp, AnydownColors.Ink, RoundedCornerShape(6.dp))
            .padding(14.dp)) {

            Box(Modifier
                .size(width = 128.dp, height = 84.dp)
                .background(Brush.linearGradient(listOf(AnydownColors.Blue, AnydownColors.Green)), RoundedCornerShape(4.dp))
                .border(2.dp, AnydownColors.Ink, RoundedCornerShape(4.dp)))

            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text("VIDEO FOUND", color = AnydownColors.Green, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = JetBrainsMonoFamily)
                Text(title, color = AnydownColors.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = SpaceGroteskFamily)
                Text(channel, color = AnydownColors.Muted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = SpaceGroteskFamily)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("CHOOSE DOWNLOAD OPTION", color = AnydownColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SpaceGroteskFamily)
        Spacer(Modifier.height(14.dp))

        FormatCard(FormatType.VIDEO, "Video + Audio (Best Quality)", listOf("MP4", "Up to 4K", "~92 MB"),
            AnydownColors.Blue, downloadingFormat == FormatType.VIDEO, downloadPercent) { onDownload(FormatType.VIDEO) }
        Spacer(Modifier.height(14.dp))
        FormatCard(FormatType.AUDIO, "Audio Only", listOf("MP3", "320 kbps", "~4.6 MB"),
            AnydownColors.Green, downloadingFormat == FormatType.AUDIO, downloadPercent) { onDownload(FormatType.AUDIO) }
        Spacer(Modifier.height(14.dp))
        FormatCard(FormatType.FAST, "Fast Download (Video + Audio)", listOf("MP4", "720p", "~28 MB", "Faster"),
            AnydownColors.Coral, downloadingFormat == FormatType.FAST, downloadPercent) { onDownload(FormatType.FAST) }
    }
}

@Composable
fun FormatCard(
    type: FormatType, name: String, badges: List<String>, accentColor: Color,
    isDownloading: Boolean, percent: Int, onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
        .fillMaxWidth()
        .background(AnydownColors.Panel, RoundedCornerShape(6.dp))
        .border(3.dp, AnydownColors.Ink, RoundedCornerShape(6.dp))
        .padding(16.dp)) {

        Box(Modifier.size(42.dp).background(accentColor, RoundedCornerShape(6.dp)).border(2.dp, AnydownColors.Ink, RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = AnydownColors.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = SpaceGroteskFamily)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                badges.forEach {
                    Text(it, fontSize = 11.5.sp, color = AnydownColors.Muted, fontFamily = JetBrainsMonoFamily,
                        modifier = Modifier.background(AnydownColors.CardDark, RoundedCornerShape(4.dp))
                            .border(1.5.dp, AnydownColors.Ink, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }
        Button(onClick = onClick, enabled = !isDownloading,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            border = BorderStroke(3.dp, AnydownColors.Ink), shape = RoundedCornerShape(5.dp)) {
            if (isDownloading) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF111111))
                Spacer(Modifier.width(6.dp))
                Text("$percent%", color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = JetBrainsMonoFamily)
            } else {
                Text("Download", color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = SpaceGroteskFamily)
            }
        }
    }
}
