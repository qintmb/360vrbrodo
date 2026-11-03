package com.elbruz.vrvideo.adblock

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.elbruz.vrvideo.data.local.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Lightweight AdGuard-style host-based adblocker.
 * - Loads gzipped host list from assets (blocklist.txt.gz)
 * - Intercepts WebView requests via shouldInterceptRequest
 * - Supports element hiding CSS injection
 * - Toggleable via UserPrefs
 */
class AdBlocker private constructor(
    private val context: Context,
) : LifecycleEventObserver {

    companion object {
        private const val TAG = "AdBlocker"
        private const val ASSET_PATH = "adblock/blocklist.txt.gz"

        @Volatile private var INSTANCE: AdBlocker? = null

        fun get(context: Context): AdBlocker =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdBlocker(context.applicationContext).also { INSTANCE = it }
            }

        fun attachToLifecycle(lifecycleOwner: LifecycleOwner, context: Context) {
            get(context).also { it.lifecycleOwner = lifecycleOwner }
        }
    }

    private var lifecycleOwner: LifecycleOwner? = null
        set(value) {
            field = value
            value?.lifecycle?.addObserver(this)
        }

    private var blockedHosts: Set<String>? = null
    private var blocklistLoaded = false
    private val scope = CoroutineScope(Dispatchers.IO)

    // Cached flag — isEnabled() must not block the WebView thread.
    @Volatile private var enabledCache: Boolean = true

    init {
        // Defer blocklist loading until first needed (avoid crash on startup if asset missing)
        scope.launch {
            enabledCache = UserPrefs.get(context).getBoolean(UserPrefs.KEY_ADBLOCK_ENABLED, true)
        }
    }

    /** Ensure blocklist is loaded before checking. */
    private suspend fun ensureBlocklistLoaded() {
        if (blocklistLoaded || blockedHosts != null) return
        blocklistLoaded = true
        try {
            val input = context.assets.open(ASSET_PATH)
            val gzip = GZIPInputStream(input)
            val reader = BufferedReader(InputStreamReader(gzip))
            val hosts = mutableSetOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { hosts += it.trim().lowercase() }
            }
            reader.close()
            blockedHosts = hosts
            Log.i(TAG, "Loaded ${hosts.size} blocked hosts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blocklist", e)
            blockedHosts = emptySet()
        }
    }

    /** Check if URL's host should be blocked */
    fun shouldBlock(url: String?): Boolean {
        if (!enabledCache) return false
        // Fire-and-forget load; first call may allow through if not loaded yet
        scope.launch { ensureBlocklistLoaded() }
        if (blockedHosts == null) return false
        val host = extractHost(url) ?: return false
        return blockedHosts!!.contains(host) || isSubdomainBlocked(host)
    }

    private fun isSubdomainBlocked(host: String): Boolean {
        val parts = host.split('.')
        for (i in 1 until parts.size) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (blockedHosts!!.contains(parent)) return true
        }
        return false
    }

    private fun extractHost(url: String?): String? {
        try {
            val uri = android.net.Uri.parse(url)
            val h = uri.host?.lowercase()
            return if (h != null && h.isNotBlank()) h else null
        } catch (_: Exception) {
            return null
        }
    }

    /** Get element hiding CSS for injection into pages */
    fun getElementHidingCss(url: String? = null): String {
        // Never inject ad-hiding CSS into YouTube — it uses custom ad player modules
        // and greedy [class*="ad-"] rules hide the actual video element.
        val host = url?.let { extractHost(it) } ?: ""
        if (host.contains("youtube.com") || host.contains("youtu.be")) {
            return ""
        }
        return """
            .ads, .advert, .advertising,
            .adsbygoogle, .gpt-ad, .dfp-ad, #google_ads_div,
            .ad-container, .ad-slot, .ad-wrapper, .ad-placeholder,
            iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
            iframe[src*="adsystem"], iframe[src*="advertising"]
            { display: none !important; visibility: hidden !important; opacity: 0 !important; height: 0 !important; width: 0 !important; }
        """.trimIndent()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            scope.cancel()
        }
    }

    fun setEnabled(enabled: Boolean) {
        enabledCache = enabled
        val prefs = UserPrefs.get(context)
        scope.launch { prefs.setBoolean(UserPrefs.KEY_ADBLOCK_ENABLED, enabled) }
    }

    fun isEnabled(): Boolean = enabledCache
}