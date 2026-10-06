package com.boom.anydown.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.boom.anydown.R
import com.boom.anydown.ui.theme.AnydownColors as Palette

/**
 * Branded launch screen: logo mark, wordmark and a brutalist pulsing loader
 * shown while the Python/yt-dlp engine boots. Nothing else.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.anydown_logo_padded),
                contentDescription = "Anydown",
                modifier = Modifier.size(104.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "ANYDOWN",
                color = Palette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(26.dp))
            PulsingBlocks()
        }
    }
}

@Composable
private fun PulsingBlocks() {
    val transition = rememberInfiniteTransition(label = "splash-loader")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.18f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 130),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "block-$index"
            )
            Box(
                Modifier
                    .width(22.dp)
                    .height(7.dp)
                    .alpha(alpha)
                    .background(Palette.yellow)
            )
        }
    }
}
