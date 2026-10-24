package com.elbruz.vrvideo.ui.main.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.elbruz.vrvideo.databinding.ActivityVideoPlayerBinding
import com.elbruz.vrvideo.util.VaultPrefs
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Video player using Media3 ExoPlayer — handles standard video formats
 * as well as 360° spherical video via Exo's SphericalSurfaceView support.
 */
class VideoPlayerActivity : ComponentActivity() {

    private val binding by lazy { ActivityVideoPlayerBinding.inflate(layoutInflater) }
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen, dark status bar icons
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContentView(binding.root)

        val videoUrl = intent.getStringExtra(EXTRA_URL)
        val is360 = intent.getBooleanExtra(EXTRA_IS_360, false)
        val isVault = intent.getBooleanExtra(EXTRA_FROM_VAULT, false)

        val resolvedUrl = if (isVault) {
            val filename = intent.getStringExtra(EXTRA_FILENAME) ?: ""
            val vaultFile = VaultPrefs.vaultFile(this, filename)
            "file://${vaultFile.absolutePath}"
        } else {
            videoUrl ?: ""
        }

        setupPlayer(resolvedUrl, is360)
    }

    private fun setupPlayer(url: String, is360: Boolean) {
        player = ExoPlayer.Builder(this).build().also { p ->
            val uri = Uri.parse(url)
            val mediaItem = MediaItem.fromUri(uri)
            p.setMediaItem(mediaItem)
            p.prepare()
            binding.playerView.player = p
            p.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FROM_VAULT = "extra_from_vault"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_IS_360 = "extra_is_360"
    }
}
