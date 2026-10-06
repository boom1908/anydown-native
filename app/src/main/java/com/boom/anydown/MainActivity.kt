package com.boom.anydown

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.boom.anydown.ui.AnydownApp
import com.boom.anydown.ui.SplashScreen
import com.boom.anydown.ui.dialogs.NotificationPrePromptDialog
import com.boom.anydown.ui.dialogs.UpdateAvailableDialog
import com.boom.anydown.ui.theme.AnydownTheme
import com.boom.anydown.util.CrashLogger
import com.boom.anydown.util.OnboardingPrefs
import com.boom.anydown.util.UpdateChecker
import com.boom.anydown.util.UpdateInfo
import com.boom.anydown.viewmodel.AnydownViewModel
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AnydownViewModel

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* downloads run either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(this)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        viewModel = ViewModelProvider(this)[AnydownViewModel::class.java]
        // Warm the Python module off the main thread; the splash stays up
        // until it's ready so the first fetch isn't the one that pays for it.
        val engineReady = mutableStateOf(false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { Python.getInstance().getModule("downloader") }
            }
            delay(400) // let the brand frame breathe instead of flashing
            engineReady.value = true
        }

        handleSharedLink(intent)

        setContent {
            AnydownTheme {
                val ready by engineReady
                if (!ready) {
                    SplashScreen()
                    return@AnydownTheme
                }

                AnydownApp()

                // First launch: explain why we're about to ask, then ask.
                var showPrePrompt by remember {
                    mutableStateOf(needsNotificationPermission() && !OnboardingPrefs.hasSeenNotificationPrePrompt(this))
                }
                if (showPrePrompt) {
                    NotificationPrePromptDialog(onContinue = {
                        OnboardingPrefs.markNotificationPrePromptSeen(this)
                        showPrePrompt = false
                        requestNotificationPermissionIfNeeded()
                    })
                } else {
                    LaunchedEffect(Unit) {
                        if (OnboardingPrefs.hasSeenNotificationPrePrompt(this@MainActivity)) {
                            requestNotificationPermissionIfNeeded()
                        }
                    }
                }

                // Update detection (throttled to ~once a day inside the checker).
                var update by remember { mutableStateOf<UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val info = UpdateChecker.check(this@MainActivity) ?: return@LaunchedEffect
                    update = info
                    UpdateChecker.notifyUpdate(this@MainActivity, info)
                    showUpdateDialog = !UpdateChecker.wasDismissed(this@MainActivity, info.tag)
                }
                val info = update
                if (showUpdateDialog && info != null) {
                    UpdateAvailableDialog(
                        versionLabel = info.versionLabel,
                        onGetUpdate = {
                            showUpdateDialog = false
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                        },
                        onDismiss = {
                            showUpdateDialog = false
                            UpdateChecker.markDismissed(this@MainActivity, info.tag)
                        }
                    )
                }
            }
        }
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Android 13+ needs runtime consent before the download service's progress
     * notification can be shown. Denial only hides the notification.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (!needsNotificationPermission()) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    private fun extractAndSetUrl(text: String) {
        val matcher = android.util.Patterns.WEB_URL.matcher(text)
        if (matcher.find()) {
            viewModel.onLinkChanged(matcher.group())
        } else {
            viewModel.onLinkChanged(text)
        }
    }
}
