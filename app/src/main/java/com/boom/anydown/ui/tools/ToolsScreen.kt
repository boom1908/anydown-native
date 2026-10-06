package com.boom.anydown.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boom.anydown.service.ConversionState
import com.boom.anydown.service.ConversionType
import com.boom.anydown.service.MediaConversionManager
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val conversionState by MediaConversionManager.state.collectAsState()
    val isConverting = conversionState is ConversionState.Processing

    // Check battery optimization status
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(checkBatteryOptimization(context))
    }

    // MP3 Multi-file Picker
    val mp3Picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            MediaConversionManager.startBatchConversion(context, uris, ConversionType.MP3)
        }
    }

    // H264 Universal Video Multi-file Picker
    val h264Picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            MediaConversionManager.startBatchConversion(context, uris, ConversionType.H264)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnydownColors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "TOOLS",
            color = AnydownColors.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = "Background offline media utilities",
            color = AnydownColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(18.dp))

        // Tool 1: Convert to MP3 (Supports Multiple Selection)
        ToolCard(
            title = "CONVERT TO MP3",
            subtitle = "Extract audio into MP3 • Select single or multiple files at once for batch conversion.",
            icon = Icons.Filled.AudioFile,
            iconTint = AnydownColors.green,
            buttonText = "CHOOSE FILES",
            buttonColor = AnydownColors.yellow,
            enabled = !isConverting,
            onPick = { mp3Picker.launch(arrayOf("audio/*", "video/*")) }
        )

        Spacer(Modifier.height(14.dp))

        // Tool 2: Convert to Universal H.264 (Supports Multiple Selection)
        ToolCard(
            title = "CONVERT TO H.264",
            subtitle = "Re-encode videos to standard 8-bit H.264 (MP4) • Select single or multiple videos for batch conversion.",
            icon = Icons.Filled.Movie,
            iconTint = AnydownColors.yellow,
            buttonText = "CHOOSE VIDEOS",
            buttonColor = AnydownColors.green,
            enabled = !isConverting,
            onPick = { h264Picker.launch(arrayOf("video/*")) }
        )

        Spacer(Modifier.height(20.dp))

        // Live Conversion Progress / Status Section
        when (val state = conversionState) {
            is ConversionState.Processing -> {
                ProcessingCard(
                    state = state,
                    onCancel = { MediaConversionManager.cancel(context) }
                )
            }
            is ConversionState.Success -> {
                val titleText = if (state.count > 1) "BATCH CONVERSION COMPLETE" else "CONVERSION COMPLETE"
                val detailText = if (state.count > 1) {
                    "${state.count} files were converted to ${state.type.label} and saved to ${state.destinationPath}"
                } else {
                    "File converted and saved to ${state.destinationPath}"
                }
                StatusCard(
                    icon = Icons.Filled.CheckCircle,
                    iconTint = AnydownColors.green,
                    title = titleText,
                    detail = detailText,
                    onDismiss = { MediaConversionManager.resetState() }
                )
            }
            is ConversionState.Error -> {
                StatusCard(
                    icon = Icons.Filled.Error,
                    iconTint = AnydownColors.danger,
                    title = "CONVERSION FAILED",
                    detail = state.errorMessage,
                    onDismiss = { MediaConversionManager.resetState() }
                )
            }
            ConversionState.Idle -> {
                // Background task guarantee badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brutalistBox(cornerRadius = 10.dp, shadowOffset = 3.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = AnydownColors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Conversions run safely in the background with lock-screen progress notifications and CPU keep-alive.",
                        color = AnydownColors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Battery Optimization Helper Card
        if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .brutalistBox(cornerRadius = 12.dp, shadowOffset = 4.dp)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = AnydownColors.coral,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BACKGROUND ENCODING LOCK",
                        color = AnydownColors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Tap to exempt Anydown from system sleep so 2-3 hour movies never pause when the screen turns off.",
                        color = AnydownColors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .brutalistClickable(
                            onClick = {
                                requestBatteryExemption(context)
                                isIgnoringBatteryOptimizations = checkBatteryOptimization(context)
                            },
                            cornerRadius = 8.dp,
                            shadowOffset = 2.dp,
                            backgroundColor = AnydownColors.coral
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ALLOW", color = AnydownColors.onAccentDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun checkBatteryOptimization(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } else {
        true
    }
}

private fun requestBatteryExemption(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(fallbackIntent)
            } catch (_: Throwable) {}
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    buttonText: String,
    buttonColor: Color,
    enabled: Boolean,
    onPick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalistBox(cornerRadius = 12.dp, shadowOffset = 5.dp)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AnydownColors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AnydownColors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistClickable(
                    onClick = onPick,
                    cornerRadius = 8.dp,
                    shadowOffset = 3.dp,
                    backgroundColor = if (enabled) buttonColor else AnydownColors.panel
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (enabled) buttonText else "CONVERSION IN PROGRESS…",
                color = if (enabled) AnydownColors.onAccentDark else AnydownColors.textMuted,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ProcessingCard(
    state: ConversionState.Processing,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalistBox(cornerRadius = 12.dp, shadowOffset = 5.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val header = if (state.totalFiles > 1) {
                    "${state.type.label.uppercase()} (${state.currentFileIndex}/${state.totalFiles})"
                } else {
                    state.type.label.uppercase()
                }
                Text(
                    text = header,
                    color = AnydownColors.yellow,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
                Text(
                    text = state.fileName,
                    color = AnydownColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            val percentDisplay = if (state.totalFiles > 1) {
                "${state.overallPercent}%"
            } else {
                "${state.filePercent}%"
            }
            Text(
                text = percentDisplay,
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // Brutalist Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AnydownColors.panel)
                .border(2.dp, AnydownColors.ink, RoundedCornerShape(6.dp))
        ) {
            val fraction = if (state.totalFiles > 1) {
                (state.overallPercent / 100f).coerceIn(0f, 1f)
            } else {
                (state.filePercent / 100f).coerceIn(0f, 1f)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(AnydownColors.yellow)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusDetail = if (state.totalFiles > 1) {
                "File ${state.filePercent}% • Speed: ${state.speed}"
            } else {
                "Speed: ${state.speed} • Background running"
            }
            Text(
                text = statusDetail,
                color = AnydownColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Box(
                modifier = Modifier
                    .brutalistClickable(
                        onClick = onCancel,
                        cornerRadius = 6.dp,
                        shadowOffset = 2.dp,
                        backgroundColor = AnydownColors.danger
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCEL",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    detail: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalistBox(cornerRadius = 12.dp, shadowOffset = 4.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AnydownColors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    color = AnydownColors.textMuted,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = AnydownColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
