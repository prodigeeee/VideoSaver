package com.example.videosaver.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videosaver.data.DownloadStatus
import com.example.videosaver.theme.*
import com.example.videosaver.ui.components.*
import com.example.videosaver.ui.download.DownloadViewModel
import com.example.videosaver.ui.download.UrlFetchState

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory(LocalContext.current)),
) {
    val urlInput by vm.urlInput.collectAsStateWithLifecycle()
    val fetchState by vm.fetchState.collectAsStateWithLifecycle()
    val showFormatPicker by vm.showFormatPicker.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle(emptyList())
    val progressMap by vm.progressMap.collectAsStateWithLifecycle()
    
    var moveTargetDownload by remember { mutableStateOf<com.example.videosaver.data.DownloadEntity?>(null) }

    // Notification permission (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .drawBehind {
                // Ambient amber radial glow at the top
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AmberGlow.copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
                        radius = size.width * 0.8f,
                        center = Offset(size.width * 0.5f, -size.width * 0.1f),
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.5f, -size.width * 0.1f),
                )
            },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Column {
                    Text(
                        "VideoSaver",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Téléchargez depuis YouTube, TikTok, Instagram et +",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ── URL input ────────────────────────────────────────────────────
            item {
                UrlInputField(
                    value = urlInput,
                    onValueChange = vm::onUrlChanged,
                    onSearch = { vm.fetchVideoInfo() },
                    onPaste = vm::onPasteFromClipboard,
                    isLoading = fetchState is UrlFetchState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Error banner ─────────────────────────────────────────────────
            if (fetchState is UrlFetchState.Error) {
                item {
                    AnimatedVisibility(visible = true, enter = slideInVertically() + fadeIn()) {
                        GlassCard(glowColor = ErrorRed.copy(0.1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    (fetchState as UrlFetchState.Error).message,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed),
                                )
                            }
                        }
                    }
                }
            }

            // ── Downloads list ────────────────────────────────────────────────
            val activeDownloads = downloads.filter {
                it.status in listOf(
                    DownloadStatus.PENDING, DownloadStatus.FETCHING_INFO, DownloadStatus.DOWNLOADING
                )
            }
            val recentDownloads = downloads.filter {
                it.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            }.take(5)

            if (activeDownloads.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsating active indicator
                        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                            0.4f, 1f,
                            infiniteRepeatable(tween(800), RepeatMode.Reverse),
                            label = "pulse_alpha",
                        )
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Amber.copy(alpha = pulse), shape = RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("En cours", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(activeDownloads, key = { it.id }) { dl ->
                    DownloadCard(
                        download = dl,
                        progress = progressMap[dl.id],
                        onCancel = { vm.cancelDownload(dl.id) },
                        onDelete = { vm.deleteDownload(dl.id, deleteFile = false) },
                        onMove   = { moveTargetDownload = dl },
                    )
                }
            }

            if (recentDownloads.isNotEmpty()) {
                item {
                    Text("Récents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(recentDownloads, key = { it.id }) { dl ->
                    DownloadCard(
                        download = dl,
                        progress = null,
                        onCancel = { vm.cancelDownload(dl.id) },
                        onDelete = { vm.deleteDownload(dl.id, deleteFile = false) },
                        onMove   = { moveTargetDownload = dl },
                    )
                }
            }

            // ── Empty state ──────────────────────────────────────────────────
            if (downloads.isEmpty()) {
                item {
                    EmptyState(modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
                }
            }
        }
    }

    // ── Format Picker Sheet ───────────────────────────────────────────────────
    if (showFormatPicker && fetchState is UrlFetchState.Success) {
        FormatPickerSheet(
            info = (fetchState as UrlFetchState.Success).info,
            onDismiss = vm::dismissFormatPicker,
            onConfirm = { fmt, audioOnly, audioFmt ->
                vm.startDownload(fmt, audioOnly, audioFmt)
            },
        )
    }

    // ── Move File Sheet ───────────────────────────────────────────────────────
    if (moveTargetDownload != null) {
        MoveFileSheet(
            onSelectFolder = { folder ->
                vm.moveDownload(moveTargetDownload!!.id, folder)
                moveTargetDownload = null
            },
            onDismiss = { moveTargetDownload = null }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "empty")
        val glowAlpha by infiniteTransition.animateFloat(
            0.3f, 0.7f,
            infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        )

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(100.dp)
                    .background(
                        Brush.radialGradient(listOf(AmberGlow.copy(alpha = glowAlpha), Color.Transparent)),
                        shape = RoundedCornerShape(50.dp),
                    )
            )
            Icon(
                Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = Amber.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Aucun téléchargement",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Collez l'URL d'une vidéo YouTube,\nTikTok, Instagram, Redgifs…",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
