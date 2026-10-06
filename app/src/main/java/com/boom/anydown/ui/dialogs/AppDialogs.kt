package com.boom.anydown.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

/** Update-available popup. Dismissing it is remembered per version tag. */
@Composable
fun UpdateAvailableDialog(
    versionLabel: String,
    onGetUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 14.dp, shadowOffset = 7.dp)
                .padding(20.dp)
        ) {
            Text(
                "🚀 New Version Available",
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "v$versionLabel is ready! Grab the update to keep things running smooth.",
                color = AnydownColors.textMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .brutalistClickable(
                        onClick = onGetUpdate,
                        cornerRadius = 10.dp,
                        shadowOffset = 5.dp,
                        backgroundColor = AnydownColors.green
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GET THE UPDATE",
                    color = AnydownColors.onAccentDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .brutalistClickable(onClick = onDismiss, cornerRadius = 10.dp, shadowOffset = 4.dp)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("MAYBE LATER", color = AnydownColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Shown once, on first launch, immediately before the system's Android 13+
 * notification permission prompt so it doesn't arrive without context.
 */
@Composable
fun NotificationPrePromptDialog(onContinue: () -> Unit) {
    Dialog(onDismissRequest = onContinue) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 14.dp, shadowOffset = 7.dp)
                .padding(20.dp)
        ) {
            Text(
                "🔔 Stay in the Loop",
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "We'll ask for notification access next, so your downloads keep working " +
                    "smoothly in the background and you never miss an update.",
                color = AnydownColors.textMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .brutalistClickable(
                        onClick = onContinue,
                        cornerRadius = 10.dp,
                        shadowOffset = 5.dp,
                        backgroundColor = AnydownColors.yellow
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GOT IT",
                    color = AnydownColors.onAccentDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
