package com.boom.anydown

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
        
        // Grab the ViewModel so we can push data to the UI
        viewModel = ViewModelProvider(this)[AnydownViewModel::class.java]
        
        // 1. Check if app was opened via the OS Share Menu
        handleSharedLink(intent)

        setContent {
            AnydownTheme { AnydownApp() }
        }
    }

    // 2. Catch the link if the app is already open in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedLink(intent)
    }

    // 3. Extract the URL and drop it into the search bar
    private fun handleSharedLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            
            // Apps like YouTube sometimes share junk text like "Check out this video: https://..." 
            // This Regex perfectly extracts just the clean URL.
            val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\((?:[^\\s()<>]+|\\([^\\s()<>]+\\))*\\))+(?:\\((?:[^\\s()<>]+|\\([^\\s()<>]+\\))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
            val match = urlRegex.find(sharedText)
            val url = match?.value ?: sharedText

            // Push it instantly to your UI!
            viewModel.onLinkChanged(url)
        }
    }
}
