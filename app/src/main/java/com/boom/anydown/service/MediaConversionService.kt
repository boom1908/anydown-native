package com.boom.anydown.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.boom.anydown.MainActivity
import com.boom.anydown.util.CrashLogger
import com.boom.anydown.util.DownloadLocation
import com.boom.anydown.util.getFfmpegExecutablePath
import com.boom.anydown.util.saveToDownloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

enum class ConversionType(val label: String, val extension: String, val mimeType: String) {
    MP3("MP3 Audio", "mp3", "audio/mpeg"),
    H264("Universal H.264 Video", "mp4", "video/mp4")
}

data class ConversionQueueItem(
    val uri: Uri,
    val type: ConversionType
)

sealed class ConversionState {
    object Idle : ConversionState()
    data class Processing(
        val type: ConversionType,
        val fileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val filePercent: Int,
        val overallPercent: Int,
        val speed: String
    ) : ConversionState()
    data class Success(
        val type: ConversionType,
        val count: Int,
        val destinationPath: String
    ) : ConversionState()
    data class Error(
        val type: ConversionType,
        val errorMessage: String
    ) : ConversionState()
}

object MediaConversionManager {
    private val _state = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    internal fun updateState(newState: ConversionState) {
        _state.value = newState
    }

    fun startConversion(context: Context, sourceUri: Uri, type: ConversionType) {
        startBatchConversion(context, listOf(sourceUri), type)
    }

