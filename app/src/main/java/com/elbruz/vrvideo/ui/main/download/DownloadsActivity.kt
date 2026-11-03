package com.elbruz.vrvideo.ui.main.download

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.util.downloaders.DownloadRepository
import kotlinx.coroutines.launch

/**
 * Download manager UI: list of active/completed downloads.
 * Observes DownloadRepository state.
 */
class DownloadsActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: DownloadsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_downloads)

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        adapter = DownloadsAdapter()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.fabAdd).setOnClickListener {
            // Manual download dialog (URL input)
            showManualDownloadDialog()
        }

        observeDownloads()
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
        // TODO: Show dialog with URL input
    }
}
