package com.elbruz.vrvideo.ui.main.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.adblock.AdBlocker
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.databinding.ActivitySettingsBinding
import com.elbruz.vrvideo.model.SearchEngine
import com.elbruz.vrvideo.ui.main.MainActivity
import com.elbruz.vrvideo.ui.main.download.DownloadsActivity
import com.elbruz.vrvideo.ui.main.vault.VaultActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings screen with persistent bottom nav:
 * search engines, AdBlocker, DNS provider, theme, vault storage.
 */
class SettingsActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySettingsBinding.inflate(layoutInflater) }
    private val prefs by lazy { UserPrefs.get(this) }
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var engineAdapter: SearchEngineAdapter

    private val keyDns = stringPreferencesKey("dns_provider")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keySaveToVault = booleanPreferencesKey("download_save_to_vault")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(binding.root)

        bottomNav = binding.bottomNav
        setupBottomNav()

        setupSearchEngines()
        setupAdblock()
        setupDns()
        setupTheme()
        setupVaultStorage()
    }

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.navSettings
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navBrowser -> { navigate(MainActivity::class.java); true }
                R.id.navDownloader -> { navigate(DownloadsActivity::class.java); true }
                R.id.navVault -> { navigate(VaultActivity::class.java); true }
                R.id.navSettings -> true
                else -> false
            }
        }
    }

    private inline fun <reified T> navigate(cls: Class<T>) {
        startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    private fun setupSearchEngines() {
        engineAdapter = SearchEngineAdapter(this, SearchEngine.DEFAULT_ENGINES.toMutableList())
        binding.rvSearchEngines.adapter = engineAdapter
        lifecycleScope.launch {
            prefs.searchEnginesFlow.collectLatest { list ->
                engineAdapter.updateData(list.toMutableList())
            }
        }
        binding.rvSearchEngines.setOnItemClickListener { _, _, position, _ ->
            showEngineDialog(engineAdapter.items[position])
        }
        binding.rvSearchEngines.setOnItemLongClickListener { _, _, position, _ ->
            showEngineDialog(engineAdapter.items[position]); true
        }
    }

    private fun showEngineDialog(engine: SearchEngine) {
        val options = arrayOf("Jadikan default", "Pindah ke atas", "Hapus")
        AlertDialog.Builder(this)
            .setTitle(engine.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch {
                        prefs.setCurrentEngine(engine.id)
                        Toast.makeText(this@SettingsActivity, "${engine.label} jadi default", Toast.LENGTH_SHORT).show()
                    }
                    else -> Toast.makeText(this, "Belum tersedia", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun setupAdblock() {
        val adBlocker = AdBlocker.get(this)
        binding.switchAdblock.isChecked = adBlocker.isEnabled()
        updateAdblockStatus(adBlocker.isEnabled())
        binding.switchAdblock.setOnCheckedChangeListener { _, isChecked ->
            adBlocker.setEnabled(isChecked)
            updateAdblockStatus(isChecked)
        }
    }

    private fun updateAdblockStatus(enabled: Boolean) {
        binding.tvAdblockStatus.text = getString(
            if (enabled) R.string.settings_adblock_status else R.string.settings_adblock_disabled
        )
    }

    private fun setupDns() {
        val options = resources.getStringArray(R.array.dns_providers)
        binding.spinnerDns.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, options
        )

        lifecycleScope.launch {
            val saved = prefs.getString(keyDns, options.first())
            val idx = options.indexOfFirst { it == saved || saved.startsWith(it) }
            if (idx >= 0) binding.spinnerDns.setSelection(idx)
        }

        binding.spinnerDns.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = options.getOrNull(position) ?: return
                if (selected == "Custom") {
                    showCustomDnsDialog()
                } else {
                    lifecycleScope.launch { prefs.setString(keyDns, selected) }
                    showDnsHint(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Android does not allow apps to silently change the system resolver.
     * Point the user at Private DNS (DoT) which is the supported path.
     */
    private fun showDnsHint(name: String) {
        val host = mapOf(
            "Google DNS (8.8.8.8)" to "dns.google",
            "Cloudflare DNS (1.1.1.1)" to "1dot1dot1dot1.cloudflare-dns.com",
            "Quad9 DNS (9.9.9.9)" to "dns.quad9.net",
            "NextDNS" to "dns.nextdns.io",
            "AdGuard DNS (94.140.14.14)" to "dns.adguard-dns.com",
            "OpenDNS (208.67.222.222)" to "doh.opendns.com",
        )[name] ?: return
        Toast.makeText(
            this,
            "Private DNS hostname: $host\n(Set in Wi-Fi → Private DNS)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showCustomDnsDialog() {
        val input = EditText(this).apply { hint = "1.1.1.1 atau dns.example.com" }
        AlertDialog.Builder(this)
            .setTitle("Custom DNS")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotBlank()) {
                    lifecycleScope.launch { prefs.setString(keyDns, "Custom: $value") }
                    Toast.makeText(this, "Custom DNS: $value", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupTheme() {
        lifecycleScope.launch {
            when (prefs.getString(keyTheme, "system")) {
                "light" -> binding.rbThemeLight.isChecked = true
                "dark" -> binding.rbThemeDark.isChecked = true
                else -> binding.rbThemeSystem.isChecked = true
            }
        }
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeLight -> "light"
                R.id.rbThemeDark -> "dark"
                else -> "system"
            }
            lifecycleScope.launch { prefs.setString(keyTheme, mode) }
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    private fun setupVaultStorage() {
        lifecycleScope.launch {
            if (prefs.getBoolean(keySaveToVault, true)) binding.rbVaultHidden.isChecked = true
            else binding.rbVaultPublic.isChecked = true
        }
        binding.rgVault.setOnCheckedChangeListener { _, checkedId ->
            val toVault = checkedId == R.id.rbVaultHidden
            lifecycleScope.launch { prefs.setBoolean(keySaveToVault, toVault) }
            Toast.makeText(
                this,
                if (toVault) "Default: Hidden Vault" else "Default: Public Movies/360VR",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.navSettings
    }
}
