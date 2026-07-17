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

data class AudioPlayerUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L,
    val loopMode: LoopMode = LoopMode.OFF,
    val currentIndex: Int = 0,
    val playlist: List<MediaFile> = emptyList(),
    val isVisible: Boolean = false, // Controls whether the mini-player is shown
    val volume: Float = 1f,
)

class AudioPlayerViewModel(context: Context) : ViewModel() {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(AudioPlayerUiState())
    val state: StateFlow<AudioPlayerUiState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
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
    }

    /** Load an audio playlist and start playing */
    fun loadPlaylist(files: List<MediaFile>, startIndex: Int = 0) {
        val items = files.map { MediaItem.fromUri("file://${it.file.absolutePath}") }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        _state.update { it.copy(
            playlist     = files,
            currentIndex = startIndex,
            title        = files.getOrNull(startIndex)?.name ?: "",
            isVisible    = true,
        )}
    }

    fun togglePlayPause() = if (player.isPlaying) player.pause() else player.play()

    fun playNext() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    
    fun playPrevious() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }

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

    fun setVolume(vol: Float) {
        player.volume = vol
        _state.update { it.copy(volume = vol) }
    }

    /** Stop playback and hide the mini-player */
    fun stopAndHide() {
        player.stop()
        player.clearMediaItems()
        _state.update { it.copy(isVisible = false, isPlaying = false, position = 0L) }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AudioPlayerViewModel(context.applicationContext) as T
    }
}
