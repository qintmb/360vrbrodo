package com.elbruz.vrvideo.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background periodic update checker (WorkManager).
 * Runs every ~12h even when the app is not opened; posts a system
 * notification when a newer GitHub release is available.
 * Tapping the notification opens MainActivity which shows the
 * update dialog (download + install, no uninstall needed).
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val release = withContext(Dispatchers.IO) {
            runCatching { GitHubUpdater.fetchLatestReleaseInternal() }.getOrNull()
        } ?: return Result.success()

        val current = currentVersionName(applicationContext)
        if (GitHubUpdater.compareVersions(release.versionName, current) > 0) {
            showUpdateNotification(release)
        }
        return Result.success()
    }

    private fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) {
        "0.0.0"
    }

    private fun showUpdateNotification(release: GitHubUpdater.ReleaseInfo) {
        val context = applicationContext
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_UPDATE_DIALOG, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, UPDATE_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vr_logo)
            .setContentTitle("Update tersedia: v${release.versionName}")
            .setContentText("Versi baru siap diunduh — tap untuk update")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    release.releaseNotes.ifBlank {
                        "Versi baru v${release.versionName} tersedia. Update tanpa kehilangan data."
                    }.take(200)
                )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4201
        private const val UPDATE_REQUEST_CODE = 4202
        private const val WORK_NAME = "github_update_check"

        /** Schedule periodic background check (default every 12h). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Update Aplikasi",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifikasi ketika versi baru tersedia di GitHub"
                    setShowBadge(true)
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }
}
