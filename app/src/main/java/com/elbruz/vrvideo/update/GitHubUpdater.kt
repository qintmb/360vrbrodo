package com.elbruz.vrvideo.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater ala GitHub Releases.
 * 1. Fetch https://api.github.com/repos/<owner>/<repo>/releases/latest
 * 2. Compare tag versionName vs current versionName
 * 3. Download .apk asset via DownloadManager, then prompt install.
 *
 * APK must be signed with the same release keystore or Android blocks the update.
 */
object GitHubUpdater {

    const val REPO_URL = "https://api.github.com/repos/qintmb/360vrbrodo"
    private const val RELEASES_API = "$REPO_URL/releases/latest"
    private const val PREF_KEY_LATEST_VERSION = "updater_latest_version"

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String,
        val sizeBytes: Long,
    )

    fun checkForUpdate(context: Context, onAvailable: (ReleaseInfo) -> Unit, onUpToDate: (() -> Unit)? = null) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val release = fetchLatestReleaseInternal()
                if (release == null) {
                    withContext(Dispatchers.Main) { onUpToDate?.invoke() }
                    return@launch
                }
                val current = currentVersionName(context)
                val isNewer = compareVersions(release.versionName, current) > 0
                withContext(Dispatchers.Main) {
                    if (isNewer) onAvailable(release) else onUpToDate?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onUpToDate?.invoke() }
            }
        }
    }

    /** Internal fetch, reusable by UpdateWorker (background periodic check). */
    fun fetchLatestReleaseInternal(): ReleaseInfo? = fetchLatestRelease()

    private fun fetchLatestRelease(): ReleaseInfo? {
        val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "360VR-Browser-Updater")
        if (conn.responseCode != 200) return null

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tagName = json.optString("tag_name", "")
        val notes = json.optString("body", "")
        val assets = json.optJSONArray("assets") ?: return null

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk")) {
                return ReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    apkUrl = asset.optString("browser_download_url", ""),
                    releaseNotes = notes,
                    sizeBytes = asset.optLong("size", 0L),
                )
            }
        }
        return null
    }

    fun downloadAndInstall(context: Context, release: ReleaseInfo) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "360vr-${release.versionName}.apk"

        // App-internal folder: not visible in gallery/system Downloads, and
        // avoids FileUriExposedException when opening the install intent.
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val destFile = File(destDir, fileName)

        val request = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
            setTitle(fileName)
            setDescription("Mengunduh update ${release.versionName}...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destFile))
            setMimeType("application/vnd.android.package-archive")
        }
        val downloadId = dm.enqueue(request)

        // Install when download finishes.
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL && destFile.exists()) {
                            installApk(ctx, destFile)
                        } else {
                            Toast.makeText(ctx, "Update gagal diunduh", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                runCatching { ctx.unregisterReceiver(this) }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        Toast.makeText(context, "Mengunduh update ${release.versionName}...", Toast.LENGTH_LONG).show()
    }

    private fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Izinkan instal dari app ini, lalu buka lagi Update", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Instal gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }

    /** Compare dotted versions: returns >0 if a newer, <0 if older, 0 equal. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
