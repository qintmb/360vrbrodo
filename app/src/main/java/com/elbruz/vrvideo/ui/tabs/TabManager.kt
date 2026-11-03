package com.elbruz.vrvideo.ui.tabs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Multi-tab manager ala ClintBrowser: pool of WebViews, one active tab,
 * background tabs keep their state (scroll, history, JS).
 */
class TabManager(
    private val factory: TabFactory,
) {
    data class TabMeta(
        val id: Long,
        val title: String,
        val url: String,
        val isIncognito: Boolean = false,
    )

    interface TabFactory {
        fun createWebView(incognito: Boolean): WebView
        fun onTabTitleChanged(id: Long, title: String)
        fun onTabUrlChanged(id: Long, url: String)
    }

    data class Tab(
        val id: Long,
        val webView: WebView,
        val isIncognito: Boolean,
    ) {
        var title: String = "New Tab"
        var url: String = ""
    }

    private val _tabs = MutableStateFlow<List<TabMeta>>(emptyList())
    val tabsFlow: StateFlow<List<TabMeta>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long>(-1)
    val activeTabIdFlow: StateFlow<Long> = _activeTabId.asStateFlow()

    private val tabs = LinkedHashMap<Long, Tab>()
    private var nextId = 1L

    val activeTab: Tab?
        get() = tabs[_activeTabId.value]

    val count: Int get() = tabs.size

    fun newTab(url: String = "https://www.google.com", incognito: Boolean = false, makeActive: Boolean = true): Tab {
        val id = nextId++
        val webView = factory.createWebView(incognito)
        val tab = Tab(id, webView, incognito)
        tabs[id] = tab
        publishTabs()
        if (makeActive) setActive(id)
        webView.loadUrl(url)
        return tab
    }

    fun setActive(id: Long) {
        if (!tabs.containsKey(id)) return
        _activeTabId.value = id
        val tab = tabs[id] ?: return
        tab.title = tab.webView.title?.takeIf { it.isNotBlank() } ?: tab.title
        tab.url = tab.webView.url ?: tab.url
        publishTabs()
    }

    fun closeTab(id: Long) {
        val tab = tabs.remove(id) ?: return
        tab.webView.stopLoading()
        tab.webView.destroy()
        publishTabs()
        if (_activeTabId.value == id) {
            // Activate nearest neighbor, else create a fresh tab.
            val next = tabs.keys.lastOrNull()
            if (next != null) setActive(next) else newTab()
        }
    }

    fun closeAll() {
        tabs.values.forEach { it.webView.destroy() }
        tabs.clear()
        publishTabs()
        _activeTabId.value = -1
        newTab()
    }

    fun restoreTab(url: String) {
        newTab(url = url)
    }

    fun canGoBack(): Boolean = activeTab?.webView?.canGoBack() == true

    fun goBack() {
        activeTab?.webView?.goBack()
    }

    fun loadUrl(url: String) {
        val tab = activeTab ?: newTab(url = url).also { return }
        tab.webView.loadUrl(url)
        tab.url = url
        publishTabs()
    }

    fun currentUrl(): String = activeTab?.webView?.url ?: activeTab?.url ?: ""

    fun currentTitle(): String = activeTab?.webView?.title ?: ""

    fun destroy() {
        tabs.values.forEach { it.webView.destroy() }
        tabs.clear()
        _tabs.value = emptyList()
    }

    fun saveState(out: Bundle) {
        val urls = tabs.values.map { it.url }
        out.putStringArrayList("tab_urls", ArrayList(urls))
        out.putLong("active_tab", _activeTabId.value)
    }

    fun restoreState(state: Bundle?) {
        val urls = state?.getStringArrayList("tab_urls") ?: return
        val active = state.getLong("active_tab", -1L)
        urls.filter { it.isNotBlank() }.take(8).forEachIndexed { i, url ->
            val tab = newTab(url = url, makeActive = i == 0)
            if (tab.id == active) _activeTabId.value = tab.id
        }
    }

    private fun publishTabs() {
        _tabs.value = tabs.values.map { TabMeta(it.id, it.title, it.url, it.isIncognito) }
    }

    companion object {
        @SuppressLint("SetJavaScriptEnabled")
        fun configureWebView(webView: WebView, incognito: Boolean) {
            val s = webView.settings
            s.javaScriptEnabled = true
            s.domStorageEnabled = !incognito
            s.mediaPlaybackRequiresUserGesture = false
            s.javaScriptCanOpenWindowsAutomatically = false
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
            s.setSupportMultipleWindows(false)
            // Fix black video on YouTube/HTML5: enable hardware acceleration on the view itself.
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            s.allowFileAccess = false
            s.allowContentAccess = false
            s.allowFileAccessFromFileURLs = false
            s.allowUniversalAccessFromFileURLs = false
            s.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_CACHE_ELSE_NETWORK
            s.userAgentString = if (incognito) {
                // Strip app identity for incognito tabs
                WebSettings.getDefaultUserAgent(webView.context)
                    .replace(Regex("""\s*VRVideo/\S+"""), "")
            } else {
                WebSettings.getDefaultUserAgent(webView.context)
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !incognito)
            CookieManager.getInstance().setAcceptCookie(!incognito)
        }
    }
}
