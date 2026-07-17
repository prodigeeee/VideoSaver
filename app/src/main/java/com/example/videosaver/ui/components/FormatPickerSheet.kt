package com.example.videosaver.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.videosaver.data.VideoFormat
import com.example.videosaver.data.VideoInfo
import com.example.videosaver.theme.*

/**
 * Bottom sheet for selecting the download format (quality, video vs audio).
 * Inspired by the dark, premium UI references provided.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatPickerSheet(
    info: VideoInfo,
    onDismiss: () -> Unit,
    onConfirm: (format: VideoFormat, isAudioOnly: Boolean, audioFormat: String) -> Unit,
) {
    var isAudioMode by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf<VideoFormat?>(null) }
    var selectedAudioFmt by remember { mutableStateOf("mp3") }

    val videoFormats = info.formats.filter { !it.isAudioOnly && it.formatId != "bestaudio" }
    val audioFormats = info.formats.filter { it.isAudioOnly || it.formatId == "bestaudio" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GlassBorder)
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // ── Video info header ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.thumbnailUrl != null) {
                    AsyncImage(
                        model = info.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceMid),
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.uploaderName != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(info.uploaderName, style = MaterialTheme.typography.bodySmall)
                    }
                    if (info.durationSeconds > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(formatDuration(info.durationSeconds), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Video / Audio toggle ───────────────────────────────────────────
            Surface(
                color = SurfaceMid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(4.dp)) {
                    ToggleTab(
                        text = "Vidéo",
                        icon = Icons.Rounded.VideoFile,
                        selected = !isAudioMode,
                        accentColor = Amber,
                        onClick = { isAudioMode = false; selectedFormat = null },
                        modifier = Modifier.weight(1f),
                    )
                    ToggleTab(
                        text = "Audio uniquement",
                        icon = Icons.Rounded.MusicNote,
                        selected = isAudioMode,
                        accentColor = TealAccent,
                        onClick = { isAudioMode = true; selectedFormat = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Format list ───────────────────────────────────────────────────
            AnimatedContent(
                targetState = isAudioMode,
                label = "format_list",
                transitionSpec = {
                    (slideInHorizontally { if (targetState) it else -it } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (targetState) -it else it } + fadeOut())
                },
            ) { audioMode ->
                if (audioMode) {
                    // Audio mode: format selector (MP3, M4A, Opus) + single "best audio" option
                    Column {
                        Text("Format audio", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("mp3", "m4a", "opus").forEach { fmt ->
                                AudioFormatChip(
                                    label = fmt.uppercase(),
                                    selected = selectedAudioFmt == fmt,
                                    onClick = { selectedAudioFmt = fmt },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Qualité", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(10.dp))
                        val bestAudio = audioFormats.firstOrNull()
                        FormatRow(
                            format = bestAudio ?: VideoFormat(
                                "bestaudio", "m4a", "audio only", null, null, null, null,
                                "Meilleure qualité (auto)", true
                            ),
                            selected = true,
                            onClick = {},
                            accentColor = TealAccent,
                        )
                    }
                } else {
                    // Video mode: scrollable list of quality options
                    Column {
                        Text("Qualité vidéo", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 280.dp),
                        ) {
                            items(videoFormats) { fmt ->
                                FormatRow(
                                    format = fmt,
                                    selected = selectedFormat?.formatId == fmt.formatId,
                                    onClick = { selectedFormat = fmt },
                                    accentColor = Amber,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Download button ───────────────────────────────────────────────
            val canConfirm = isAudioMode || selectedFormat != null
            AmberButton(
                text = if (isAudioMode) "Télécharger en ${selectedAudioFmt.uppercase()}"
                       else "Télécharger ${selectedFormat?.label ?: ""}",
                onClick = {
                    if (isAudioMode) {
                        val fmt = audioFormats.firstOrNull() ?: VideoFormat(
                            "bestaudio", "m4a", "audio only", null, null, null, null,
                            "Meilleure qualité", true,
                        )
                        onConfirm(fmt, true, selectedAudioFmt)
                    } else {
                        selectedFormat?.let { onConfirm(it, false, "mp3") }
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                leadingIcon = {
                    Icon(
                        if (isAudioMode) Icons.Rounded.MusicNote else Icons.Rounded.Download,
                        null,
                        Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

// ─── Sub-components ──────────────────────────────────────────────────────────

@Composable
private fun ToggleTab(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent,
        label = "tab_bg",
    )
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            Icon(
                icon, null,
                tint = if (selected) accentColor else TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (selected) accentColor else TextSecondary,
                ),
            )
        }
    }
}

@Composable
private fun FormatRow(
    format: VideoFormat,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
) {
    val bg by animateColorAsState(
        if (selected) accentColor.copy(alpha = 0.12f) else GlassWhite,
        label = "row_bg",
    )
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)) else BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (format.isAudioOnly) Icons.Rounded.MusicNote else Icons.Rounded.Hd,
                null,
                tint = if (selected) accentColor else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(format.label, style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (selected) accentColor else TextPrimary,
                ))
                format.filesize?.let {
                    Text(
                        formatFileSize(it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AudioFormatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (selected) TealAccent.copy(alpha = 0.18f) else GlassWhite,
        label = "chip_bg",
    )
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = if (selected) BorderStroke(1.dp, TealAccent.copy(0.6f)) else BorderStroke(1.dp, GlassBorder),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (selected) TealAccent else TextSecondary,
                ),
            )
        }
    }
}

// ─── Formatting helpers ───────────────────────────────────────────────────────

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}
