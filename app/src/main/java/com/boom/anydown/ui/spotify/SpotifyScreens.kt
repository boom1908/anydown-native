package com.boom.anydown.ui.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.boom.anydown.model.SpotifyMatch
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

/**
 * Confirmation card for a Spotify track that has been matched to a YouTube
 * video. Nothing is downloaded until the user taps Proceed — auto-matching can
 * occasionally land on a remix or live version, so the match is always shown.
 */
@Composable
fun SpotifyMatchContent(
    match: SpotifyMatch,
    onProceed: (Offset) -> Unit,
    onGrabAnother: () -> Unit
) {
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AnydownColors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .brutalistBox(cornerRadius = 100.dp, shadowOffset = 3.dp, backgroundColor = AnydownColors.green)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                "SPOTIFY TRACK MATCHED",
                color = AnydownColors.onAccentDark,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            match.spotifyTitle,
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            lineHeight = 26.sp
        )
        if (match.spotifyArtist.isNotBlank()) {
            Text(match.spotifyArtist, color = AnydownColors.textMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .brutalistBox(cornerRadius = 10.dp, shadowOffset = 6.dp)
        ) {
            AsyncImage(
                model = match.thumbnailUrl,
                contentDescription = match.videoTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (match.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(AnydownColors.ink, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        match.durationText,
                        color = AnydownColors.onAccentDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 10.dp, shadowOffset = 5.dp)
                .padding(14.dp)
        ) {
            Text("BEST YOUTUBE MATCH", color = AnydownColors.textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                match.videoTitle,
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(match.channel, color = AnydownColors.green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Downloads as Audio Only (M4A) — that's the point of a Spotify link.",
            color = AnydownColors.textMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    buttonCenter = Offset(bounds.center.x, bounds.center.y)
                }
                .brutalistClickable(
                    onClick = { onProceed(buttonCenter) },
                    cornerRadius = 10.dp,
                    shadowOffset = 6.dp,
                    backgroundColor = AnydownColors.green
                )
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "PROCEED · GET AUDIO",
                color = AnydownColors.onAccentDark,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistClickable(onClick = onGrabAnother, cornerRadius = 10.dp, shadowOffset = 5.dp)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("GRAB ANOTHER", color = AnydownColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Spotify playlist/album explainer. There is intentionally no download path
 * here — reading a full playlist needs the official Spotify Web API, which
 * this app does not use anywhere.
 */
@Composable
fun SpotifyCollectionDialog(
    kind: String,
    onConvert: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = if (kind == "album") "Album" else "Playlist"
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 14.dp, shadowOffset = 7.dp)
                .padding(20.dp)
        ) {
            Text(
                "Spotify $label Detected 🎵",
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Anydown can match individual tracks automatically, but across a whole " +
                    "$label a few can come back wrong — a remix, a live version, or a cover " +
                    "instead of the original.",
                color = AnydownColors.textMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "For accurate results, convert it to a YouTube playlist first. That also lets " +
                    "you review the matches and fix any wrong ones yourself.",
                color = AnydownColors.textMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .brutalistClickable(
                        onClick = onConvert,
                        cornerRadius = 10.dp,
                        shadowOffset = 5.dp,
                        backgroundColor = AnydownColors.green
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "CONVERT PLAYLIST",
                    color = AnydownColors.onAccentDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Once you have the converted YouTube playlist link, paste it back into " +
                    "Anydown — it works just like any other playlist.",
                color = AnydownColors.textMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}
