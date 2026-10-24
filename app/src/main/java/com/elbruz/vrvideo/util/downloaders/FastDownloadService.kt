package com.elbruz.vrvideo.util.downloaders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.util.VaultPrefs
import com.elbruz.vrvideo.ui.main.player.VideoPlayerActivity
import android.content.Intent
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * Wraps youtubedl-android for fast segmented downloads.
 *
 * yt-dlp supports multi-connection downloads natively (via the
 * `--throttles` option). This gives us "fast download" capability
 * without bundling a separate aria2c binary.
 *
 * The download runs in-process via YoutubeDL.execute() with a
 * progress callback. A foreground notification reflects progress.
 */
object FastDownloadService {

    private const val CHANNEL_ID = "downloads_channel"
    private const val NOTIF_ID_BASE = 42

    fun startDownload(
        context: Context,
        url: String,
        filename: String,
        saveToVault: Boolean,
    ) {
        val destDir = if (saveToVault) {
            VaultPrefs.getVaultDir(context)
        } else {
            VaultPrefs.externalDownloadDir(context)
        }

        val destFile = java.io.File(destDir, filename)
        destDir.mkdirs()

        createNotificationChannel(context)

        // Build yt-dlp request: best video + best audio, mux to MP4
        val request = YoutubeDLRequest(url)
        request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
        request.addOption("-o", destFile.absolutePath)
        request.addOption("--no-playlist")
        request.addOption("--retries", "5")
        request.addOption("--fragment-retries", "10")
        request.addOption("--throttles", "16")           // parallel connections — the "fast" part
        request.addOption("--no-warnings")

        try {
            YoutubeDL.getInstance().init(context)

            val notifId = NOTIF_ID_BASE + filename.hashCode()

            YoutubeDL.getInstance().execute(
                request,
                callback = { progress, downloadedBytes, speed ->
                    // progress is 0f–1f
                    updateNotification(context, notifId, progress, destFile)
                },
            )

            // Post-success: offer to open
            val successNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.label_download))
                .setContentText("${destFile.name} ready")
                .setSmallIcon(R.drawable.ic_download)
                .setAutoCancel(true)
                .build()

            // Tap → open in player
            val playerIntent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_FROM_VAULT, saveToVault)
                putExtra(VideoPlayerActivity.EXTRA_FILENAME, destFile.name)
                putExtra(VideoPlayerActivity.EXTRA_IS_360, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pi = android.app.PendingIntent.getActivity(
                context, 0, playerIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val successBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.label_download))
                .setContentText("${destFile.name} ready")
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pi)
                .setAutoCancel(true)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifId, successBuilder.build())

        } catch (e: YoutubeDLException) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.label_download),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification(context: Context, notifId: Int, progress: Float, file: java.io.File) {
        val pct = (progress * 100).toInt()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.label_download))
            .setContentText(file.name)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, pct.coerceIn(0, 100), false)
            .build()
        nm.notify(notifId, notification)
    }
}
