package com.elbruz.vrvideo.ui.main.vault

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.ui.main.player.VideoPlayerActivity
import com.elbruz.vrvideo.vault.VaultRepository
import com.elbruz.vrvideo.vault.VaultRepository.Filter
import com.elbruz.vrvideo.vault.VaultRepository.Sort
import com.elbruz.vrvideo.vault.VaultRepository.VaultFile
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * Vault: thumbnails of downloaded videos (hidden vault + public Movies/360VR),
 * filter (all / 360° VR / flat), grid-list toggle, sort, and playback.
 */
class VaultActivity : ComponentActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: VaultAdapter
    private lateinit var empty: View

    private var allFiles: List<VaultFile> = emptyList()
    private var filter = Filter.ALL
    private var sort = Sort.NEWEST
    private var isGrid = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_vault)

        rv = findViewById(R.id.rvVault)
        empty = findViewById(R.id.emptyView)

        adapter = VaultAdapter(
            onClick = { play(it) },
            onLongClick = { showActions(it) },
        )
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter

        findViewById<ImageButton>(R.id.btnViewMode).setOnClickListener { toggleView() }
        findViewById<ImageButton>(R.id.btnSort).setOnClickListener { showSortMenu() }

        findViewById<Chip>(R.id.chipAll).setOnClickListener { applyFilter(Filter.ALL) }
        findViewById<Chip>(R.id.chipVr).setOnClickListener { applyFilter(Filter.VR360) }
        findViewById<Chip>(R.id.chipFlat).setOnClickListener { applyFilter(Filter.FLAT) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            allFiles = VaultRepository.load(this@VaultActivity)
            publish()
        }
    }

    private fun publish() {
        val list = VaultRepository.applyFilterSort(allFiles, filter, sort)
        adapter.submitList(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyFilter(f: Filter) {
        filter = f
        publish()
    }

    private fun toggleView() {
        isGrid = !isGrid
        adapter.isGrid = isGrid
        rv.layoutManager = if (isGrid) GridLayoutManager(this, 2) else LinearLayoutManager(this)
        findViewById<ImageButton>(R.id.btnViewMode).setImageResource(
            if (isGrid) R.drawable.ic_list else R.drawable.ic_grid
        )
    }

    private fun showSortMenu() {
        val labels = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_largest),
            getString(R.string.sort_name),
            getString(R.string.sort_duration),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.vault_sort)
            .setItems(labels) { _, which ->
                sort = when (which) {
                    0 -> Sort.NEWEST
                    1 -> Sort.LARGEST
                    2 -> Sort.NAME
                    else -> Sort.DURATION
                }
                publish()
            }
            .show()
    }

    private fun play(item: VaultFile) {
        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_URL, "file://${item.file.absolutePath}")
            putExtra(VideoPlayerActivity.EXTRA_TITLE, item.name)
            putExtra(VideoPlayerActivity.EXTRA_IS_360, item.isVr)
        })
    }

    private fun showActions(item: VaultFile) {
        val actions = arrayOf(
            getString(R.string.vault_open),
            getString(R.string.vault_share),
            getString(R.string.vault_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> play(item)
                    1 -> share(item)
                    else -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun share(item: VaultFile) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", item.file
        )
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, getString(R.string.vault_share)))
    }

    private fun confirmDelete(item: VaultFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.vault_delete)
            .setMessage(item.name)
            .setPositiveButton(R.string.vault_delete) { _, _ ->
                if (VaultRepository.delete(item)) {
                    android.widget.Toast.makeText(this, R.string.vault_deleted, android.widget.Toast.LENGTH_SHORT).show()
                    reload()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
