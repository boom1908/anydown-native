package com.boom.anydown.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.boom.anydown.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tag: String,
    val versionLabel: String,
    val url: String
)

/**
 * Update detection against the public GitHub releases API.
 *
 * No auth, no credentials: the repo is public, so the anonymous endpoint is
 * enough. The check is throttled to roughly once a day to stay well inside
 * GitHub's unauthenticated rate limits, and every failure is swallowed — a
 * missing update check must never affect the app.
 */
object UpdateChecker {
    private const val PREFS = "anydown_updates"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"
    private const val KEY_CACHED_TAG = "cached_tag"
    private const val KEY_CACHED_URL = "cached_url"

    private const val CHANNEL_ID = "anydown_updates"
    private const val NOTIFICATION_ID = 2001
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    const val RELEASES_URL = "https://api.github.com/repos/boom1908/anydown-native/releases/latest"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Returns an update when one is available, honouring the once-a-day
     * throttle. When throttled, the last known result is reused.
     */
    suspend fun check(context: Context, force: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val p = prefs(context)
            val now = System.currentTimeMillis()
            val due = force || now - p.getLong(KEY_LAST_CHECK, 0L) > CHECK_INTERVAL_MS

            val info = if (due) {
                // Record the attempt before networking so an offline launch does
                // not retry the same request on every Activity recreation.
                p.edit().putLong(KEY_LAST_CHECK, now).apply()
                fetchLatest()?.also {
                    p.edit()
                        .putString(KEY_CACHED_TAG, it.tag)
                        .putString(KEY_CACHED_URL, it.url)
                        .apply()
                }
            } else {
                val tag = p.getString(KEY_CACHED_TAG, null)
                val url = p.getString(KEY_CACHED_URL, null)
                if (tag != null && url != null) UpdateInfo(tag, tag.trimStart('v', 'V'), url) else null
            }

            if (info != null && isNewer(info.tag, BuildConfig.VERSION_NAME)) info else null
        }

    private fun fetchLatest(): UpdateInfo? = try {
        val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Anydown-Android")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(body)
        val tag = json.optString("tag_name").orEmpty()
        if (tag.isBlank()) {
            null
        } else {
            // Prefer a direct APK asset when the release ships one.
            var link = json.optString("html_url").orEmpty()
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        link = a.optString("browser_download_url", link)
                        break
                    }
                }
            }
            if (link.isBlank()) null else UpdateInfo(tag, tag.trimStart('v', 'V'), link)
        }
    } catch (e: Throwable) {
        CrashLogger.log("UPDATE CHECK FAILED: ${e.message}")
        null
    }

    /** Numeric-segment comparison; unparsable versions simply mean "no update". */
    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V')
            .split('-', '+')[0]
            .split('.')
            .mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val remote = parts(remoteTag)
        val current = parts(currentVersion)
        if (remote.isEmpty() || current.isEmpty()) return false
        for (i in 0 until maxOf(remote.size, current.size)) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    // --- "don't nag" bookkeeping ------------------------------------------

    fun wasDismissed(context: Context, tag: String): Boolean =
        prefs(context).getString(KEY_DISMISSED_TAG, null) == tag

    fun markDismissed(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_DISMISSED_TAG, tag).apply()
    }

    // --- system notification ----------------------------------------------

    fun notifyUpdate(context: Context, info: UpdateInfo) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Tells you when a new Anydown release is out" }
            )
        }

        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(info.url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🚀 New version available")
            .setContentText("v${info.versionLabel} is ready — tap to grab the update.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            CrashLogger.log("Update notification blocked: ${e.message}")
        }
    }
}

/** First-launch bookkeeping for the notification-permission explainer. */
object OnboardingPrefs {
    private const val PREFS = "anydown_onboarding"
    private const val KEY_SEEN_NOTIF_PREPROMPT = "seen_notif_preprompt"

    fun hasSeenNotificationPrePrompt(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SEEN_NOTIF_PREPROMPT, false)

    fun markNotificationPrePromptSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN_NOTIF_PREPROMPT, true).apply()
    }
}
