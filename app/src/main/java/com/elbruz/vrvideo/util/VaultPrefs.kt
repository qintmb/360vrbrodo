package com.elbruz.vrvideo.util

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Keeps all downloaded files out of the public Downloads/Downloads-Images
 * folders so they never show up in the Android gallery or media scanners.
 * The vault lives in the app's internal files dir under a hidden `.vault`
 * folder that is also excluded from MediaStore indexing via a
 * `.nomedia` sentinel file and a res/xml/media_scanner_extras.xml rule.
 */
object VaultPrefs {

    private const val VAULT_DIR_NAME = ".vault"
    private const val NOMEDIA = ".nomedia"

    fun getVaultDir(context: Context): File {
        return File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
    }

    fun ensureVaultDir(context: Context) {
        val dir = getVaultDir(context)
        val nomedia = File(dir, NOMEDIA)
        if (!nomedia.exists()) nomedia.createNewFile()
    }

    /** Convenience: resolve a destination path inside the vault. */
    fun vaultFile(context: Context, name: String): File {
        return File(getVaultDir(context), name)
    }

    /** Public external downloads dir (shows in gallery) — used only if the
     *  user explicitly wants a file exported via the share sheet. */
    fun externalDownloadDir(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
    }
}
