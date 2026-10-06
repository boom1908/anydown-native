package com.boom.anydown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.brutalist.brutalistClickable
import com.boom.anydown.ui.theme.AnydownColors

@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val logText = produceState(initialValue = "Loading logs…") {
        value = CrashLogger.readLogs()
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnydownColors.background)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = AnydownColors.textPrimary)
            }
            Text(
                "DEBUG LOGS",
                color = AnydownColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .brutalistClickable(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logText.value))
                            android.widget.Toast.makeText(context, "Logs copied", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        cornerRadius = 6.dp,
                        shadowOffset = 3.dp,
                        backgroundColor = AnydownColors.yellow
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = AnydownColors.onAccentDark, modifier = Modifier.size(15.dp))
                    Text("COPY", color = AnydownColors.onAccentDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .brutalistBox(cornerRadius = 8.dp, shadowOffset = 4.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = logText.value,
                    color = AnydownColors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
