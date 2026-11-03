package com.elbruz.vrvideo.ui.main.download

import android.app.Dialog
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.databinding.DialogDownloadPromptBinding
import com.elbruz.vrvideo.ui.main.VrJsInterface.VideoItem
import com.elbruz.vrvideo.util.VaultPrefs
import com.elbruz.vrvideo.util.downloaders.DownloadRepository
import com.elbruz.vrvideo.util.downloaders.FastDownloadService
import com.elbruz.vrvideo.util.downloaders.MediaInspector
import com.elbruz.vrvideo.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom-sheet dialog listing detected media variants with
 * resolution + estimated size (yt-dlp inspected when possible).
 * Lets the user pick a destination (public Movies folder visible
 * in the gallery, or hidden Vault) and remembers that choice.
 */
object DownloadPromptDialog {

    private val PREF_SAVE_TO_VAULT =
        androidx.datastore.preferences.core.booleanPreferencesKey("download_save_to_vault")
    private val PREF_ASKED_LOCATION =
        androidx.datastore.preferences.core.booleanPreferencesKey("download_asked_location")

    fun show(activity: ComponentActivity, items: List<VideoItem>) {
        if (items.isEmpty()) return
        val hint = items.first()

        val binding = DialogDownloadPromptBinding.inflate(activity.layoutInflater)
        val dialog = Dialog(activity, R.style.Theme_VrVideo_Dialog)
        dialog.setContentView(binding.root)

        val prefs = UserPrefs.get(activity)

        // Restore last choice; first run shows the explainer.
        var saveToVault = true
        CoroutineScope(Dispatchers.IO).launch {
            val asked = prefs.getBoolean(PREF_ASKED_LOCATION, false)
            saveToVault = prefs.getBoolean(PREF_SAVE_TO_VAULT, true)
            withContext(Dispatchers.Main) {
                binding.switchVault.isChecked = saveToVault
                if (!asked) showLocationExplanation(activity)
            }
        }

        binding.btnDownload.setOnClickListener {
            val filename = binding.editFilename.text?.toString()
                ?.takeIf { it.isNotBlank() } ?: suggestedName(hint)
            val toVault = binding.switchVault.isChecked
            CoroutineScope(Dispatchers.IO).launch {
                prefs.setBoolean(PREF_SAVE_TO_VAULT, toVault)
                prefs.setBoolean(PREF_ASKED_LOCATION, true)
            }

            val variant = binding.spinnerFormat.selectedItem as? VariantRow
            FastDownloadService.startDownload(
                context = activity,
                url = variant?.requestUrl ?: hint.url,
                filename = filename,
                saveToVault = toVault,
                formatSelector = variant?.selector,
            )
            Toast.makeText(
                activity,
                if (toVault) "Menyimpan ke Vault…" else "Menyimpan ke Movies/360VR…",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.let {
            it.width = FrameLayout.LayoutParams.MATCH_PARENT
            it.height = FrameLayout.LayoutParams.WRAP_CONTENT
            dialog.window?.attributes = it
        }

        // Populate real variants via yt-dlp in the background
        activity.lifecycleScope.launch {
            val variants = MediaInspector.inspect(hint.url, hint)
            if (variants.isEmpty()) return@launch
            val rows = variants.map { v ->
                val size = if (v.sizeBytes > 0) " · ~${VaultRepository.formatSize(v.sizeBytes)}" else ""
                val dims = if (v.width > 0) " · ${v.width}×${v.height}" else ""
                VariantRow(v, "${v.label}$dims$size", selectorFor(v))
            }
            withContext(Dispatchers.Main) {
                binding.spinnerFormat.adapter = ArrayAdapter(
                    activity, android.R.layout.simple_spinner_item, rows
                )
            }
        }
    }

    /** Map a variant to a yt-dlp format selector for the downloader. */
    private fun selectorFor(v: MediaInspector.Variant): String? {
        if (v.formatId.isBlank()) return null
        return if (v.isAudioOnly) "bestaudio/best"
        else "bv*[height<=${v.height.coerceAtLeast(360)}]+ba/b[height<=${v.height.coerceAtLeast(360)}]"
    }

    private fun suggestedName(item: VideoItem): String {
        val ext = item.format.takeIf { it.isNotBlank() }
            ?: item.url.substringAfterLast('.', "mp4").substringBefore('?').take(4)
        val base = item.title.takeIf { it.isNotBlank() && it != "video" }
            ?: "video_${System.currentTimeMillis()}"
        return "${base.take(60)}.$ext"
    }

    /** One-time explainer of where downloads go. */
    private fun showLocationExplanation(activity: ComponentActivity) {
        AlertDialog.Builder(activity)
            .setTitle("Lokasi penyimpanan")
            .setMessage(
                "Pilih tujuan download:\n\n" +
                    "• Movies/360VR — file terlihat di Galeri, bisa diputar aplikasi lain\n\n" +
                    "• Vault — tersembunyi, tidak muncul di Galeri, hanya bisa dibuka dari 360VR Browser\n\n" +
                    "Pilihan bisa diubah kapan saja lewat tombol Vault di dialog download."
            )
            .setPositiveButton("Mengerti", null)
            .show()
    }

    /** Spinner row: display label + the URL/selector to hand the downloader. */
    private data class VariantRow(
        val variant: MediaInspector.Variant,
        val label: String,
        val selector: String?,
    ) {
        val requestUrl: String get() = variant.url.ifBlank { "" }
        override fun toString(): String = label
    }
}
