package com.boom.anydown.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalShell(title: String, content: @Composable ColumnScope.() -> Unit) {
    NeoBrutalBox(shadowOffset = 8.dp) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AnydownColors.Yellow)
                    .border(BorderStroke(0.dp, Color.Transparent))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(AnydownColors.Coral)
                    Dot(Color(0xFF111111))
                    Dot(AnydownColors.Green)
                }
                Spacer(Modifier.width(14.dp))
                Text(title, color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, fontFamily = SpaceGroteskFamily)
            }
            Column(Modifier.padding(24.dp), content = content)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .background(color, CircleShape)
            .border(2.dp, AnydownColors.Ink, CircleShape)
    )
}
