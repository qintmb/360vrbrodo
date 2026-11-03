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
 * a JSON array of <video>/<source> URLs collected by a read-only
 * DOM scanner script. It cannot read cookies, page HTML, or
 * control navigation.
 */
class VrJsInterface(
    private val activity: MainActivity,
) {
    companion object { private const val TAG = "VrJsInterface" }

    /** Called from JS: receive detected video URLs. */
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
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse video list from JS", e)
        }
        return list
    }

    /** Metadata extracted from the page by the injected scanner script. */
    data class VideoItem(
        val url: String,
        val title: String,
        val poster: String,
        val width: Int,
        val height: Int,
    ) {
        val resolutionLabel: String
            get() = when {
                width >= 1920 || height >= 1080 -> "HD 1080p+"
                width >= 1280 || height >= 720 -> "HD 720p"
                width >= 854 || height >= 480 -> "480p"
                else -> "Mobile"
            }
    }

    private val SCANNER_JS = """
        (function(){
          if (window.__vrScannerInjected) return;
          window.__vrScannerInjected = true;
          function collect(){
            var vids = [];
            var cands = document.querySelectorAll('video, source, a[href]');
            cands.forEach(function(el){
              var src = '';
              if (el.tagName === 'VIDEO' || el.tagName === 'SOURCE') {
                src = el.src || el.getAttribute('src')||'';
                var vid = el.closest('video');
                vids.push({
                  src:src,
                  title: (vid&&vid.getAttribute('data-title'))||(el.getAttribute('title')||'video'),
                  poster: vid?vid.poster:'',
                  width: vid?vid.videoWidth:(el.getAttribute('width')||0),
                  height: vid?vid.videoHeight:(el.getAttribute('height')||0)
                });
              } else {
                src = el.getAttribute('href')||'';
              }
              if (src && el.tagName === 'A') {
                var lower = src.toLowerCase();
                if (lower.match(/\.(mp4|webm|mkv|mov|avi|mp3|m4a)(\?|$)/)) {
                  vids.push({src:src, title:el.textContent.trim()||'download', poster:'', width:0, height:0});
                }
              }
            });
            if (vids.length) window.VrBrowser.onVideosFound(JSON.stringify(vids));
          }
          collect();
          var observer = new MutationObserver(collect);
          observer.observe(document.body, {childList:true, subtree:true, attributes:true, attributeFilter:['src']});
        })();
    """.trimIndent()
}
