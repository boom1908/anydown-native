package com.boom.anydown.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle

val SpaceGroteskFamily = FontFamily.SansSerif
val JetBrainsMonoFamily = FontFamily.Monospace

object AnydownType {
    val Display = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold)
    val Mono = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium)
}
