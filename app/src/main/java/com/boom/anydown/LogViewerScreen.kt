package com.boom.anydown

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LogViewerScreen() {
    val logText = remember { mutableStateOf(CrashLogger.readLogs()) }
    val context = LocalContext.current

    Column(Modifier.padding(16.dp)) {
        Text(logText.value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, logText.value)
                }
                context.startActivity(Intent.createChooser(intent, "Share log"))
            }) { Text("Share Log") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { CrashLogger.clear(); logText.value = "" }) { Text("Clear") }
        }
    }
}
