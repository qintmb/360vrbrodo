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
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background service that drives yt-dlp downloads.
 * Started via [Intent] with extras:
 *  - "url": video URL to download
 */
class FastDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildForegroundNotification("Downloading…")
        )

        scope.launch {
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("-o", downloadsDir() + "/%(title)s.%(ext)s")
                request.addOption("--no-playlist")
                request.addOption("-f", "bestvideo*+bestaudio/best")

                val response = YoutubeDL.getInstance().execute(
                    request,
                    null
                ) { progress, _, line ->
                    updateNotification("Downloading… ${"%.0f".format(progress * 100)}%")
                }

                updateNotification("Download complete")
                Toast.makeText(this@FastDownloadService, "Download completed", Toast.LENGTH_SHORT).show()
            } catch (e: YoutubeDLException) {
                Toast.makeText(this@FastDownloadService, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                updateNotification("Download failed")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun downloadsDir(): String =
        getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    private fun buildForegroundNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("VR Video")
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_download)
        .setOnlyAlertOnce(true)
        .build()

    companion object {
        const val CHANNEL_ID = "downloads_channel"
        const val NOTIFICATION_ID = 101

        /**
         * Convenience launcher: builds the start intent and starts the service.
         */
        fun startDownload(context: Context, url: String, filename: String, saveToVault: Boolean) {
            val intent = Intent(context, FastDownloadService::class.java).apply {
                putExtra("url", url)
                putExtra("filename", filename)
                putExtra("vault", saveToVault)
            }
            context.startForegroundService(intent)
        }
    }
}
