package com.elbruz.vrvideo.ui.main

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.elbruz.vrvideo.adblock.AdBlocker
import com.elbruz.vrvideo.ui.main.download.DownloadPromptDialog
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript bridge injected into every WebView page.
 *
 * Safety constraint: exposes ONLY `onVideosFound(...)` to receive
 * a JSON array of media candidates collected by a read-only DOM +
 * network-performance scanner script. It cannot read cookies, page
 * HTML, or control navigation.
 */
class VrJsInterface(
    private val activity: MainActivity,
) {
    companion object { private const val TAG = "VrJsInterface" }

    /** Called from JS: receive detected media URLs. */
    @JavascriptInterface
    fun onVideosFound(videoJsonArray: String) {
        activity.runOnUiThread {
            val list = parseVideoItems(videoJsonArray)
            if (list.isNotEmpty()) {
                activity.onVideosDetected(list)
            }
        }
    }

    /** Called from JS: receive a direct download link from a user click. */
    @JavascriptInterface
    fun onDownloadLink(url: String, filename: String) {
        activity.runOnUiThread {
            val item = VideoItem(url, filename, "", 0, 0)
            DownloadPromptDialog.show(activity, listOf(item))
        }
    }

    /** Inject the scanner into the given WebView (defaults to active tab). */
    fun injectScanner(target: WebView? = null) {
        val webView = target ?: activity.tabManager.activeTab?.webView ?: return
        webView.evaluateJavascript(SCANNER_JS, null)
    }

    /** Inject adblock element-hiding CSS using JSON-encoded string (safe escaping). */
    fun injectAdblockCss(target: WebView? = null) {
        val webView = target ?: activity.tabManager.activeTab?.webView ?: return
        val css = AdBlocker.get(activity).getElementHidingCss()
        val escaped = org.json.JSONObject.quote(css)
        webView.evaluateJavascript(
            "(function(){var s=document.createElement('style');s.textContent=$escaped;(document.head||document.documentElement).appendChild(s);})();",
            null
        )
    }

    private fun parseVideoItems(json: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj: JSONObject = arr.getJSONObject(i)
                list += VideoItem(
                    url = obj.optString("src", ""),
                    title = obj.optString("title", "video"),
                    poster = obj.optString("poster", ""),
                    width = obj.optInt("width", 0),
                    height = obj.optInt("height", 0),
                    format = obj.optString("format", ""),
                    estimatedBytes = obj.optLong("size", 0L),
                    durationSec = obj.optLong("duration", 0L),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse video list from JS", e)
        }
        return list.filter { it.url.isNotBlank() }
    }

    /** Metadata extracted from the page by the injected scanner script. */
    data class VideoItem(
        val url: String,
        val title: String,
        val poster: String,
        val width: Int,
        val height: Int,
        val format: String = "",
        val estimatedBytes: Long = 0L,
        val durationSec: Long = 0L,
    ) {
        val resolutionLabel: String
            get() = when {
                height >= 2160 -> "4K 2160p"
                height >= 1440 -> "2K 1440p"
                height >= 1080 || width >= 1920 -> "HD 1080p"
                height >= 720 || width >= 1280 -> "HD 720p"
                height >= 480 || width >= 854 -> "480p"
                height > 0 -> "${height}p"
                else -> "Unknown"
            }
    }

    private val SCANNER_JS = """
        (function(){
          if (window.__vrScannerInjected) return;
          window.__vrScannerInjected = true;

          function ext(url){
            var m = url.toLowerCase().match(/\.(mp4|webm|mkv|mov|avi|m3u8|mpd|mp3|m4a)(\?|#|$)/);
            return m ? m[1] : '';
          }
          function pushVariant(vids, seen, src, title, poster, w, h, size, dur){
            if (!src || seen[src]) return;
            seen[src] = 1;
            vids.push({
              src: src, title: title || 'video', poster: poster || '',
              width: w || 0, height: h || 0, format: ext(src),
              size: size || 0, duration: dur || 0
            });
          }

          function collect(){
            var vids = [];
            var seen = {};

            // 1) <video> elements with direct sources
            document.querySelectorAll('video').forEach(function(v){
              var src = v.currentSrc || v.src || '';
              if (!src) {
                var s = v.querySelector('source');
                if (s) src = s.src || s.getAttribute('src') || '';
              }
              pushVariant(vids, seen, src, v.getAttribute('data-title') || document.title,
                          v.poster, v.videoWidth, v.videoHeight, 0, Math.round(v.duration||0));
            });

            // 2) <source> tags anywhere (HLS/DASH manifests included)
            document.querySelectorAll('source').forEach(function(s){
              var src = s.src || s.getAttribute('src') || '';
              pushVariant(vids, seen, src, document.title, '', 0, 0, 0, 0);
            });

            // 3) direct media links
            document.querySelectorAll('a[href]').forEach(function(a){
              var href = a.href || '';
              if (/\.(mp4|webm|mkv|mov|avi|mp3|m4a)(\?|#|$)/i.test(href)) {
                pushVariant(vids, seen, href, (a.textContent||'').trim() || 'download',
                            '', 0, 0, 0, 0);
              }
            });

            // 4) network performance entries (catches blob/source-loaded streams)
            try {
              performance.getEntriesByType('resource').forEach(function(e){
                var u = e.name || '';
                if (/\.(mp4|webm|mkv|m3u8|mpd|m4s|ts)(\?|#|$)/i.test(u)) {
                  pushVariant(vids, seen, u, document.title, '', 0, 0,
                              Math.round(e.transferSize || 0), 0);
                }
              });
            } catch(_){}

            if (vids.length) window.VrBrowser.onVideosFound(JSON.stringify(vids));
          }

          collect();
          var t = null;
          function debounced(){
            if (t) return;
            t = setTimeout(function(){ t = null; collect(); }, 1200);
          }
          var observer = new MutationObserver(debounced);
          observer.observe(document.body, {childList:true, subtree:true, attributes:true, attributeFilter:['src']});
          setInterval(collect, 4000);
        })();
    """.trimIndent()
}
