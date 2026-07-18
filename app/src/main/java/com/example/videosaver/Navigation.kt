package com.example.videosaver

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import com.example.videosaver.data.MediaFile
import com.example.videosaver.theme.*
import com.example.videosaver.ui.browser.FolderBrowserScreen
import com.example.videosaver.ui.home.HomeScreen
import com.example.videosaver.ui.library.LibraryScreen
import com.example.videosaver.ui.settings.SettingsScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videosaver.ui.player.AudioPlayerViewModel
import com.example.videosaver.ui.player.AudioPlayerUiState
import com.example.videosaver.ui.player.LoopMode
import androidx.compose.ui.text.style.TextOverflow

private data class NavItem(
    val key: Any,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
)

@Composable
fun isInPipMode(): Boolean {
    val activity = LocalContext.current as? androidx.activity.ComponentActivity ?: return false
    var pipMode by remember { mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) activity.isInPictureInPictureMode else false) }
    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            pipMode = info.isInPictureInPictureMode
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }
    return pipMode
}

@Composable
fun MainNavigation() {
    var currentRoute    by remember { mutableStateOf<Any>(Home) }
    var videoPlayerDest by remember { mutableStateOf<VideoPlayer?>(null) }
    
    val audioVm: AudioPlayerViewModel = viewModel(factory = AudioPlayerViewModel.Factory(LocalContext.current))
    val audioState by audioVm.state.collectAsStateWithLifecycle()

    val navItems = listOf(
        NavItem(Home,     Icons.Rounded.Home,         label = "Accueil"),
        NavItem(Library,  Icons.Rounded.VideoLibrary, label = "Galerie"),
        NavItem(Browser,  Icons.Rounded.FolderOpen,   label = "Fichiers"),
        NavItem(Settings, Icons.Rounded.Settings,     label = "Réglages"),
    )

    var lastPlayerDest by remember { mutableStateOf<VideoPlayer?>(null) }
    if (videoPlayerDest != null) {
        lastPlayerDest = videoPlayerDest
    }

    LaunchedEffect(videoPlayerDest) {
        MainActivity.isVideoPlayerActive = videoPlayerDest != null
    }

    val inPip = isInPipMode()

    var audioOffsetX by remember { mutableFloatStateOf(0f) }
    var audioOffsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (!inPip) {
                    PremiumBottomBar(
                        items        = navItems,
                        currentRoute = currentRoute,
                        onSelect     = { currentRoute = it },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState  = currentRoute,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "screen_transition",
                ) { route ->
                    when (route) {
                        is Home     -> HomeScreen()
                        is Library  -> LibraryScreen(
                            onPlayAudio = { playlist, startIndex ->
                                audioVm.loadPlaylist(playlist, startIndex)
                            }
                        )
                        is Browser  -> FolderBrowserScreen(
                            onPlayMedia = { playlist, startIndex ->
                                val media = playlist.getOrNull(startIndex)
                                if (media?.isAudio == true) {
                                    audioVm.loadPlaylist(playlist, startIndex)
                                } else {
                                    videoPlayerDest = VideoPlayer(playlist, startIndex)
                                }
                            },
                        )
                        is Settings -> SettingsScreen()
                        else        -> HomeScreen()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = videoPlayerDest != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            lastPlayerDest?.let { dest ->
                val currentMedia = dest.playlist.getOrNull(dest.startIndex)
                if (currentMedia?.isImage == true) {
                    com.example.videosaver.ui.player.ImageViewerScreen(
                        playlist   = dest.playlist,
                        startIndex = dest.startIndex,
                        onBack     = { videoPlayerDest = null },
                    )
                } else {
                    com.example.videosaver.ui.player.VideoPlayerScreen(
                        playlist   = dest.playlist,
                        startIndex = dest.startIndex,
                        onBack     = { videoPlayerDest = null },
                    )
                }
            }
        }

        // ── Mini Audio Player ──────────────────────────────────────────────────
        val audioPlayerBottomPadding = if (videoPlayerDest != null) 130.dp else 80.dp
        AnimatedVisibility(
            visible = audioState.isVisible && !inPip,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = audioPlayerBottomPadding, start = 12.dp, end = 12.dp)
                .offset { IntOffset(audioOffsetX.roundToInt(), audioOffsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        audioOffsetX += dragAmount.x
                        audioOffsetY += dragAmount.y
                    }
                }
        ) {
            MiniAudioPlayer(
                state = audioState,
                onTogglePlay = audioVm::togglePlayPause,
                onNext = audioVm::playNext,
                onPrevious = audioVm::playPrevious,
                onSeek = { audioVm.seekTo((it * audioState.duration).toLong()) },
                onSkipForward = audioVm::skipForward,
                onSkipBackward = audioVm::skipBackward,
                onCycleLoop = audioVm::cycleLoopMode,
                onClose = audioVm::stopAndHide,
                onVolumeChange = audioVm::setVolume,
            )
        }
    }
}

