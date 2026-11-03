package com.elbruz.vrvideo.ui.main.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background playback service ala ClintBrowser: MediaSession + media-style
 * notification controls. Plain Service (no lifecycle-service dependency);
 * collects PlaybackController.state on its own scope.
 */
class VideoPlayerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var mediaSession: MediaSession? = null
    private var promoted = false
    private var noisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                PlaybackController.pause()
            }
        }
    }

    private val positionTicker = object : Runnable {
        override fun run() {
            val s = PlaybackController.state.value
            if (s != null && s.isPlaying) {
                // Approximate position advance; activity publishes exact values.
                PlaybackController.update(
                    isPlaying = true,
                    positionMs = s.positionMs + 1000L,
                    durationMs = s.durationMs,
                )
                mediaSession?.setPlaybackState(buildPlaybackState(s.copy(positionMs = s.positionMs + 1000L)))
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, "VrVideoPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = PlaybackController.play()
                override fun onPause() = PlaybackController.pause()
                override fun onStop() = PlaybackController.stop()
                override fun onSeekTo(pos: Long) = PlaybackController.seekTo(pos)
            })
            setSessionActivity(contentIntent())
            isActive = true
        }
        scope.launch {
            PlaybackController.state.collect { render(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> PlaybackController.play()
            ACTION_PAUSE -> PlaybackController.pause()
            ACTION_STOP -> PlaybackController.stop()
        }
        render(PlaybackController.state.value)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(positionTicker)
        unregisterNoisyReceiver()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun render(state: PlaybackController.PlaybackState?) {
        if (state == null) {
            handler.removeCallbacks(positionTicker)
            unregisterNoisyReceiver()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            promoted = false
            stopSelf()
            return
        }
        mediaSession?.setMetadata(buildMetadata(state))
        mediaSession?.setPlaybackState(buildPlaybackState(state))
        promote(buildNotification(state))
        if (state.isPlaying) {
            registerNoisyReceiver()
            handler.removeCallbacks(positionTicker)
            handler.postDelayed(positionTicker, 1000L)
        } else {
            unregisterNoisyReceiver()
            handler.removeCallbacks(positionTicker)
        }
    }

    private fun buildMetadata(state: PlaybackController.PlaybackState) = android.media.MediaMetadata.Builder()
        .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, state.title)
        .putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE, state.title)
        .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
        .build()

    private fun buildPlaybackState(state: PlaybackController.PlaybackState): PlaybackState {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
        return PlaybackState.Builder()
            .setActions(actions)
            .setState(
                if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                state.positionMs,
                if (state.isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime()
            )
            .build()
    }

    private fun promote(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            promoted = true
        }
    }

    private fun buildNotification(state: PlaybackController.PlaybackState?): Notification {
        val playing = state?.isPlaying == true
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vr_logo)
            .setContentTitle(state?.title ?: getString(R.string.app_name))
            .setContentIntent(contentIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.addAction(
            NotificationCompat.Action(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) getString(R.string.action_pause) else getString(R.string.action_play),
                serviceIntent(if (playing) ACTION_PAUSE else ACTION_PLAY)
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_close),
                serviceIntent(ACTION_STOP)
            )
        )
        return builder.build()
    }

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, VideoPlayerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun contentIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.video_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.video_playback_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    companion object {
        private const val CHANNEL_ID = "video_playback"
        private const val NOTIFICATION_ID = 9101
        private const val ACTION_PLAY = "com.elbruz.vrvideo.PLAY"
        private const val ACTION_PAUSE = "com.elbruz.vrvideo.PAUSE"
        private const val ACTION_STOP = "com.elbruz.vrvideo.STOP"

        @Volatile var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, VideoPlayerService::class.java))
            }
        }
    }
}
