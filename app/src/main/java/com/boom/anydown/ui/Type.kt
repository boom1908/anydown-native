package com.boom.anydown.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.boom.anydown.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val spaceGrotesk = GoogleFont("Space Grotesk")
val jetBrainsMono = GoogleFont("JetBrains Mono")

val SpaceGroteskFamily = FontFamily(
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Bold)
)

object AnydownType {
    val Display = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold)
    val Mono = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium)
}
