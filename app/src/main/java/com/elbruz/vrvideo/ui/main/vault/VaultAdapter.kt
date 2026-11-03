package com.elbruz.vrvideo.ui.main.vault

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.vault.VaultRepository
import com.elbruz.vrvideo.vault.VaultRepository.VaultFile

/**
 * Single adapter rendering the vault as either a 2-column grid or a
 * vertical list — both layouts share the same view ids so binding is
 * one code path (see item_vault_grid.xml / item_vault_list.xml).
 */
class VaultAdapter(
    private val onClick: (VaultFile) -> Unit,
    private val onLongClick: (VaultFile) -> Unit,
) : ListAdapter<VaultFile, VaultAdapter.VH>(DIFF) {

    var isGrid = true
        set(value) { field = value; notifyDataSetChanged() }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.ivThumb)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val name: TextView = view.findViewById(R.id.tvName)
        val meta: TextView = view.findViewById(R.id.tvMeta)
        val vr: TextView = view.findViewById(R.id.tvBadgeVr)
    }

    override fun getItemViewType(position: Int) = if (isGrid) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == TYPE_GRID) R.layout.item_vault_grid else R.layout.item_vault_list
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.duration.text = VaultRepository.formatDuration(item.durationMs)
        holder.vr.visibility = if (item.isVr) View.VISIBLE else View.GONE
        val location = if (item.inVault) "Vault" else "Movies"
        holder.meta.text = buildString {
            if (item.width > 0) append("${item.width}×${item.height} · ")
            append(VaultRepository.formatSize(item.sizeBytes))
            append(" · ")
            append(location)
        }
        holder.thumb.load(item.thumbPath?.let { java.io.File(it) }) {
            crossfade(true)
            placeholder(android.R.color.darker_gray)
        }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        private const val TYPE_GRID = 0
        private const val TYPE_LIST = 1
        private val DIFF = object : DiffUtil.ItemCallback<VaultFile>() {
            override fun areItemsTheSame(a: VaultFile, b: VaultFile) = a.file.absolutePath == b.file.absolutePath
            override fun areContentsTheSame(a: VaultFile, b: VaultFile) = a == b
        }
    }
}
