package com.example.videosaver.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
    var showTagDialog   by remember { mutableStateOf(false) }
    val inPip = com.example.videosaver.isInPipMode()
    val showControls = state.showControls && !inPip
    val context = LocalContext.current
    val repo = remember(context) { com.example.videosaver.data.BrowserRepository(context.applicationContext) }
    var allKnownTags by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        allKnownTags = repo.getAllKnownTags()
    }

    if (showTagDialog) {
        val currentMedia = state.playlist.getOrNull(state.currentIndex)
        if (currentMedia != null) {
            var currentTags by remember(currentMedia) { mutableStateOf(currentMedia.tags) }

            LaunchedEffect(currentMedia) {
                val scanned = repo.scanMediaFiles(currentMedia.file.parentFile ?: currentMedia.file)
                    .find { it.file.absolutePath == currentMedia.file.absolutePath }?.tags
                if (!scanned.isNullOrEmpty()) {
                    currentTags = scanned
                }
                allKnownTags = repo.getAllKnownTags()
            }

            com.example.videosaver.ui.library.TagEditDialog(
                initialTags = currentTags,
                allKnownTags = allKnownTags,
                onDismiss = { showTagDialog = false },
                onSave = { newTags ->
                    currentTags = newTags
                    vm.updateCurrentFileTags(newTags)
                    showTagDialog = false
                }
            )
        }
    }

    // ── Pinch-to-zoom state ───────────────────────────────────────────────
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var offsetX   by remember { mutableFloatStateOf(0f) }
    var offsetY   by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { vm.player.pause() }
    }

    LaunchedEffect(playlist, startIndex) {
        vm.loadPlaylist(playlist, startIndex)
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 1) Transform gestures : pinch-to-zoom + pan while zoomed
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (zoomScale * zoom).coerceIn(1f, 3f)
                    zoomScale = newZoom
                    if (newZoom > 1.01f) {
                        val maxOffX = size.width  * (newZoom - 1f) / 2f
                        val maxOffY = size.height * (newZoom - 1f) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffX, maxOffX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffY, maxOffY)
                    } else {
                        zoomScale = 1f; offsetX = 0f; offsetY = 0f
                    }
                }
            }
            // 2) Swipe down to close (only when not zoomed)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (zoomScale <= 1.01f && dragAmount > 40f) onBack()
                }
            }
            // 3) Tap : show controls / Double-tap : toggle play or reset zoom
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { vm.showControls() },
                    onDoubleTap = {
                        if (zoomScale > 1.01f) {
                            zoomScale = 1f; offsetX = 0f; offsetY = 0f
                        } else {
                            vm.togglePlayPause()
                        }
                    },
                )
            },
    ) {
        // ── ExoPlayer Surface ────────────────────────────────────────────────
        // FILL uses RESIZE_MODE_ZOOM : crop centred, original ratio always preserved
        val resizeMode = when (state.aspectRatio) {
            AspectRatioMode.FIT        -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL       -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.ZOOM       -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectRatioMode.RATIO_4_3  -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            AspectRatioMode.RATIO_1_1  -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player        = vm.player
                    useController = false
                    layoutParams  = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setResizeMode(resizeMode)
                }
            },
            update = { it.setResizeMode(resizeMode) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX       = zoomScale
                    scaleY       = zoomScale
                    translationX = offsetX
                    translationY = offsetY
                },
        )

        // ── Buffering indicator ───────────────────────────────────────────────
        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Amber,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ── Gradient overlays (top + bottom) ───────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(),
            exit    = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
                )
                Box(
                    Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                )
            }
        }

        // ── Controls overlay ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(),
            exit    = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize(),
        ) {
            // displayCutoutPadding + navigationBarsPadding avoids nav bar overlap
            Box(modifier = Modifier.fillMaxSize().displayCutoutPadding().navigationBarsPadding()) {

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

                    // Zoom level badge (shown when zoomed in)
                    if (zoomScale > 1.05f) {
                        Surface(
                            color = Color.White.copy(0.18f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                "×${"%.1f".format(zoomScale)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // Loop button — moved here from bottom bar to avoid Android nav bar overlap
                    val (loopIcon, loopTint) = when (state.loopMode) {
                        LoopMode.ONE -> Icons.Rounded.RepeatOne to Amber
                        LoopMode.ALL -> Icons.Rounded.Repeat     to Amber
                        LoopMode.OFF -> Icons.Rounded.Clear       to Color.White.copy(0.4f)
                    }
                    IconButton(onClick = vm::cycleLoopMode) {
                        Icon(loopIcon, "Boucle", tint = loopTint)
                    }

                    // Tag button
                    IconButton(onClick = { showTagDialog = true }) {
                        Icon(Icons.Rounded.Tag, "Gérer les tags", tint = Amber)
                    }

                    // Aspect ratio button
                    IconButton(onClick = { showRatioPicker = true }) {
                        Icon(Icons.Rounded.AspectRatio, "Ratio d'image", tint = Color.White)
                    }
                }

                // ── Center controls ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.totalFiles > 1) {
                        PlayerIconButton(Icons.Rounded.SkipPrevious, "Précédent", size = 36, onClick = vm::playPrevious)
                    }
                    PlayerIconButton(Icons.Rounded.Replay10, "−10s", size = 36, onClick = vm::skipBackward)

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

                // ── Bottom bar (seekbar + time only — loop moved to top bar) ────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPos by remember { mutableFloatStateOf(0f) }

                    val progress = if (isSeeking) seekPos else if (state.duration > 0) {
                        state.position.toFloat() / state.duration.toFloat()
                    } else 0f

                    val displayPos = if (isSeeking && state.duration > 0) (seekPos * state.duration).toLong() else state.position

                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = {
                            isSeeking = true
                            seekPos = it
                        },
                        onValueChangeFinished = {
                            vm.seekTo((seekPos * state.duration).toLong())
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor         = Amber,
                            activeTrackColor   = Amber,
                            inactiveTrackColor = Color.White.copy(0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        "${formatMs(displayPos)} / ${formatMs(state.duration)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.8f)),
                    )
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
