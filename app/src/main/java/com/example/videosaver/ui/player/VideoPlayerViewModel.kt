package com.example.videosaver.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.videosaver.data.BrowserRepository
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
    val loopMode: LoopMode = LoopMode.ONE,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val showControls: Boolean = true,
    val currentIndex: Int = 0,
    val totalFiles: Int = 1,
    val playlist: List<MediaFile> = emptyList(),
    val isBuffering: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
)

class VideoPlayerViewModel(context: Context) : ViewModel() {

    private val repo = BrowserRepository(context.applicationContext)

    private val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setConstantBitrateSeekingAlwaysEnabled(true)

    private val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

    private val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            2500,  // minBufferMs
            30000, // maxBufferMs
            1000,  // bufferForPlaybackMs
            2000   // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory))
        .setLoadControl(loadControl)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)

        // Single listener — avoids race condition from duplicate onIsPlayingChanged calls
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                showControls() // restart auto-hide on every play/pause
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                _state.update { it.copy(
                    currentIndex = idx,
                    title = it.playlist.getOrNull(idx)?.name ?: "",
                    hasError = false,
                    errorMessage = null,
                )}
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.update { it.copy(
                    isBuffering = false,
                    hasError = true,
                    errorMessage = "Codec ou format vidéo non lisible : ${error.localizedMessage ?: "Fichier corrompu"}"
                )}
            }
        })

        // Position polling — delay() is non-blocking, safe on Main
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

    private var autoHideJob: kotlinx.coroutines.Job? = null

    /** Load a playlist starting at [startIndex] */
    fun loadPlaylist(files: List<MediaFile>, startIndex: Int = 0) {
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        // Uri.fromFile is more reliable than string "file://" for paths with special chars
        val items = files.map { MediaItem.fromUri(android.net.Uri.fromFile(it.file)) }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
        player.repeatMode = Player.REPEAT_MODE_ONE // Default: loop current video
        _state.update { it.copy(
            playlist     = files,
            totalFiles   = files.size,
            currentIndex = startIndex,
            title        = files.getOrNull(startIndex)?.name ?: "",
            loopMode     = LoopMode.ONE,
        )}
        showControls()
    }

    fun togglePlayPause() = if (player.isPlaying) player.pause() else player.play()

    fun seekTo(posMs: Long) {
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        player.seekTo(posMs)
        showControls()
    }

    fun skipForward(ms: Long = 10_000L) {
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        val dur = player.duration.coerceAtLeast(0L)
        val target = if (dur > 0) (player.currentPosition + ms).coerceAtMost(dur) else player.currentPosition + ms
        player.seekTo(target)
        showControls()
    }

    fun skipBackward(ms: Long = 10_000L) {
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        val target = (player.currentPosition - ms).coerceAtLeast(0L)
        player.seekTo(target)
        showControls()
    }

    fun playNext() { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); showControls() }
    fun playPrevious() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); showControls() }

    fun cycleLoopMode() {
        // ONE (default) → ALL → OFF → ONE
        val next = when (_state.value.loopMode) {
            LoopMode.ONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.OFF
            LoopMode.OFF -> LoopMode.ONE
        }
        player.repeatMode = when (next) {
            LoopMode.ONE -> Player.REPEAT_MODE_ONE
            LoopMode.ALL -> Player.REPEAT_MODE_ALL
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
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

    fun updateCurrentFileTags(tags: List<String>) {
        val currentMedia = _state.value.playlist.getOrNull(_state.value.currentIndex) ?: return
        viewModelScope.launch {
            repo.updateFileTags(currentMedia.file, tags)
            val updatedPlaylist = _state.value.playlist.toMutableList()
            updatedPlaylist[_state.value.currentIndex] = currentMedia.copy(tags = tags)
            _state.update { it.copy(playlist = updatedPlaylist) }
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