// ─── Mini Audio Player ────────────────────────────────────────────────────────

@Composable
private fun MiniAudioPlayer(
    state: AudioPlayerUiState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onCycleLoop: () -> Unit,
    onClose: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val progress = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
    var showVolume by remember { mutableStateOf(false) }

    // Pill / gélule shape — fully rounded, works anywhere on screen
    Surface(
        color = SurfaceMid.copy(alpha = 0.97f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── Row 1 : icon + title + time + close ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Music icon badge
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberGlow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Amber, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))

                // Title
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))

                // Time display
                Text(
                    text = "${formatAudioMs(state.position)} / ${formatAudioMs(state.duration)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 9.sp,
                    ),
                )
                Spacer(Modifier.width(2.dp))

                // Close
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }

            // ── Seek slider ────────────────────────────────────────────────────
            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber,
                    inactiveTrackColor = AmberDim.copy(alpha = 0.2f),
                ),
            )

            // ── Row 2 : controls + volume ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Loop
                IconButton(onClick = onCycleLoop, modifier = Modifier.size(30.dp)) {
                    val tint = if (state.loopMode == LoopMode.OFF) TextSecondary else Amber
                    val icon = if (state.loopMode == LoopMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                }

                // Previous
                IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Skip -10s
                IconButton(onClick = onSkipBackward, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Replay10, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Play / Pause
                Surface(
                    onClick = onTogglePlay,
                    color = Amber.copy(0.18f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Skip +10s
                IconButton(onClick = onSkipForward, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Forward10, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Next
                IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.weight(1f))

                // Volume toggle + inline slider
                AnimatedVisibility(visible = showVolume) {
                    Slider(
                        value = state.volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.width(72.dp).height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Amber,
                            activeTrackColor = Amber,
                            inactiveTrackColor = AmberDim.copy(0.2f),
                        ),
                    )
                }
                if (showVolume) Spacer(Modifier.width(4.dp))

                // Volume icon (toggle)
                IconButton(
                    onClick = { showVolume = !showVolume },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = when {
                            state.volume == 0f -> Icons.Rounded.VolumeOff
                            state.volume < 0.5f -> Icons.Rounded.VolumeDown
                            else -> Icons.Rounded.VolumeUp
                        },
                        contentDescription = "Volume",
                        tint = if (showVolume) Amber else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun formatAudioMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%d:%02d".format(m, s % 60)
}

// ─── Premium Bottom Bar ───────────────────────────────────────────────────────

@Composable
private fun PremiumBottomBar(
    items: List<NavItem>,
    currentRoute: Any,
    onSelect: (Any) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Top amber glow line
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Amber.copy(0.35f), Amber.copy(0.35f), Color.Transparent)
                    ),
                    topLeft = Offset(0f, 0f),
                    size    = Size(size.width, 1.dp.toPx()),
                )
            }
            .background(Background.copy(alpha = 0.97f))
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute::class == item.key::class
                BottomNavItem(item = item, selected = selected, onClick = { onSelect(item.key) })
            }
        }
    }
}

@Composable
private fun BottomNavItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val iconTint   by animateColorAsState(if (selected) Amber else TextSecondary, label = "icon_tint")
    val labelColor by animateColorAsState(if (selected) Amber else Color.Transparent, label = "label_color")
    val bgColor    by animateColorAsState(if (selected) AmberGlow else Color.Transparent, label = "bg_color")

    Surface(
        onClick  = onClick,
        color    = Color.Transparent,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    imageVector      = item.icon,
                    contentDescription = item.label,
                    tint             = iconTint,
                    modifier         = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.label,
                style    = MaterialTheme.typography.labelSmall.copy(color = labelColor),
                maxLines = 1,
            )
        }
    }
}
