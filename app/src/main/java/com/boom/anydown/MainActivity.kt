package com.boom.anydown
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.boom.anydown.ui.AnydownApp
import com.boom.anydown.ui.theme.AnydownTheme
import com.boom.anydown.util.CrashLogger
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(this)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContent {
            AnydownTheme { AnydownApp() }
        }
    }
}
