package com.elbruz.vrvideo.ui.main.download

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.databinding.DialogDownloadPromptBinding
import com.elbruz.vrvideo.ui.main.VrJsInterface.VideoItem
import com.elbruz.vrvideo.util.VaultPrefs
import com.elbruz.vrvideo.util.downloaders.FastDownloadService

/**
 * Bottom-sheet-style dialog that appears when a video is detected in
 * the WebView. Offers resolution/format choices and a Vault toggle.
 */
object DownloadPromptDialog {

    fun show(activity: Activity, items: List<VideoItem>) {
        val binding = DialogDownloadPromptBinding.inflate(activity.layoutInflater)
        val dialog = Dialog(activity, R.style.Theme_VrVideo_Dialog)
        dialog.setContentView(binding.root)

        // Format the detected files into selectable labels
        val formats = items.map { item ->
            "${item.title} — ${item.resolutionLabel}"
        }.distinct()

        val adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            formats,
        )
        binding.spinnerFormat.adapter = adapter
        binding.editFilename.setText(items.firstOrNull()?.title ?: "video")

        binding.switchVault.isChecked = true  // default to vault

        binding.btnDownload.setOnClickListener {
            val selected = items.getOrNull(binding.spinnerFormat.selectedItemPosition)
                ?: items.first()
            val filename = binding.editFilename.text?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    val ext = getExtension(selected.url)
                    "download_${System.currentTimeMillis()}.${ext}"
                }
            val saveToVault = binding.switchVault.isChecked

            FastDownloadService.startDownload(
                context = activity,
                url = selected.url,
                filename = filename,
                saveToVault = saveToVault,
            )

            Toast.makeText(activity, "Downloading…", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val lp = dialog.window?.attributes
        lp?.width = FrameLayout.LayoutParams.MATCH_PARENT
        lp?.height = FrameLayout.LayoutParams.WRAP_CONTENT
        dialog.window?.attributes = lp
    }

    private fun getExtension(url: String): String {
        return url.substringAfterLast('.', "mp4").substringAfterLast("&").take(4)
    }
}