    fun startBatchConversion(context: Context, uris: List<Uri>, type: ConversionType) {
        if (uris.isEmpty()) return
        val uriStrings = ArrayList(uris.map { it.toString() })
        val intent = Intent(context, MediaConversionService::class.java).apply {
            action = MediaConversionService.ACTION_START_CONVERSION
            putStringArrayListExtra(MediaConversionService.EXTRA_URI_LIST, uriStrings)
            putExtra(MediaConversionService.EXTRA_TYPE, type.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancel(context: Context) {
        val intent = Intent(context, MediaConversionService::class.java).apply {
            action = MediaConversionService.ACTION_CANCEL_CONVERSION
        }
        context.startService(intent)
    }

    fun resetState() {
        _state.value = ConversionState.Idle
    }
}

class MediaConversionService : Service() {

    companion object {
        const val CHANNEL_ID = "media_conversion_channel"
        const val NOTIFICATION_ID = 2002
        const val COMPLETE_NOTIFICATION_ID = 2003

        const val ACTION_START_CONVERSION = "com.boom.anydown.action.START_CONVERSION"
        const val ACTION_CANCEL_CONVERSION = "com.boom.anydown.action.CANCEL_CONVERSION"

        const val EXTRA_URI_LIST = "extra_uri_list"
        const val EXTRA_TYPE = "extra_type"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var queueJob: Job? = null
    private var activeProcess: Process? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val taskQueue = ConcurrentLinkedQueue<ConversionQueueItem>()

    private var totalBatchSize = 0
    private var completedBatchCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_CONVERSION -> {
                cancelAllConversions("Conversion cancelled by user.")
                return START_NOT_STICKY
            }
            ACTION_START_CONVERSION -> {
                val uriStrings = intent.getStringArrayListExtra(EXTRA_URI_LIST)
                val typeName = intent.getStringExtra(EXTRA_TYPE)
                val type = runCatching { ConversionType.valueOf(typeName ?: "") }.getOrDefault(ConversionType.MP3)

                if (!uriStrings.isNullOrEmpty()) {
                    val newItems = uriStrings.map { ConversionQueueItem(Uri.parse(it), type) }
                    taskQueue.addAll(newItems)
                    totalBatchSize += newItems.size

                    // Immediately promote to Foreground Service synchronously
                    startForegroundSync(type)

                    // Start background drain if not running
                    if (queueJob == null || queueJob?.isActive != true) {
                        queueJob = serviceScope.launch {
                            drainConversionQueue()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSync(type: ConversionType) {
        val initialNotification = buildProgressNotification(
            type = type,
            fileName = "Preparing conversion…",
            currentFileIndex = completedBatchCount + 1,
            totalFiles = maxOf(totalBatchSize, 1),
            filePercent = 0,
            overallPercent = 0,
            speed = "starting…"
        )
        val fgType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or 0x00002000 // FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING (0x2000)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgType)
    }

    private suspend fun drainConversionQueue() {
        acquireWakeLock()
        var lastType = ConversionType.MP3

        try {
            while (true) {
                val item = taskQueue.poll() ?: break
                lastType = item.type
                val currentIndex = completedBatchCount + 1
                val totalCount = maxOf(totalBatchSize, 1)

                convertSingleItem(item.uri, item.type, currentIndex, totalCount)
                completedBatchCount++
            }

            val destination = DownloadLocation.displayName(this)
            MediaConversionManager.updateState(
                ConversionState.Success(lastType, completedBatchCount, destination)
            )
            showCompletedNotification(lastType, completedBatchCount, destination)

        } catch (e: Throwable) {
            if (e is InterruptedException) {
                CrashLogger.log("FFMPEG CONVERSION QUEUE INTERRUPTED")
            } else {
                CrashLogger.log("FFMPEG CONVERSION QUEUE ERROR: ${e.stackTraceToString()}")
                MediaConversionManager.updateState(
                    ConversionState.Error(lastType, e.message ?: "Conversion failed.")
                )
            }
        } finally {
            totalBatchSize = 0
            completedBatchCount = 0
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun convertSingleItem(
        sourceUri: Uri,
        type: ConversionType,
        currentIndex: Int,
        totalCount: Int
    ) {
        val fileName = queryDisplayName(sourceUri)
        val baseName = fileName.substringBeforeLast('.').ifBlank { "converted" }
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim()

        MediaConversionManager.updateState(
            ConversionState.Processing(
                type = type,
                fileName = fileName,
                currentFileIndex = currentIndex,
                totalFiles = totalCount,
                filePercent = 0,
                overallPercent = ((completedBatchCount * 100) / totalCount),
                speed = "starting…"
            )
        )

        var tempInputFile: File? = null
        var tempOutputFile: File? = null

        try {
            // 1. Copy source stream to temp input file
            tempInputFile = File.createTempFile("anydown-conv-in-", ".tmp", cacheDir)
            contentResolver.openInputStream(sourceUri)?.use { input ->
                tempInputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not read file: $fileName")

            // 2. Measure duration
            val totalDurationMs = queryMediaDurationMs(tempInputFile)

            // 3. Prepare temp output file
            val extension = type.extension
            tempOutputFile = File(cacheDir, "$baseName.$extension")
            tempOutputFile.delete()

            val ffmpegPath = getFfmpegExecutablePath(this)
            val ffmpegBin = File(ffmpegPath)
            if (!ffmpegBin.exists() || ffmpegBin.length() == 0L) {
                throw IllegalStateException("FFmpeg converter binary is missing (searched ${ffmpegBin.absolutePath}).")
            }

            // 4. Construct FFmpeg command
            val command = when (type) {
                ConversionType.MP3 -> listOf(
                    ffmpegBin.absolutePath,
                    "-y",
                    "-i", tempInputFile.absolutePath,
                    "-vn",
                    "-codec:a", "libmp3lame",
                    "-q:a", "2",
                    "-progress", "pipe:1",
                    tempOutputFile.absolutePath
                )
                ConversionType.H264 -> listOf(
                    ffmpegBin.absolutePath,
                    "-y",
                    "-i", tempInputFile.absolutePath,
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "22",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-movflags", "+faststart",
                    "-progress", "pipe:1",
                    tempOutputFile.absolutePath
                )
            }

            CrashLogger.log("FFMPEG CONVERSION START: type=${type.name} file=$fileName")

            val pb = ProcessBuilder(command).redirectErrorStream(false)
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val binDir = File(filesDir, "bin").absolutePath
            pb.environment()["LD_LIBRARY_PATH"] = "$nativeLibDir:$binDir:/system/lib64"

            val process = pb.start()
            activeProcess = process

            // 5. Read line by line progress
            var lastNotificationTime = 0L
            var lastPercent = 0
            var currentSpeed = "1.0x"

            val reader = process.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("out_time_us=")) {
                    val outTimeUs = line.substringAfter("out_time_us=").trim().toLongOrNull() ?: 0L
                    val currentTimeMs = outTimeUs / 1000L
                    if (totalDurationMs > 0) {
                        val filePercent = ((currentTimeMs * 100) / totalDurationMs).toInt().coerceIn(0, 99)
                        if (filePercent != lastPercent) {
                            lastPercent = filePercent
                            val overallPercent = (((completedBatchCount * 100) + filePercent) / totalCount).coerceIn(0, 99)

                            MediaConversionManager.updateState(
                                ConversionState.Processing(
                                    type = type,
                                    fileName = fileName,
                                    currentFileIndex = currentIndex,
                                    totalFiles = totalCount,
                                    filePercent = filePercent,
                                    overallPercent = overallPercent,
                                    speed = currentSpeed
                                )
                            )

                            val now = System.currentTimeMillis()
                            if (now - lastNotificationTime > 600L) {
                                lastNotificationTime = now
                                updateProgressNotification(
                                    type, fileName, currentIndex, totalCount, filePercent, overallPercent, currentSpeed
                                )
                            }
                        }
                    }
                } else if (line.startsWith("speed=")) {
                    val s = line.substringAfter("speed=").trim()
                    if (s.isNotBlank() && s != "N/A") {
                        currentSpeed = s
                    }
                } else if (line.startsWith("progress=end")) {
                    break
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0 || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                val errLog = process.errorStream.bufferedReader().use { it.readText() }
                CrashLogger.log("FFMPEG CONVERSION FAILED: exitCode=$exitCode errLog=$errLog")
                throw IllegalStateException("Conversion process failed (exit code $exitCode).")
            }

            // 6. Save output file
            saveToDownloads(this, tempOutputFile, type.mimeType)

        } finally {
            runCatching { tempInputFile?.delete() }
            runCatching { tempOutputFile?.delete() }
            activeProcess = null
        }
    }

    private fun cancelAllConversions(reason: String) {
        taskQueue.clear()
        activeProcess?.destroyForcibly()
        queueJob?.cancel()
        releaseWakeLock()
        MediaConversionManager.updateState(
            ConversionState.Error(ConversionType.MP3, reason)
        )
        totalBatchSize = 0
        completedBatchCount = 0
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anydown:conversion_lock")?.apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(1000L * 60 * 60 * 6) // up to 6 hours
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: "media_file"
    }

    private fun queryMediaDurationMs(file: File): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        }.getOrDefault(0L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Converter",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows live conversion progress for background media tasks"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(
        type: ConversionType,
        fileName: String,
        currentFileIndex: Int,
        totalFiles: Int,
        filePercent: Int,
        overallPercent: Int,
        speed: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, MediaConversionService::class.java).apply {
            action = ACTION_CANCEL_CONVERSION
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalFiles > 1) {
            "Converting ($currentFileIndex/$totalFiles): $fileName"
        } else {
            "Converting: $fileName"
        }

        val subtext = if (totalFiles > 1) {
            "$filePercent% (Overall: $overallPercent%) • Speed: $speed"
        } else {
            if (filePercent > 0) "$filePercent% • Speed: $speed" else "Processing…"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtext)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, if (totalFiles > 1) overallPercent else filePercent, filePercent == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateProgressNotification(
        type: ConversionType,
        fileName: String,
        currentFileIndex: Int,
        totalFiles: Int,
        filePercent: Int,
        overallPercent: Int,
        speed: String
    ) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(
            NOTIFICATION_ID,
            buildProgressNotification(type, fileName, currentFileIndex, totalFiles, filePercent, overallPercent, speed)
        )
    }

    private fun showCompletedNotification(type: ConversionType, count: Int, destination: String) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count > 1) "Batch Conversion Complete!" else "Conversion Complete!"
        val text = if (count > 1) {
            "$count files converted to ${type.label} in $destination"
        } else {
            "Saved to $destination"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        releaseWakeLock()
        activeProcess?.destroyForcibly()
        queueJob?.cancel()
        super.onDestroy()
    }
}
