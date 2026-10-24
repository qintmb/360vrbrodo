package com.elbruz.vrvideo.ui.main.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.elbruz.vrvideo.databinding.ItemSearchEngineBinding
import com.elbruz.vrvideo.model.SearchEngine

class SearchEngineAdapter(
    context: Context,
    initialItems: MutableList<SearchEngine>,
) : ArrayAdapter<SearchEngine>(context, 0, initialItems) {

    var items: MutableList<SearchEngine> = initialItems

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        createItemView(position, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        createItemView(position, parent)

    private fun createItemView(position: Int, parent: ViewGroup): View {
        val binding = ItemSearchEngineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val item = items[position]
        binding.textLabel.text = item.label
        binding.textUrl.text = item.queryTemplate
        binding.root.tag = item
        return binding.root
    }

    fun updateData(newItems: MutableList<SearchEngine>) {
        items = newItems
        notifyDataSetChanged()
    }
}
