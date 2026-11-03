package com.elbruz.vrvideo.ui.main.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.util.downloaders.DownloadRepository

class DownloadsAdapter : ListAdapter<DownloadRepository.DownloadItem, DownloadsAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DownloadRepository.DownloadItem>() {
            override fun areItemsTheSame(a: DownloadRepository.DownloadItem, b: DownloadRepository.DownloadItem) = a.id == b.id
            override fun areContentsTheSame(a: DownloadRepository.DownloadItem, b: DownloadRepository.DownloadItem) = a == b
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val status: TextView = itemView.findViewById(R.id.status)
        val progress: ProgressBar = itemView.findViewById(R.id.progress)
        val btnPause: ImageButton = itemView.findViewById(R.id.btnPause)
        val btnCancel: ImageButton = itemView.findViewById(R.id.btnCancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.title.text = item.filename
        holder.progress.progress = item.progress
        holder.progress.max = 100

        val statusText = when (item.status) {
            DownloadRepository.Status.QUEUED -> "Queued"
            DownloadRepository.Status.DOWNLOADING -> "${item.progress}% · ${formatSpeed(item.speedBps)}"
            DownloadRepository.Status.PAUSED -> "Paused · ${item.progress}%"
            DownloadRepository.Status.COMPLETED -> "Completed"
            DownloadRepository.Status.FAILED -> "Failed: ${item.error}"
        }
        holder.status.text = statusText

        holder.btnPause.setOnClickListener {
            if (item.status == DownloadRepository.Status.DOWNLOADING) {
                DownloadRepository.pause(item.id)
            } else if (item.status == DownloadRepository.Status.PAUSED) {
                DownloadRepository.resume(item.id)
            }
        }

        holder.btnCancel.setOnClickListener {
            DownloadRepository.remove(item.id)
        }
    }

    private fun formatSpeed(bps: Long): String {
        return when {
            bps < 1024 -> "$bps B/s"
            bps < 1024 * 1024 -> "${bps / 1024} KB/s"
            else -> String.format("%.1f MB/s", bps / (1024.0 * 1024.0))
        }
    }
}
