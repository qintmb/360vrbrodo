package com.elbruz.vrvideo.vault

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.elbruz.vrvideo.util.VaultPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans downloaded media from both the hidden vault and the public
 * Download folder, extracting size/duration/resolution/thumbnail.
 * Thumbnails are cached under cacheDir/thumbs so Coil can load them.
 */
object VaultRepository {

    private const val TAG = "VaultRepository"
    private val VIDEO_EXT = setOf("mp4", "webm", "mkv", "mov", "avi", "3gp", "m4v", "ts")

    enum class Sort { NEWEST, LARGEST, NAME, DURATION }
    enum class Filter { ALL, VR360, FLAT }

    data class VaultFile(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val isVr: Boolean,
        val thumbPath: String?,
        val lastModified: Long,
        val inVault: Boolean,
    )

    /** Scan both locations and return media with metadata. */
    suspend fun load(context: Context): List<VaultFile> = withContext(Dispatchers.IO) {
        val vaultDir = VaultPrefs.getVaultDir(context)
        val publicDir = publicVideoDir(context)
        val files = mutableListOf<VaultFile>()
        files += vaultDir.listFiles().orEmpty().filter { it.isMedia() }.map { readMeta(context, it, true) }
        files += publicDir.listFiles().orEmpty().filter { it.isMedia() }.map { readMeta(context, it, false) }
        files.sortedByDescending { it.lastModified }
    }

    /** Public folder that shows up in the system gallery. */
    fun publicVideoDir(context: Context): File =
        File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MOVIES), "360VR").apply { mkdirs() }

    fun applyFilterSort(files: List<VaultFile>, filter: Filter, sort: Sort): List<VaultFile> {
        val filtered = when (filter) {
            Filter.ALL -> files
            Filter.VR360 -> files.filter { it.isVr }
            Filter.FLAT -> files.filter { !it.isVr }
        }
        return when (sort) {
            Sort.NEWEST -> filtered.sortedByDescending { it.lastModified }
            Sort.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            Sort.NAME -> filtered.sortedBy { it.name.lowercase() }
            Sort.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    private fun File.isMedia(): Boolean =
        isFile && extension.lowercase() in VIDEO_EXT && !name.startsWith(".")

    private fun readMeta(context: Context, file: File, inVault: Boolean): VaultFile {
        var durationMs = 0L
        var width = 0
        var height = 0
        var rotation = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Metadata failed for ${file.name}: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }

        val (w, h) = if (rotation == 90 || rotation == 270) height to width else width to height
        val isVr = detectVr(file.name, w, h)
        val thumb = thumbnail(context, file)

        return VaultFile(
            file = file,
            name = file.name,
            sizeBytes = file.length(),
            durationMs = durationMs,
            width = w,
            height = h,
            isVr = isVr,
            thumbPath = thumb,
            lastModified = file.lastModified(),
            inVault = inVault,
        )
    }

    /**
     * Equirectangular 360° video is almost always 2:1 (e.g. 3840x1920,
     * 2048x1024). Some tools export other ratios, so also trust the
     * filename when it says 360/vr/spherical/equirect.
     */
    private fun detectVr(name: String, width: Int, height: Int): Boolean {
        val lower = name.lowercase()
        if (listOf("360", "vr", "equirect", "spherical", "v360", "insta360", "gopro max").any { lower.contains(it) }) {
            return true
        }
        if (width <= 0 || height <= 0) return false
        val ratio = width.toFloat() / height.toFloat()
        return ratio >= 1.85f && ratio <= 2.15f
    }

    private fun thumbnail(context: Context, file: File): String? {
        val outDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val out = File(outDir, "${file.absolutePath.hashCode()}_${file.lastModified()}.jpg")
        if (out.exists()) return out.absolutePath
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                out.outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
                out.absolutePath
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Thumb failed for ${file.name}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun delete(file: VaultFile): Boolean = runCatching { file.file.delete() }.getOrDefault(false)

    fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "--:--"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
