package com.elbruz.vrvideo

import android.app.Application
import com.elbruz.vrvideo.update.UpdateWorker
import com.elbruz.vrvideo.util.VaultPrefs

class VrBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the hidden vault directory on first run
        VaultPrefs.ensureVaultDir(this)
        // Periodic GitHub update check → system notification when new release exists
        UpdateWorker.schedule(this)
        UpdateWorker.createChannel(this)
    }
}
