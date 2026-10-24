package com.elbruz.vrvideo.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(UserPrefs.get(this))
    }

    private lateinit var binding: ActivityMainBinding
    internal lateinit var webView: WebView
    private lateinit var jsInterface: VrJsInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        webView = binding.webView

        jsInterface = VrJsInterface(this)
        setupWebView()
        setupUi()

        val startUrl = savedInstanceState?.getString("url")
            ?: "https://www.google.com"

        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.postDelayed({ jsInterface.injectScanner() }, 800)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress >= 90) {
                    view.postDelayed({ jsInterface.injectScanner() }, 500)
                }
            }
        }

        webView.addJavascriptInterface(jsInterface, "VrBrowser")
    }

    private fun setupUi() {
        binding.searchBox.queryHint = getString(R.string.hint_url_or_search)
        binding.searchBox.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim().orEmpty()
                if (q.isNotEmpty()) {
                    val url = viewModel.currentEngine().buildSearchUrl(q)
                    webView.loadUrl(url)
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = true
        })

        binding.menuBtn.setOnClickListener {
            Toast.makeText(this, "Menu — Settings/Vault/Downloads coming soon", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("url", webView.url ?: "")
    }
}
