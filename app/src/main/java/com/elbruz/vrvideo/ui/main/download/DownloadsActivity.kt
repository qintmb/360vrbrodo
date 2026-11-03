package com.elbruz.vrvideo.ui.main.download

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.ui.main.MainActivity
import com.elbruz.vrvideo.ui.main.settings.SettingsActivity
import com.elbruz.vrvideo.ui.main.vault.VaultActivity
import com.elbruz.vrvideo.util.downloaders.DownloadRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Download manager tab: live queue/progress of active and completed downloads.
 * Persistent bottom navigation across all main screens.
 */
class DownloadsActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: DownloadsAdapter
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_downloads)

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        bottomNav = findViewById(R.id.bottomNav)
        adapter = DownloadsAdapter()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.fabAdd).setOnClickListener { showManualDownloadDialog() }

        setupBottomNav()
        observeDownloads()
    }

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.navDownloader
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navBrowser -> { switchTo(MainActivity::class.java); true }
                R.id.navDownloader -> true
                R.id.navVault -> { switchTo(VaultActivity::class.java); true }
                R.id.navSettings -> { switchTo(SettingsActivity::class.java); true }
                else -> false
            }
        }
    }

    private fun switchTo(cls: Class<*>) {
        startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    private fun observeDownloads() {
        lifecycleScope.launch {
            DownloadRepository.items.collect { items ->
                adapter.submitList(items)
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showManualDownloadDialog() {
        // Manual URL download dialog — reuse the video prompt for a single item.
        val input = android.widget.EditText(this).apply { hint = "https://..." }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.downloader_add_url)
            .setView(input)
            .setPositiveButton(R.string.action_download) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    com.elbruz.vrvideo.util.downloaders.FastDownloadService.startDownload(
                        this, url, url.substringAfterLast('/').take(60).ifBlank { "video.mp4" }, saveToVault = true
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.navDownloader
    }
}
