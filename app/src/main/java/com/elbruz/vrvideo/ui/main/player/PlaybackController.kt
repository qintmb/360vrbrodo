package com.elbruz.vrvideo.ui.main.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized playback state controller.
 * VideoPlayerActivity and VideoPlayerService both observe/control this.
 */
object PlaybackController {

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val title: String = "",
        val videoUrl: String = "",
        val is360: Boolean = false,
    )

    private val _state = MutableStateFlow<PlaybackState?>(null)
    val state: StateFlow<PlaybackState?> = _state.asStateFlow()

    // Listener called by service when play/pause/seek happens
    var onCommand: ((Command) -> Unit)? = null

    sealed class Command {
        object Play : Command()
        object Pause : Command()
        object Stop : Command()
        data class SeekTo(val ms: Long) : Command()
    }

    fun init(title: String, videoUrl: String, is360: Boolean) {
        _state.value = PlaybackState(
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            title = title,
            videoUrl = videoUrl,
            is360 = is360,
        )
    }

    fun update(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val current = _state.value ?: return
        _state.value = current.copy(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun play() {
        onCommand?.invoke(Command.Play)
    }

    fun pause() {
        onCommand?.invoke(Command.Pause)
    }

    fun stop() {
        onCommand?.invoke(Command.Stop)
        _state.value = null
    }

    fun seekTo(ms: Long) {
        onCommand?.invoke(Command.SeekTo(ms))
    }

    fun clear() {
        _state.value = null
        onCommand = null
    }
}
