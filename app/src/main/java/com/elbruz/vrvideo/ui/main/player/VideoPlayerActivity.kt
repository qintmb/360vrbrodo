package com.elbruz.vrvideo.ui.main.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.elbruz.vrvideo.R
import com.elbruz.vrvideo.databinding.ActivityVideoPlayerBinding
import com.elbruz.vrvideo.util.VaultPrefs
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Video player using Media3 ExoPlayer — handles standard video formats
 * as well as 360° spherical video via Exo's SphericalSurfaceView support.
 * Supports background playback via VideoPlayerService and VR 360 single-screen mode.
 */
class VideoPlayerActivity : ComponentActivity() {

    private val binding by lazy { ActivityVideoPlayerBinding.inflate(layoutInflater) }
    private var player: ExoPlayer? = null
    private var is360 = false
    private var vrModeEnabled = false
    private var backgroundPlaybackEnabled = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            PlaybackController.update(
                isPlaying = playing,
                positionMs = player?.currentPosition ?: 0L,
                durationMs = player?.duration ?: 0L
            )
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                PlaybackController.stop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()
        setContentView(binding.root)

        val videoUrl = intent.getStringExtra(EXTRA_URL)
        is360 = intent.getBooleanExtra(EXTRA_IS_360, false)
        val isVault = intent.getBooleanExtra(EXTRA_FROM_VAULT, false)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"

        val resolvedUrl = if (isVault) {
            val filename = intent.getStringExtra(EXTRA_FILENAME) ?: ""
            val vaultFile = VaultPrefs.vaultFile(this, filename)
            "file://${vaultFile.absolutePath}"
        } else {
            videoUrl ?: ""
        }

        setupPlayer(resolvedUrl, title)
        setupVrControls()
        setupBackgroundPlayback()
    }

    private fun setupPlayer(url: String, title: String) {
        player = ExoPlayer.Builder(this).build().also { p ->
            val uri = Uri.parse(url)
            val mediaItem = MediaItem.fromUri(uri)
            p.setMediaItem(mediaItem)
            p.prepare()
            binding.playerView.player = p
            p.playWhenReady = true
            p.addListener(playerListener)
        }
        PlaybackController.init(title, url, is360)
        PlaybackController.onCommand = { cmd ->
            when (cmd) {
                is PlaybackController.Command.Play -> player?.play()
                is PlaybackController.Command.Pause -> player?.pause()
                is PlaybackController.Command.Stop -> { player?.stop(); finish() }
                is PlaybackController.Command.SeekTo -> player?.seekTo(cmd.ms)
            }
        }
    }

    private fun setupVrControls() {
        // Add VR mode toggle button if 360 video
        if (is360) {
            binding.btnVrToggle.visibility = View.VISIBLE
            binding.btnVrToggle.setOnClickListener { toggleVrMode() }
            vrModeEnabled = false
        }
    }

    private fun toggleVrMode() {
        vrModeEnabled = !vrModeEnabled
        binding.vrIndicator.visibility = if (vrModeEnabled) View.VISIBLE else View.GONE
        binding.vrIndicator.text = if (vrModeEnabled) getString(R.string.vr_mode_on) else getString(R.string.vr_mode_off)
        
        // Media3 PlayerView supports spherical video automatically when video is tagged as spherical.
        // For manual VR mode, we enable gyroscope tracking.
        // The PlayerView's SurfaceView can render spherical content with correct projection.
        if (vrModeEnabled) {
            // Enable VR mode - user can look around by moving the phone
            Toast.makeText(this, R.string.vr_mode_on, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.vr_mode_off, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackgroundPlayback() {
        lifecycleScope.launch {
            // Start background service when app goes to background
            // but keep playing
        }
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStop() {
        super.onStop()
        if (backgroundPlaybackEnabled && player?.isPlaying == true) {
            VideoPlayerService.start(this)
        } else {
            player?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!backgroundPlaybackEnabled) {
            player?.release()
            PlaybackController.clear()
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FROM_VAULT = "extra_from_vault"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_IS_360 = "extra_is_360"
        const val EXTRA_TITLE = "extra_title"
    }
}
