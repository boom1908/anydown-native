package com.boom.anydown.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Neo-brutalist dark palette — matches the anydown-neobrutalism-dark.html
 * preview exactly: thick off-white borders/shadows on a deep charcoal base,
 * flat (non-gradient) accent colors.
 */
object AnydownColors {
    val background = Color(0xFF121319)
    val panel = Color(0xFF1B1C24)
    val ink = Color(0xFFF5F5F0)          // border + shadow color
    val textPrimary = Color(0xFFF5F5F0)
    val textMuted = Color(0xFF9A9DAE)
    val yellow = Color(0xFFFFD93D)
    val blue = Color(0xFF6E93FF)
    val green = Color(0xFF3ED9A0)
    val coral = Color(0xFFFF8367)
    val danger = Color(0xFFFF5D5D)
    val onAccentDark = Color(0xFF111111) // text color used on top of bright accents
}

private val AnydownDarkScheme = darkColorScheme(
    background = AnydownColors.background,
    surface = AnydownColors.panel,
    primary = AnydownColors.green,
    onBackground = AnydownColors.textPrimary,
    onSurface = AnydownColors.textPrimary
)

@Composable
fun AnydownTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AnydownDarkScheme, content = content)
}
