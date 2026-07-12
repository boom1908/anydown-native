package com.boom.anydown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.boom.anydown.ui.AnydownApp
import com.boom.anydown.ui.theme.AnydownTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnydownTheme {
                AnydownApp()
            }
        }
    }
}
