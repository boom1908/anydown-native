package com.boom.anydown.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.boom.anydown.MainActivity
import com.boom.anydown.model.DownloadRequest
import com.boom.anydown.model.DownloadStatus
import com.boom.anydown.util.CrashLogger
import com.boom.anydown.util.ProgressCallback
import com.boom.anydown.util.getFfmpegExecutablePath
import com.boom.anydown.util.saveToDownloads
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that owns the download queue.
 *
 * Everything — single videos and playlist batches alike — goes through here so
 * downloads keep running when the app is backgrounded or the screen is locked.
 *
 * Foreground service type is `dataSync`: on Android 14+ a type is mandatory,
 * and dataSync is the type for app-initiated transfer of files to/from the
 * network. `mediaProcessing` covers transcoding of media already on the device,
 * which is not what this is.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false
    private var lastProgressPublish = 0L
    private var lastPublishedProgress = -1
    private var lastPublishedStatus: DownloadStatus? = null
    private var lastPublishedStage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        createChannel()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        DownloadQueue.ensureLoaded(this)
    }

    private var sessionSuccessCount = 0
    private var sessionFailureCount = 0
    private var sessionLastFailedTitle: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification("Preparing downloads…", null, 0, indeterminate = true))
        if (!running) {
            running = true
            sessionSuccessCount = 0
            sessionFailureCount = 0
            sessionLastFailedTitle = null
            scope.launch { drainQueue() }
        }
        return START_STICKY
    }

    private suspend fun drainQueue() {
        try {
            while (true) {
                val request = DownloadQueue.nextRequest() ?: break
                processRequest(request)
                DownloadQueue.onItemFinished()
            }
        } finally {
            running = false
            DownloadQueue.onQueueDrained()
            val finalMessage = when {
                sessionSuccessCount > 0 && sessionFailureCount == 0 -> {
                    if (sessionSuccessCount == 1) "Download complete" else "$sessionSuccessCount downloads complete"
                }
                sessionSuccessCount > 0 && sessionFailureCount > 0 -> {
                    "$sessionSuccessCount completed, $sessionFailureCount failed"
                }
                sessionSuccessCount == 0 && sessionFailureCount > 0 -> {
                    "Download failed: ${sessionLastFailedTitle ?: "Error downloading"}"
                }
                else -> "Downloads finished"
            }
            notify(buildNotification(finalMessage, null, 0, ongoing = false))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun processRequest(request: DownloadRequest) {
        if (DownloadQueue.isCancelled(request.id)) {
            DownloadQueue.updateItem(this, request.id, persist = true) {
                it.copy(status = DownloadStatus.CANCELLED)
            }
            return
        }

        DownloadQueue.publishActive(request.title, 0, DownloadStatus.DOWNLOADING)
        DownloadQueue.updateItem(this, request.id, persist = false) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = 0,
                formatId = request.formatId,
                failureReason = null,
                stageText = null,
                statusDetail = null
            )
        }
        updateProgressNotification(request.title, 0, DownloadStatus.DOWNLOADING, force = true)
        lastProgressPublish = SystemClock.elapsedRealtime()
        lastPublishedProgress = 0
        lastPublishedStatus = DownloadStatus.DOWNLOADING
        lastPublishedStage = null

        try {
            val py = Python.getInstance()
            val ffmpegExecutable = getFfmpegExecutablePath(this)
            val outputDir = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
            var lastStage = ""
            var shownProgress = 0

            val callback = object : ProgressCallback {
                override fun onProgress(percent: Int, status: String) {
                    onProgressDetails(percent, status, "", "", 0L, 0L, 0L)
                }

                override fun onProgressDetails(
                    percent: Int,
                    status: String,
                    stageText: String,
                    statusDetail: String,
                    downloadedBytes: Long,
                    totalBytes: Long,
                    speedBytes: Long
                ) {
                    val isProcessing = status == "processing" || status == "merging"
                    val mapped = if (isProcessing) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING

                    // Option B: Reset progress bar when transitioning between video (Step 1) and audio (Step 2)
                    if (status != lastStage) {
                        lastStage = status
                        shownProgress = percent.coerceIn(0, 100)
                    } else {
                        shownProgress = maxOf(shownProgress, percent.coerceIn(0, 100))
                    }

                    val now = SystemClock.elapsedRealtime()
                    val statusChanged = mapped != lastPublishedStatus
                    val stageChanged = stageText != lastPublishedStage
                    val progressChanged = shownProgress != lastPublishedProgress
                    val intervalElapsed = now - lastProgressPublish >= PROGRESS_THROTTLE_MS
                    if (!statusChanged && !stageChanged && (!progressChanged || !intervalElapsed) && shownProgress < 100) return

                    lastProgressPublish = now
                    lastPublishedProgress = shownProgress
                    lastPublishedStatus = mapped
                    lastPublishedStage = stageText

                    DownloadQueue.updateItem(this@DownloadService, request.id, persist = false) {
                        it.copy(
                            progress = shownProgress,
                            status = mapped,
                            stageText = stageText.ifBlank { null },
                            statusDetail = statusDetail.ifBlank { null }
                        )
                    }
                    DownloadQueue.publishActive(
                        request.title,
                        shownProgress,
                        mapped,
                        stageText = stageText.ifBlank { null },
                        statusDetail = statusDetail.ifBlank { null }
                    )
                    updateProgressNotification(
                        title = request.title,
                        percent = shownProgress,
                        status = mapped,
                        stageText = stageText,
                        statusDetail = statusDetail,
                        force = true
                    )
                }

                override fun isCancelled(): Boolean = DownloadQueue.isCancelled(request.id)
            }

            val resultPath = py.getModule("downloader")
                .callAttr("fetch_video", request.url, ffmpegExecutable, outputDir, request.formatId, callback)
                .toString()

            val file = File(resultPath)
            val mime = if (request.formatId == "audio") "audio/m4a" else "video/mp4"
            val uri = saveToDownloads(this, file, mime)
            val sizeMb = if (file.exists()) (file.length() / (1024 * 1024)).toInt() else 0
            if (file.exists()) file.delete()

            DownloadQueue.updateItem(this, request.id, persist = true) {
                it.copy(
                    filePath = uri,
                    sizeMb = sizeMb,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    formatId = request.formatId,
                    failureReason = null
                )
            }
            sessionSuccessCount++
        } catch (e: Throwable) {
            // A single failure must never take the rest of the queue down with it.
            CrashLogger.log("DOWNLOAD FAILED (${request.title}): ${e.message}")
            val wasCancelled = DownloadQueue.isCancelled(request.id) ||
                (e is PyException && e.message?.contains("cancel", ignoreCase = true) == true)
            if (!wasCancelled) {
                sessionFailureCount++
                sessionLastFailedTitle = request.title
            }
            DownloadQueue.updateItem(this, request.id, persist = true) {
                it.copy(
                    status = if (wasCancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                    failureReason = if (wasCancelled) null else friendlyFailureReason(request, e)
                )
            }
        }
    }

    private fun friendlyFailureReason(request: DownloadRequest, error: Throwable): String {
        val text = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        val formatUnavailable = listOf(
            "requested format is not available",
            "format is not available",
            "requested format not available",
            "no video formats found",
            "unable to download format"
        ).any { text.contains(it) }

        if (!formatUnavailable) {
            return "Something went wrong with this one — give it another try"
        }

        return when (request.formatId) {
            "audio" -> "Audio Only isn't available for this video — try Best Quality or Fast Download instead"
            "fast" -> "Fast Download isn't available for this video — try Best Quality or Audio Only instead"
            else -> "Best Quality isn't available for this video — try Audio Only or Fast Download instead"
        }
    }

    // --- notification ------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows progress while Anydown downloads your media"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateProgressNotification(
        title: String,
        percent: Int,
        status: DownloadStatus,
        stageText: String = "",
        statusDetail: String = "",
        force: Boolean = false
    ) {
        val info = DownloadQueue.queueInfo.value
        val isMerging = status == DownloadStatus.PROCESSING
        val stage = stageText.ifBlank {
            if (isMerging) "Merging audio & video..." else "Downloading"
        }
        val detail = statusDetail.ifBlank { "$percent%" }
        val subtitleContent = if (stage == detail) stage else "$stage · $detail"

        val subtitle = if (info.batchTotal > 1) {
            "Item ${info.positionInBatch} of ${info.batchTotal} · $subtitleContent"
        } else {
            subtitleContent
        }
        val heading = if (isMerging) "Processing: $title" else "Downloading: $title"
        notify(buildNotification(heading, subtitle, percent, indeterminate = isMerging))
    }

    private fun buildNotification(
        title: String,
        subtitle: String?,
        progress: Int,
        indeterminate: Boolean = false,
        ongoing: Boolean = true
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (subtitle != null) builder.setContentText(subtitle)
        if (ongoing) {
            builder.setProgress(100, progress.coerceIn(0, 100), indeterminate)
        } else {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            builder.setAutoCancel(true)
        }
        return builder.build()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+ — downloads still run.
            CrashLogger.log("Notification blocked: ${e.message}")
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    companion object {
        const val CHANNEL_ID = "anydown_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_THROTTLE_MS = 500L
    }
}
