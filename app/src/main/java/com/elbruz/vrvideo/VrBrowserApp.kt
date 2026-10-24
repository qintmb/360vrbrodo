package com.elbruz.vrvideo

import android.app.Application
import com.elbruz.vrvideo.util.VaultPrefs

class VrBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the hidden vault directory on first run
        VaultPrefs.ensureVaultDir(this)
    }
}
