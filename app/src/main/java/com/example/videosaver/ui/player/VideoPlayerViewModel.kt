package com.example.videosaver.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.videosaver.data.MediaFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class LoopMode { OFF, ONE, ALL }
enum class AspectRatioMode(val label: String) {
    FIT("Ajuster"),
    FILL("Remplir"),
    ZOOM("Zoom 1.5×"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_1_1("1:1"),
}

data class PlayerUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L,
    val loopMode: LoopMode = LoopMode.OFF,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val showControls: Boolean = true,
    val currentIndex: Int = 0,
    val totalFiles: Int = 1,
    val playlist: List<MediaFile> = emptyList(),
    val isBuffering: Boolean = false,
)

class VideoPlayerViewModel(context: Context) : ViewModel() {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                _state.update { it.copy(
                    currentIndex = idx,
                    title = it.playlist.getOrNull(idx)?.name ?: "",
                )}
            }
        })

        // Position polling
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (player.isPlaying) {
                    _state.update { it.copy(
                        position = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0L),
                    )}
                }
            }
        }

        // Show controls briefly when playback state changes (play/pause)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                showControls()
            }
        })
    }

    private var autoHideJob: kotlinx.coroutines.Job? = null

    /** Load a playlist starting at [startIndex] */
    fun loadPlaylist(files: List<MediaFile>, startIndex: Int = 0) {
        val items = files.map { MediaItem.fromUri("file://${it.file.absolutePath}") }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        _state.update { it.copy(
            playlist     = files,
            totalFiles   = files.size,
            currentIndex = startIndex,
            title        = files.getOrNull(startIndex)?.name ?: "",
        )}
        showControls() // Start auto-hide timer immediately
    }

    fun togglePlayPause() = if (player.isPlaying) player.pause() else player.play()

    fun seekTo(posMs: Long) {
        player.seekTo(posMs)
        showControls()
    }

    fun skipForward(ms: Long = 10_000L) {
        player.seekTo((player.currentPosition + ms).coerceAtMost(player.duration))
        showControls()
    }

    fun skipBackward(ms: Long = 10_000L) {
        player.seekTo((player.currentPosition - ms).coerceAtLeast(0L))
        showControls()
    }

    fun playNext() { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); showControls() }
    fun playPrevious() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); showControls() }

    fun cycleLoopMode() {
        val next = when (_state.value.loopMode) {
            LoopMode.OFF -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.OFF
        }
        player.repeatMode = when (next) {
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
            LoopMode.ONE -> Player.REPEAT_MODE_ONE
            LoopMode.ALL -> Player.REPEAT_MODE_ALL
        }
        _state.update { it.copy(loopMode = next) }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _state.update { it.copy(aspectRatio = mode) }
    }

    fun showControls() {
        _state.update { it.copy(showControls = true) }
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(3500)
            _state.update { it.copy(showControls = false) }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoPlayerViewModel(context.applicationContext) as T
    }
}
