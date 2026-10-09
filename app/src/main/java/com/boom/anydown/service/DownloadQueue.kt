package com.boom.anydown.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.boom.anydown.model.DownloadRequest
import com.boom.anydown.model.DownloadStatus
import com.boom.anydown.model.DownloadedItem
import com.boom.anydown.util.CrashLogger
import com.boom.anydown.util.HistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide download queue.
 *
 * The queue itself lives here (not in the Service instance) so that state
 * survives the service being stopped and restarted, and so the UI can observe
 * it without needing to bind. DownloadService is the only consumer that pops
 * items off; everyone else just enqueues and observes.
 *
 * Downloads run strictly one at a time — this is intentional, for battery and
 * network stability on mobile.
 */
object DownloadQueue {

    /** Full history + in-flight items, newest first. Drives the Downloads screen. */
    private val _items = MutableStateFlow<List<DownloadedItem>>(emptyList())
    val items: StateFlow<List<DownloadedItem>> = _items.asStateFlow()

    /** Progress of the queue as a whole — used for the notification text. */
    data class QueueInfo(
        val activeTitle: String? = null,
        val activeProgress: Int = 0,
        val activeStatus: DownloadStatus = DownloadStatus.QUEUED,
        val activeStageText: String? = null,
        val activeStatusDetail: String? = null,
        val positionInBatch: Int = 0,
        val batchTotal: Int = 0
    ) {
        val isActive: Boolean get() = activeTitle != null
    }

    private val _queueInfo = MutableStateFlow(QueueInfo())
    val queueInfo: StateFlow<QueueInfo> = _queueInfo.asStateFlow()

    private val lock = Any()
    private val pending = ArrayDeque<DownloadRequest>()
    private val cancelled = mutableSetOf<String>()
    private var batchTotal = 0
    private var batchDone = 0
    private var loaded = false

    /**
     * Loads persisted history once per process. Anything left mid-flight by a
     * process death is marked FAILED, matching the previous ViewModel behaviour.
     */
    fun ensureLoaded(context: Context) {
        synchronized(lock) {
            if (loaded) return
            loaded = true
        }
        val appContext = context.applicationContext
        val restored = HistoryManager.load(appContext).map { item ->
            if (item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.PROCESSING ||
                item.status == DownloadStatus.QUEUED
            ) {
                item.copy(status = DownloadStatus.FAILED, progress = 0)
            } else item
        }
        _items.value = restored
        if (restored.any { it.status == DownloadStatus.FAILED }) {
            HistoryManager.save(appContext, restored)
        }
    }

    /**
     * Adds a batch of downloads and makes sure the foreground service is running.
     * A single-video download is simply a batch of one — there is no separate path.
     */
    fun enqueue(context: Context, requests: List<DownloadRequest>) {
        if (requests.isEmpty()) return
        val appContext = context.applicationContext
        ensureLoaded(appContext)

        val newItems = requests.map {
            DownloadedItem(
                id = it.id,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                sizeMb = 0,
                filePath = "",
                status = DownloadStatus.QUEUED,
                progress = 0,
                formatId = it.formatId,
                url = it.url,
                batchId = it.batchId,
                batchTitle = it.batchTitle
            )
        }
        _items.value = newItems.reversed() + _items.value
        HistoryManager.save(appContext, _items.value)

        synchronized(lock) {
            pending.addAll(requests)
            batchTotal += requests.size
        }
        publishQueueInfo()

        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, DownloadService::class.java)
        )
    }

    // --- consumed by DownloadService -------------------------------------

    internal fun nextRequest(): DownloadRequest? {
        synchronized(lock) {
            while (pending.isNotEmpty()) {
                val next = pending.removeFirst()
                if (cancelled.contains(next.id)) {
                    cancelled.remove(next.id)
                    batchDone++
                    continue
                }
                return next
            }
            return null
        }
    }

    internal fun onItemFinished() {
        synchronized(lock) { batchDone++ }
    }

    internal fun onQueueDrained() {
        synchronized(lock) {
            batchTotal = 0
            batchDone = 0
            cancelled.clear()
        }
        _queueInfo.value = QueueInfo()
    }

    internal fun publishActive(
        title: String,
        progress: Int,
        status: DownloadStatus,
        stageText: String? = null,
        statusDetail: String? = null
    ) {
        val position: Int
        val total: Int
        synchronized(lock) {
            position = batchDone + 1
            total = batchTotal
        }
        _queueInfo.value = QueueInfo(
            activeTitle = title,
            activeProgress = progress,
            activeStatus = status,
            activeStageText = stageText,
            activeStatusDetail = statusDetail,
            positionInBatch = position,
            batchTotal = total
        )
    }

    private fun publishQueueInfo() {
        val current = _queueInfo.value
        val total: Int
        val position: Int
        synchronized(lock) {
            total = batchTotal
            position = batchDone + 1
        }
        _queueInfo.value = current.copy(positionInBatch = position, batchTotal = total)
    }

    // --- item mutation ----------------------------------------------------

    fun updateItem(context: Context, id: String, persist: Boolean, transform: (DownloadedItem) -> DownloadedItem) {
        val updated = _items.value.map { if (it.id == id) transform(it) else it }
        _items.value = updated
        if (persist) HistoryManager.save(context.applicationContext, updated)
    }

    fun isCancelled(id: String): Boolean = synchronized(lock) { cancelled.contains(id) }

    /** Cancels an in-flight item, or drops it from the queue if it hasn't started. */
    fun cancel(context: Context, id: String) {
        val wasPending: Boolean
        synchronized(lock) {
            cancelled.add(id)
            wasPending = pending.any { it.id == id }
            if (wasPending) pending.removeAll { it.id == id }
        }
        if (wasPending) {
            synchronized(lock) { cancelled.remove(id); batchDone++ }
            updateItem(context, id, persist = true) { it.copy(status = DownloadStatus.CANCELLED) }
        }
    }

    fun remove(context: Context, id: String) {
        cancel(context, id)
        val item = _items.value.find { it.id == id }
        if (item != null && item.filePath.isNotEmpty()) {
            try {
                context.contentResolver.delete(Uri.parse(item.filePath), null, null)
            } catch (e: Exception) {
                CrashLogger.log("Failed to delete file from disk: ${e.message}")
            }
        }
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        HistoryManager.save(context.applicationContext, updated)
    }

    fun retryItem(context: Context, id: String) {
        val item = _items.value.find { it.id == id } ?: return
        val targetUrl = item.url ?: return
        val newId = java.util.UUID.randomUUID().toString()
        val request = DownloadRequest(
            id = newId,
            url = targetUrl,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl,
            formatId = item.formatId ?: "audio",
            batchId = item.batchId,
            batchTitle = item.batchTitle
        )
        // Remove old failed item
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        HistoryManager.save(context.applicationContext, updated)
        enqueue(context, listOf(request))
    }

    fun retryBatchFailed(context: Context, batchId: String) {
        val failed = _items.value.filter { it.batchId == batchId && it.status == DownloadStatus.FAILED && !it.url.isNullOrBlank() }
        if (failed.isEmpty()) return
        val failedIds = failed.map { it.id }.toSet()
        val requests = failed.map { item ->
            DownloadRequest(
                id = java.util.UUID.randomUUID().toString(),
                url = item.url!!,
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                formatId = item.formatId ?: "audio",
                batchId = item.batchId,
                batchTitle = item.batchTitle
            )
        }
        val updated = _items.value.filterNot { failedIds.contains(it.id) }
        _items.value = updated
        HistoryManager.save(context.applicationContext, updated)
        enqueue(context, requests)
    }
}
