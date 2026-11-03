package com.elbruz.vrvideo.util.downloaders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared download state: queue, progress, pause/resume/cancel.
 * FastDownloadService publishes progress here; DownloadsActivity observes.
 */
object DownloadRepository {

    enum class Status { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    data class DownloadItem(
        val id: Long,
        val url: String,
        val filename: String,
        val status: Status,
        val progress: Int = 0,          // 0..100
        val speedBps: Long = 0L,
        val etaSeconds: Long = -1L,
        val totalBytes: Long = -1L,
        val downloadedBytes: Long = 0L,
        val filePath: String? = null,
        val error: String? = null,
        val saveToVault: Boolean = false,
        val startedAt: Long = System.currentTimeMillis(),
    )

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val byId = ConcurrentHashMap<Long, DownloadItem>()
    private var nextId = 1L

    fun nextId(): Long = synchronized(this) { nextId++ }

    fun enqueue(url: String, filename: String, saveToVault: Boolean): DownloadItem {
        val id = nextId()
        val item = DownloadItem(
            id = id,
            url = url,
            filename = filename,
            status = Status.QUEUED,
            saveToVault = saveToVault,
        )
        byId[id] = item
        publish()
        return item
    }

    fun update(id: Long, transform: (DownloadItem) -> DownloadItem) {
        val current = byId[id] ?: return
        byId[id] = transform(current)
        publish()
    }

    fun updateProgress(id: Long, progress: Int, speedBps: Long, etaSeconds: Long, downloadedBytes: Long, totalBytes: Long) {
        update(id) {
            it.copy(
                status = Status.DOWNLOADING,
                progress = progress.coerceIn(0, 100),
                speedBps = speedBps,
                etaSeconds = etaSeconds,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
            )
        }
    }

    fun markCompleted(id: Long, filePath: String) {
        update(id) { it.copy(status = Status.COMPLETED, progress = 100, filePath = filePath, speedBps = 0) }
    }

    fun markFailed(id: Long, error: String) {
        update(id) { it.copy(status = Status.FAILED, error = error, speedBps = 0) }
    }

    fun pause(id: Long) {
        update(id) { it.copy(status = Status.PAUSED, speedBps = 0) }
    }

    fun resume(id: Long) {
        update(id) { it.copy(status = Status.QUEUED, error = null) }
    }

    fun remove(id: Long) {
        byId.remove(id)
        publish()
    }

    fun get(id: Long): DownloadItem? = byId[id]

    fun activeCount(): Int = _items.value.count { it.status == Status.DOWNLOADING || it.status == Status.QUEUED }

    private fun publish() {
        _items.value = byId.values.sortedByDescending { it.startedAt }
    }
}
