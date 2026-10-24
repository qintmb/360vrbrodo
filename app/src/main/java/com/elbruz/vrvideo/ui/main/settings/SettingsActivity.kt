package com.elbruz.vrvideo.ui.main.settings

import android.os.Bundle
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.databinding.ActivitySettingsBinding
import com.elbruz.vrvideo.model.SearchEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Settings screen: reorder / set default search engine,
 * toggle dark mode.
 */
class SettingsActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySettingsBinding.inflate(layoutInflater) }
    private val prefs by lazy { UserPrefs.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val adapter = SearchEngineAdapter(this, SearchEngine.DEFAULT_ENGINES.toMutableList())
        binding.listSearchEngines.adapter = adapter

        lifecycleScope.launch {
            prefs.searchEnginesFlow.collectLatest { list ->
                adapter.updateData(list.toMutableList())
            }
        }

        binding.listSearchEngines.setOnItemClickListener { _, _, position, _ ->
            val engine = adapter.items[position]
            showEngineDialog(engine)
        }

        binding.switchDarkMode.isChecked = true
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, "Dark theme ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEngineDialog(engine: SearchEngine) {
        val options = arrayOf("Set as default", "Move to top", "Remove")
        AlertDialog.Builder(this, R.style.Theme_VrVideo_Dialog)
            .setTitle(engine.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            prefs.setCurrentEngine(engine.id)
                            Toast.makeText(this@SettingsActivity, "${engine.label} is now default", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> Toast.makeText(this, "Move — long-press to reorder", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Remove — edit DataStore", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
