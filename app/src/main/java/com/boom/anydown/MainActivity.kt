package com.boom.anydown

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.boom.anydown.ui.AnydownApp
import com.boom.anydown.ui.theme.AnydownTheme
import com.boom.anydown.util.CrashLogger
import com.boom.anydown.viewmodel.AnydownViewModel
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AnydownViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(this)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        
        viewModel = ViewModelProvider(this)[AnydownViewModel::class.java]
        
        handleSharedLink(intent)

        setContent {
            AnydownTheme { AnydownApp() }
        }
    }

    // Wait for the window to actually be in focus before checking clipboard (Android 12+ requirement)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            checkClipboardForLinks()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedLink(intent)
    }

    private fun handleSharedLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            extractAndSetUrl(sharedText)
        }
    }

    private fun checkClipboardForLinks() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    
                    if (text.contains("youtube.com") || text.contains("youtu.be") || 
                        text.contains("instagram.com") || text.contains("instagr.am")) {
                        extractAndSetUrl(text)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore clipboard access errors on strict Android versions
        }
    }

    private fun extractAndSetUrl(text: String) {
        // Use Android's native web URL parser which handles complex symbols like '==' perfectly
        val matcher = android.util.Patterns.WEB_URL.matcher(text)
        if (matcher.find()) {
            viewModel.onLinkChanged(matcher.group())
        } else {
            viewModel.onLinkChanged(text)
        }
    }
}
