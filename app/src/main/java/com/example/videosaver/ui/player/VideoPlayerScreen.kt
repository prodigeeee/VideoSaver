package com.example.videosaver.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.videosaver.data.MediaFile
import com.example.videosaver.theme.*

@Composable
fun VideoPlayerScreen(
    playlist: List<MediaFile>,
    startIndex: Int = 0,
    onBack: () -> Unit,
    vm: VideoPlayerViewModel = viewModel(factory = VideoPlayerViewModel.Factory(LocalContext.current)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showRatioPicker by remember { mutableStateOf(false) }
    val inPip = com.example.videosaver.isInPipMode()
    val showControls = state.showControls && !inPip

    LaunchedEffect(playlist, startIndex) {
        vm.loadPlaylist(playlist, startIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.player.pause()
        }
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 40f) {
                        onBack()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { vm.showControls() },
                    onDoubleTap = { vm.togglePlayPause() },
                )
            },
    ) {
        // ── ExoPlayer Surface ──────────────────────────────────────────────────
        val resizeMode = when (state.aspectRatio) {
            AspectRatioMode.FIT      -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL     -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.ZOOM     -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectRatioMode.RATIO_4_3  -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            AspectRatioMode.RATIO_1_1  -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player       = vm.player
                    useController = false // we draw our own controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setResizeMode(resizeMode)
                }
            },
            update = { it.setResizeMode(resizeMode) },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Buffering indicator ────────────────────────────────────────────────
        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Amber,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ── Gradient overlays (top + bottom) ──────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(),
            exit    = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top gradient
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent))
                        )
                )
                // Bottom gradient
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))
                        )
                )
            }
        }

        // ── Controls overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(),
            exit    = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding().systemBarsPadding()) {

                // ── Top bar ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Retour", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.title.removeSuffix(".mp4").removeSuffix(".mkv").removeSuffix(".webm"),
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.totalFiles > 1) {
                            Text(
                                "${state.currentIndex + 1} / ${state.totalFiles}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.6f)),
                            )
                        }
                    }

                    // Aspect ratio button
                    IconButton(onClick = { showRatioPicker = true }) {
                        Icon(Icons.Rounded.AspectRatio, "Ratio d'image", tint = Color.White)
                    }
                }

                // ── Center controls ───────────────────────────────────────────
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Previous (only if playlist)
                    if (state.totalFiles > 1) {
                        PlayerIconButton(Icons.Rounded.SkipPrevious, "Précédent", size = 36, onClick = vm::playPrevious)
                    }
                    PlayerIconButton(Icons.Rounded.Replay10, "−10s", size = 36, onClick = vm::skipBackward)

                    // Play / Pause (large)
                    Surface(
                        onClick = vm::togglePlayPause,
                        color = Color.White.copy(0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (state.isPlaying) "Pause" else "Lecture",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    PlayerIconButton(Icons.Rounded.Forward10, "+10s", size = 36, onClick = vm::skipForward)
                    if (state.totalFiles > 1) {
                        PlayerIconButton(Icons.Rounded.SkipNext, "Suivant", size = 36, onClick = vm::playNext)
                    }
                }

                // ── Bottom bar ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Seek bar
                    val progress = if (state.duration > 0) {
                        state.position.toFloat() / state.duration.toFloat()
                    } else 0f

                    Slider(
                        value = progress,
                        onValueChange = { vm.seekTo((it * state.duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor        = Amber,
                            activeTrackColor  = Amber,
                            inactiveTrackColor = Color.White.copy(0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Time
                        Text(
                            "${formatMs(state.position)} / ${formatMs(state.duration)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.8f)),
                        )

                        // Loop button
                        val (loopIcon, loopTint) = when (state.loopMode) {
                            LoopMode.OFF -> Icons.Rounded.Clear to Color.White.copy(0.4f)
                            LoopMode.ONE -> Icons.Rounded.RepeatOne to Amber
                            LoopMode.ALL -> Icons.Rounded.Repeat to Amber
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Loop label
                            Text(
                                when (state.loopMode) {
                                    LoopMode.OFF -> ""
                                    LoopMode.ONE -> "×1"
                                    LoopMode.ALL -> "∞"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(color = loopTint),
                            )
                            IconButton(onClick = vm::cycleLoopMode, modifier = Modifier.size(36.dp)) {
                                Icon(loopIcon, "Boucle", tint = loopTint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Aspect ratio picker ────────────────────────────────────────────────
        if (showRatioPicker) {
            AspectRatioPicker(
                current  = state.aspectRatio,
                onSelect = { vm.setAspectRatio(it); showRatioPicker = false },
                onDismiss = { showRatioPicker = false },
            )
        }
    }
}

// ─── Aspect Ratio Picker ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AspectRatioPicker(
    current: AspectRatioMode,
    onSelect: (AspectRatioMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text("Ratio d'image", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Choisissez comment la vidéo remplit l'écran", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))

            AspectRatioMode.entries.forEach { mode ->
                val selected = mode == current
                val bg by animateColorAsState(
                    if (selected) AmberGlow else GlassWhite, label = "ratio_bg"
                )
                Surface(
                    onClick = { onSelect(mode) },
                    color = bg,
                    shape = RoundedCornerShape(14.dp),
                    border = if (selected) BorderStroke(1.dp, Amber.copy(0.5f))
                             else BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            ratioIcon(mode), null,
                            tint = if (selected) Amber else TextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = if (selected) Amber else TextPrimary,
                                ),
                            )
                            Text(ratioDescription(mode), style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Amber, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    desc: String,
    size: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size.dp)) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.7).dp))
    }
}

private fun ratioIcon(mode: AspectRatioMode): ImageVector = when (mode) {
    AspectRatioMode.FIT      -> Icons.Rounded.FitScreen
    AspectRatioMode.FILL     -> Icons.Rounded.Fullscreen
    AspectRatioMode.ZOOM     -> Icons.Rounded.ZoomIn
    AspectRatioMode.RATIO_16_9 -> Icons.Rounded.Crop169
    AspectRatioMode.RATIO_4_3  -> Icons.Rounded.Crop54
    AspectRatioMode.RATIO_1_1  -> Icons.Rounded.CropSquare
}

private fun ratioDescription(mode: AspectRatioMode): String = when (mode) {
    AspectRatioMode.FIT      -> "Affiche la vidéo entière sans rogner"
    AspectRatioMode.FILL     -> "Remplit l'écran, peut rogner légèrement"
    AspectRatioMode.ZOOM     -> "Zoom ×1.5 dans la vidéo"
    AspectRatioMode.RATIO_16_9 -> "Force le ratio 16:9 (écran large)"
    AspectRatioMode.RATIO_4_3  -> "Force le ratio 4:3 (TV classique)"
    AspectRatioMode.RATIO_1_1  -> "Format carré"
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%d:%02d".format(m, s % 60)
}
