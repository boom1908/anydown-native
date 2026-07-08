package com.boom.anydown.ui

import androidx.compose.animation.Crossfade
import androidx.compose.core.animation.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boom.anydown.AnydownState

@Composable
fun HomeScreen(
    state: AnydownState,
    linkText: String,
    onLinkChange: (String) -> Unit,
    onFetch: () -> Unit,
    showError: Boolean
) {
    TerminalShell(title = "anydown — fetch.sh") {
        Text("POWERED BY YT-DLP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF111111), fontFamily = SpaceGroteskFamily, modifier = Modifier
                .background(AnydownColors.Green, RoundedCornerShape(100.dp))
                .border(2.dp, AnydownColors.Ink, RoundedCornerShape(100.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp))

        Spacer(Modifier.height(18.dp))
        Text("ANYDOWN", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = AnydownColors.Ink, fontFamily = SpaceGroteskFamily)
        Spacer(Modifier.height(8.dp))
        Text("Download any YouTube video as full video, audio only, or a fast lightweight file.",
            fontSize = 15.sp, color = AnydownColors.Muted, lineHeight = 22.sp, fontFamily = SpaceGroteskFamily)
        Spacer(Modifier.height(24.dp))

        when (state) {
            is AnydownState.Idle -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .background(AnydownColors.Panel, RoundedCornerShape(6.dp))
                    .border(3.dp, AnydownColors.Ink, RoundedCornerShape(6.dp))
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {

                    Text("›", color = AnydownColors.Ink, fontWeight = FontWeight.ExtraBold, fontFamily = JetBrainsMonoFamily)
                    TextField(
                        value = linkText,
                        onValueChange = onLinkChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Paste YouTube video link here", color = Color(0xFF6D707E), fontFamily = SpaceGroteskFamily) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = AnydownColors.Ink,
                            unfocusedTextColor = AnydownColors.Ink
                        )
                    )
                    Button(
                        onClick = onFetch,
                        colors = ButtonDefaults.buttonColors(containerColor = AnydownColors.Yellow),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(3.dp, AnydownColors.Ink)
                    ) {
                        Text("Fetch Video", color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontFamily = SpaceGroteskFamily)
                    }
                }
                if (showError) {
                    Spacer(Modifier.height(12.dp))
                    Text("That doesn't look like a valid YouTube link. Try again.",
                        color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, fontFamily = SpaceGroteskFamily)
                }
            }

            is AnydownState.Loading -> {
                LoadingIndicator(progress = state.progress)
            }
            else -> {}
        }
    }
}

@Composable
fun LoadingIndicator(progress: Float) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(progress, animationSpec = tween(400), label = "progress")
    val label = when {
        progress < 0.4f -> "Reading link…"
        progress < 1f -> "Connecting to yt-dlp…"
        else -> "Loading available formats…"
    }
    Column {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AnydownColors.Green,
            trackColor = AnydownColors.CardDark
        )
        Spacer(Modifier.height(10.dp))
        Crossfade(label, label = "status-text") { text ->
            Text(text, fontFamily = JetBrainsMonoFamily, fontSize = 13.5.sp, color = AnydownColors.Muted)
        }
    }
}
