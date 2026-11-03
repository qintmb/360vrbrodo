package com.elbruz.vrvideo.ui.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.adblock.AdBlocker
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.databinding.ActivityMainBinding
import com.elbruz.vrvideo.ui.main.download.DownloadsActivity
import com.elbruz.vrvideo.ui.main.settings.SettingsActivity
import com.elbruz.vrvideo.ui.main.vault.VaultActivity
import com.elbruz.vrvideo.ui.tabs.TabManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), TabManager.TabFactory {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(UserPrefs.get(this))
    }

    private lateinit var binding: ActivityMainBinding
    internal lateinit var tabManager: TabManager
    private lateinit var jsInterface: VrJsInterface
    private lateinit var bottomNav: BottomNavigationView

    /** Videos detected on the current page; drives the floating download pill. */
    private var detectedVideos: List<VrJsInterface.VideoItem> = emptyList()

    /** Called by VrJsInterface when the page scanner finds videos. */
    fun onVideosDetected(items: List<VrJsInterface.VideoItem>) {
        detectedVideos = items
        if (binding.btnFloatingDownload.visibility != View.VISIBLE) {
            binding.btnFloatingDownload.visibility = View.VISIBLE
        }
        pulseDownloadButton()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabManager = TabManager(this)
        jsInterface = VrJsInterface(this)
        bottomNav = binding.bottomNav

        setupUi()
        setupTabObservers()

        val intentUrl = intent?.data?.toString()
        if (savedInstanceState == null) {
            if (!intentUrl.isNullOrBlank()) {
                tabManager.newTab(url = intentUrl)
            } else {
                tabManager.newTab(url = "https://www.google.com")
            }
            checkForUpdate()
        } else {
            tabManager.restoreState(savedInstanceState)
            if (tabManager.count == 0) tabManager.newTab(url = intentUrl ?: "https://www.google.com")
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false) == true) {
            checkForUpdate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false)) {
            checkForUpdate()
        }
    }

    private fun checkForUpdate() {
        com.elbruz.vrvideo.update.GitHubUpdater.checkForUpdate(
            this,
            onAvailable = { release ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Update available: ${release.versionName}")
                    .setMessage(release.releaseNotes.ifBlank { "New version ${release.versionName} is available." })
                    .setPositiveButton("Update") { _, _ ->
                        com.elbruz.vrvideo.update.GitHubUpdater.downloadAndInstall(this, release)
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        )
    }

    // --- TabManager.TabFactory ---

    @SuppressLint("SetJavaScriptEnabled")
    override fun createWebView(incognito: Boolean): WebView {
        val webView = WebView(this)
        TabManager.configureWebView(webView, incognito)
        val adBlocker = AdBlocker.get(this)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                if (adBlocker.shouldBlock(url)) {
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream("Blocked by AdBlocker".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (view === tabManager.activeTab?.webView) {
                    view.postDelayed({ jsInterface.injectScanner(view) }, 800)
                    view.postDelayed({ jsInterface.injectAdblockCss(view) }, 700)
                }
                refreshTabStrip()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress >= 90 && view === tabManager.activeTab?.webView) {
                    view.postDelayed({ jsInterface.injectScanner(view) }, 500)
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                super.onReceivedTitle(view, title)
                refreshTabStrip()
            }

            // Fullscreen video (YouTube, etc.): show the video surface in the overlay.
            private var customView: View? = null
            private var customViewCallback: WebChromeClient.CustomViewCallback? = null

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                val container = binding.fullscreenVideoContainer
                container.visibility = View.VISIBLE
                container.addView(view)
                binding.bottomNav.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val custom = customView ?: return
                val container = binding.fullscreenVideoContainer
                container.removeView(custom)
                container.visibility = View.GONE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                binding.bottomNav.visibility = View.VISIBLE
            }
        }

        webView.addJavascriptInterface(jsInterface, "VrBrowser")
        return webView
    }

    override fun onTabTitleChanged(id: Long, title: String) {
        refreshTabStrip()
    }

    override fun onTabUrlChanged(id: Long, url: String) {
        refreshTabStrip()
        updateSiteSecurity(url)
    }

    // --- UI setup ---

    private fun setupUi() {
        // SearchView inside rounded CardView
        binding.searchBox.queryHint = getString(R.string.hint_url_or_search)
        binding.searchBox.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim().orEmpty()
                if (q.isNotEmpty()) {
                    val url = viewModel.currentEngine().buildSearchUrl(q)
                    tabManager.loadUrl(url)
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = true
        })

        // Address bar Back/Forward buttons
        setupAddressBarControls()

        // Bottom Navigation
        var lastBrowserClick = 0L
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navBrowser -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBrowserClick < 400) {
                        showTabSwitcher()
                    }
                    lastBrowserClick = now
                    true
                }
                R.id.navDownloader -> {
                    startActivity(android.content.Intent(this, DownloadsActivity::class.java))
                    true
                }
                R.id.navVault -> {
                    startActivity(android.content.Intent(this, VaultActivity::class.java))
                    true
                }
                R.id.navSettings -> {
                    startActivity(android.content.Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        updateTabCount()

        // Floating download pill — draggable + opens download dialog
        setupFloatingDownloadButton()
    }

    private fun setupAddressBarControls() {
        binding.btnBack.setOnClickListener { tabManager.goBack() }
        binding.btnForward.setOnClickListener { tabManager.activeTab?.webView?.goForward() }
        // Update visibility when the page navigates
        lifecycleScope.launch {
            tabManager.activeTabIdFlow.collect {
                val webView = tabManager.activeTab?.webView ?: return@collect
                binding.btnBack.visibility = if (webView.canGoBack()) View.VISIBLE else View.GONE
                binding.btnForward.visibility = if (webView.canGoForward()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupFloatingDownloadButton() {
        val btn = binding.btnFloatingDownload
        btn.setOnClickListener {
            if (detectedVideos.isNotEmpty()) {
                com.elbruz.vrvideo.ui.main.download.DownloadPromptDialog.show(this, detectedVideos)
            }
        }
        // Draggable via onTouch; on DOWN return false so taps still reach the
        // click listener. A move past the slop threshold starts the drag.
        val viewConfiguration = android.view.ViewConfiguration.get(this)
        val slop = viewConfiguration.scaledTouchSlop
        var dragging = false
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btn.tag = Pair(event.rawX - btn.x, event.rawY - btn.y)
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        val dx = event.rawX - btn.x - (btn.tag as Pair<Float, Float>).first
                        val dy = event.rawY - btn.y - (btn.tag as Pair<Float, Float>).second
                        if (dx * dx + dy * dy > slop * slop) dragging = true
                    }
                    if (dragging) {
                        val (offX, offY) = btn.tag as Pair<Float, Float>
                        btn.x = event.rawX - offX
                        btn.y = event.rawY - offY
                    }
                    dragging
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) snapToEdge(btn)
                    dragging
                }
                else -> false
            }
        }
    }

    /** Slide the floating pill to the nearest screen edge so it never blocks content. */
    private fun snapToEdge(btn: View) {
        val root = btn.parent as? View ?: return
        val maxX = root.width - btn.width
        val targetX = if (btn.x < maxX / 2f) 8f else maxX - 8f
        btn.animate().x(targetX).setDuration(150).start()
    }

    /** Pulse the pill a few times when new media is detected. */
    fun pulseDownloadButton() {
        val btn = binding.btnFloatingDownload
        if (btn.visibility != View.VISIBLE) return
        btn.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).withEndAction {
            btn.animate().scaleX(1f).scaleY(1f).setDuration(180).withEndAction {
                btn.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).withEndAction {
                    btn.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                }.start()
            }.start()
        }.start()
    }

    private fun setupTabObservers() {
        lifecycleScope.launch {
            tabManager.activeTabIdFlow.collect { showTab(it) }
        }
        lifecycleScope.launch {
            tabManager.tabsFlow.collect { tabs ->
                updateTabCount()
                refreshTabStrip()
            }
        }
    }

    private fun showTab(id: Long) {
        val tab = tabManager.tabsFlow.value.firstOrNull { it.id == id } ?: return
        val webView = tabManager.activeTab?.webView ?: return
        val container = binding.webViewContainer
        if (container.getChildAt(0) !== webView) {
            container.removeAllViews()
            container.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        binding.searchBox.setQuery(tab.url, false)
        updateSiteSecurity(tab.url)
        // Reset detection state when switching tabs; scanner re-fires on the new page.
        detectedVideos = emptyList()
        binding.btnFloatingDownload.visibility = View.GONE
    }

    private fun updateSiteSecurity(url: String?) {
        val secure = url?.startsWith("https://") == true
        binding.ivSiteSecurity.setImageResource(
            if (secure) android.R.drawable.ic_lock_lock else android.R.drawable.ic_lock_lock
        )
        binding.ivSiteSecurity.setColorFilter(
            if (secure) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_orange_dark)
        )
    }

    private fun updateTabCount() {
        val count = tabManager.count
        binding.tvTabCount.text = count.toString()
        binding.tvTabCount.visibility = if (count > 1) View.VISIBLE else View.GONE
    }

    private fun refreshTabStrip() {
        // Tab strip removed - using tab switcher grid instead
    }

    private fun showTabSwitcher() {
        val dialog = Dialog(this, R.style.Theme_VrVideo_Dialog)
        dialog.setContentView(R.layout.dialog_tab_switcher)
        val recycler = dialog.findViewById<RecyclerView>(R.id.rvTabs)
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = TabSwitcherAdapter(this, tabManager.tabsFlow.value, tabManager.activeTabIdFlow.value) { meta ->
            tabManager.setActive(meta.id)
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnNewTab).setOnClickListener {
            tabManager.newTab()
            dialog.dismiss()
        }
        dialog.findViewById<TextView>(R.id.btnIncognito).setOnClickListener {
            tabManager.newTab(incognito = true)
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onBackPressed() {
        if (tabManager.canGoBack()) tabManager.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        tabManager.saveState(out)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tabManager.destroy()
    }

    companion object {
        /** Extra set by UpdateWorker notification to open the update dialog. */
        const val EXTRA_OPEN_UPDATE_DIALOG = "extra_open_update_dialog"
    }

    private class TabSwitcherAdapter(
        private val context: MainActivity,
        private val tabs: List<TabManager.TabMeta>,
        private val activeId: Long,
        private val onTabClick: (TabManager.TabMeta) -> Unit
    ) : RecyclerView.Adapter<TabSwitcherAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: View = view.findViewById(R.id.tabThumbnail)
            val title: TextView = view.findViewById(R.id.tabTitle)
            val close: ImageView = view.findViewById(R.id.tabClose)
            val incognitoBadge: View = view.findViewById(R.id.incognitoBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(context).inflate(R.layout.item_tab_grid, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val meta = tabs[position]
            holder.title.text = meta.title
            holder.thumbnail.setBackgroundColor(if (meta.id == activeId) context.getColor(R.color.md_theme_primary) else context.getColor(R.color.md_theme_surfaceVariant))
            holder.incognitoBadge.visibility = if (meta.isIncognito) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onTabClick(meta) }
            holder.close.setOnClickListener { context.tabManager.closeTab(meta.id); notifyItemRemoved(position) }
        }

        override fun getItemCount() = tabs.size
    }
}