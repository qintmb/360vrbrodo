package com.elbruz.vrvideo.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.databinding.ActivityMainBinding
import com.elbruz.vrvideo.databinding.ItemTabStripBinding
import com.elbruz.vrvideo.ui.main.settings.SettingsActivity
import com.elbruz.vrvideo.ui.main.download.DownloadsActivity
import com.elbruz.vrvideo.ui.tabs.TabManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), TabManager.TabFactory {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(UserPrefs.get(this))
    }

    private lateinit var binding: ActivityMainBinding
    internal lateinit var tabManager: TabManager
    private lateinit var jsInterface: VrJsInterface
    private val tabViews = HashMap<Long, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabManager = TabManager(this)
        jsInterface = VrJsInterface(this)

        setupUi()
        setupTabObservers()

        if (savedInstanceState == null) {
            tabManager.newTab(url = "https://www.google.com")
            checkForUpdate()
        } else {
            tabManager.restoreState(savedInstanceState)
            if (tabManager.count == 0) tabManager.newTab(url = "https://www.google.com")
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (view === tabManager.activeTab?.webView) {
                    view.postDelayed({ jsInterface.injectScanner(view) }, 800)
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
        }

        webView.addJavascriptInterface(jsInterface, "VrBrowser")
        return webView
    }

    override fun onTabTitleChanged(id: Long, title: String) {
        refreshTabStrip()
    }

    override fun onTabUrlChanged(id: Long, url: String) {
        refreshTabStrip()
    }

    // --- UI setup ---

    private fun setupUi() {
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

        binding.menuBtn.setOnClickListener {
            Toast.makeText(this, "Menu — Settings/Vault/Downloads", Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        binding.btnBack.setOnClickListener { tabManager.goBack() }
        binding.btnForward.setOnClickListener { tabManager.activeTab?.webView?.goForward() }
        binding.btnHome.setOnClickListener { tabManager.loadUrl("https://www.google.com") }
        binding.btnTabs.setOnClickListener { toggleTabStrip() }
        binding.btnDownloads.setOnClickListener {
            startActivity(android.content.Intent(this, DownloadsActivity::class.java))
        }
    }

    private fun toggleTabStrip() {
        binding.tabStrip.visibility =
            if (binding.tabStrip.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        refreshTabStrip()
    }

    private fun setupTabObservers() {
        lifecycleScope.launch {
            tabManager.activeTabIdFlow.collect { id ->
                showTab(id)
                refreshTabStrip()
            }
        }
        lifecycleScope.launch {
            tabManager.tabsFlow.collect { refreshTabStrip() }
        }
    }

    private fun showTab(id: Long) {
        val tab = tabManager.tabsFlow.value.firstOrNull { it.id == id } ?: return
        val webView = tabManager.activeTab?.webView ?: return
        val container = binding.webViewContainer
        if (container.getChildAt(0) !== webView) {
            container.removeAllViews()
            container.addView(webView, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        }
        // Update search box with current URL
        binding.searchBox.setQuery(tab.url, false)
    }

    private fun refreshTabStrip() {
        val container = binding.tabStripContainer
        container.removeAllViews()
        val tabs = tabManager.tabsFlow.value
        val activeId = tabManager.activeTabIdFlow.value
        tabs.forEach { meta ->
            val itemBinding = ItemTabStripBinding.inflate(layoutInflater, container, false)
            itemBinding.tabTitle.text = meta.title
            itemBinding.tabClose.setOnClickListener { tabManager.closeTab(meta.id) }
            itemBinding.root.setOnClickListener { tabManager.setActive(meta.id) }
            itemBinding.root.alpha = if (meta.id == activeId) 1f else 0.6f
            container.addView(itemBinding.root)
        }
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
}
