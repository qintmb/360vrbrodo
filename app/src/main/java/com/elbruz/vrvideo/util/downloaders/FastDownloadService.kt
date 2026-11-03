package com.elbruz.vrvideo.util.downloaders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.util.VaultPrefs
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service driving yt-dlp downloads.
 * Publishes progress to [DownloadRepository] so the Downloader tab
 * shows live queue/progress; saves into the hidden vault or the
 * public Movies/360VR folder depending on the user's choice.
 */
class FastDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        val filename = intent?.getStringExtra("filename") ?: "video.mp4"
        val saveToVault = intent?.getBooleanExtra("vault", true) ?: true
        val formatSelector = intent?.getStringExtra("format")
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val item = DownloadRepository.enqueue(url, filename, saveToVault)

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Downloading…", 0))

        scope.launch {
            try {
                val outDir = if (saveToVault) VaultPrefs.getVaultDir(this@FastDownloadService)
                else VaultRepositoryPublic.dir(this@FastDownloadService)

                val stem = filename.substringBeforeLast('.', filename)
                val request = YoutubeDLRequest(url)
                request.addOption("-o", File(outDir, "$stem.%(ext)s").absolutePath)
                request.addOption("--no-playlist")
                request.addOption("-f", formatSelector ?: "bestvideo*+bestaudio/best")

                DownloadRepository.update(item.id) { it.copy(status = DownloadRepository.Status.DOWNLOADING) }

                val response = YoutubeDL.getInstance().execute(request, null) { progress, eta, line ->
                    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
                    DownloadRepository.updateProgress(item.id, pct, 0L, eta, 0L, -1L)
                    updateNotification("Downloading… $pct%", pct)
                }

                // yt-dlp may rename the extension; find the produced file.
                val produced = outDir.listFiles()
                    ?.filter { it.name.startsWith(stem) && it.isFile }
                    ?.maxByOrNull { it.lastModified() }
                if (produced != null) {
                    DownloadRepository.markCompleted(item.id, produced.absolutePath)
                    notify("Download complete", produced.name)

                    // Refresh the gallery entry for the public folder.
                    if (!saveToVault) {
                        android.media.MediaScannerConnection.scanFile(
                            this@FastDownloadService, arrayOf(produced.absolutePath), null, null
                        )
                    }
                } else {
                    DownloadRepository.markFailed(item.id, "output file not found")
                    notify("Download failed", filename)
                }
            } catch (e: YoutubeDLException) {
                DownloadRepository.markFailed(item.id, e.message ?: "download failed")
                notify("Download failed", filename)
            } catch (e: Exception) {
                DownloadRepository.markFailed(item.id, e.message ?: "download failed")
                notify("Download failed", filename)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String, progress: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildForegroundNotification(text, progress))
    }

    private fun notify(title: String, text: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID + 1, n)
    }

    private fun buildForegroundNotification(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VR Video")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .build()

    companion object {
        const val CHANNEL_ID = "downloads_channel"
        const val NOTIFICATION_ID = 101

        fun startDownload(
            context: Context,
            url: String,
            filename: String,
            saveToVault: Boolean,
            formatSelector: String? = null,
        ) {
            val intent = Intent(context, FastDownloadService::class.java).apply {
                putExtra("url", url)
                putExtra("filename", filename)
                putExtra("vault", saveToVault)
                putExtra("format", formatSelector)
            }
            context.startForegroundService(intent)
        }
    }
}

/** Public Movies/360VR folder (visible in gallery). */
object VaultRepositoryPublic {
    fun dir(context: Context): File =
        File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MOVIES), "360VR").apply { mkdirs() }
}
