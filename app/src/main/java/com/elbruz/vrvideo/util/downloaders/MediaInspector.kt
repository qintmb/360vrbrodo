package com.elbruz.vrvideo.util.downloaders

import android.util.Log
import com.elbruz.vrvideo.ui.main.VrJsInterface.VideoItem
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uses yt-dlp's --dump-single-json to enumerate real stream variants
 * (resolution, filesize, ext) for a page/video URL. Falls back to the
 * DOM-detected item when yt-dlp fails or the URL is a direct file.
 */
object MediaInspector {

    private const val TAG = "MediaInspector"

    data class Variant(
        val url: String,        // direct stream URL when available
        val formatId: String,   // yt-dlp format id ("" when direct file)
        val label: String,      // "1080p", "audio only", …
        val width: Int,
        val height: Int,
        val sizeBytes: Long,    // -1 when unknown
        val ext: String,
        val isAudioOnly: Boolean,
    )

    /** Inspect a page or media URL; returns variants best-first. */
    suspend fun inspect(pageUrl: String, domHint: VideoItem? = null): List<Variant> =
        withContext(Dispatchers.IO) {
            // Direct media file → single variant, size from HTTP header not needed
            if (isDirectMedia(pageUrl)) {
                return@withContext listOf(
                    Variant(
                        url = pageUrl, formatId = "", label = "Direct file",
                        width = domHint?.width ?: 0, height = domHint?.height ?: 0,
                        sizeBytes = -1, ext = pageUrl.substringAfterLast('.', "mp4")
                            .substringBefore('?').take(4),
                        isAudioOnly = false,
                    )
                )
            }

            try {
                val request = YoutubeDLRequest(pageUrl)
                request.addOption("--dump-single-json")
                request.addOption("--no-playlist")
                val response = YoutubeDL.getInstance().execute(request, null, null)
                parseFormats(JSONObject(response.out))
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp inspect failed: ${e.message}")
                domHint?.let {
                    listOf(
                        Variant(it.url, "", it.resolutionLabel, it.width, it.height, -1,
                            it.format.ifBlank { "mp4" }, false)
                    )
                } ?: emptyList()
            }
        }

    private fun parseFormats(root: JSONObject): List<Variant> {
        val title = root.optString("title", "video")
        val formats = root.optJSONArray("formats") ?: JSONArray()
        val out = mutableListOf<Variant>()

        for (i in 0 until formats.length()) {
            val f = formats.getJSONObject(i)
            val vcodec = f.optString("vcodec", "none")
            val acodec = f.optString("acodec", "none")
            val isAudio = vcodec == "none" && acodec != "none"
            val height = f.optInt("height", 0)
            val width = f.optInt("width", 0)
            val size = f.optLong("filesize", f.optLong("filesize_approx", -1L))
            val ext = f.optString("ext", "mp4")
            val note = f.optString("format_note", "")
            val id = f.optString("format_id", "")

            // Skip storyboards / m3u8-only tiny fragments
            if (note.equals("storyboard", true)) continue

            val label = when {
                isAudio -> "Audio only (${ext.uppercase()})"
                height > 0 -> "${height}p${if (note.isNotBlank()) " · $note" else ""}"
                else -> "${ext.uppercase()}${if (note.isNotBlank()) " · $note" else ""}"
            }

            out += Variant(
                url = f.optString("url", ""),
                formatId = id,
                label = label,
                width = width,
                height = height,
                sizeBytes = size,
                ext = ext,
                isAudioOnly = isAudio,
            )
        }

        // Best-first: video by height desc, then audio
        val videos = out.filter { !it.isAudioOnly }.sortedByDescending { it.height }
        val audios = out.filter { it.isAudioOnly }.sortedByDescending { it.sizeBytes.coerceAtLeast(0) }
        val merged = videos + audios

        // Dedup by label, keep the first (best) of each
        val seen = mutableSetOf<String>()
        return merged.filter { seen.add(it.label) }.take(12)
            .also { if (it.isNotEmpty()) Log.i(TAG, "inspect: $title → ${it.size} variants") }
    }

    private fun isDirectMedia(url: String): Boolean =
        Regex("\\.(mp4|webm|mkv|mov|avi|mp3|m4a)(\\?|#|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(url)
}
