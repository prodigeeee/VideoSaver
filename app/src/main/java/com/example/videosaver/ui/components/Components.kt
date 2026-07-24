package com.example.videosaver.ui.components

import android.os.Environment
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.videosaver.data.DownloadEntity
import com.example.videosaver.data.DownloadStatus
import com.example.videosaver.theme.*
import com.example.videosaver.ui.download.DownloadProgress
import kotlin.math.sin

// ─── Glass Card ──────────────────────────────────────────────────────────────

/**
 * A glassmorphism-style card with a subtle amber border and background glow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = AmberGlow,
    cornerRadius: Dp = 20.dp,
    borderStroke: BorderStroke = BorderStroke(1.dp, GlassBorder),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                // Subtle ambient glow behind card
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        radius = size.maxDimension * 0.7f,
                    ),
                    radius = size.maxDimension * 0.7f,
                    center = Offset(size.width * 0.3f, 0f),
                )
            }
            .background(GlassWhite, shape)
            .border(borderStroke, shape)
            .padding(16.dp),
        content = content,
    )
}

// ─── Animated Amber Glow Button ────────────────────────────────────────────────

@Composable
fun AmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "btn_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .drawBehind {
                if (enabled) drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Amber.copy(alpha = glowAlpha * 0.3f), Color.Transparent),
                        radius = size.maxDimension,
                    ),
                    radius = size.maxDimension,
                )
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = Background,
            disabledContainerColor = AmberDim.copy(alpha = 0.4f),
            disabledContentColor = TextDisabled,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        leadingIcon?.invoke()
        if (leadingIcon != null) Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium.copy(color = Background))
    }
}

// ─── Download Progress Card ───────────────────────────────────────────────────

@Composable
fun DownloadCard(
    download: DownloadEntity,
    progress: DownloadProgress?,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isActive = download.status == DownloadStatus.DOWNLOADING ||
            download.status == DownloadStatus.FETCHING_INFO ||
            download.status == DownloadStatus.PENDING

    val accentColor = when {
        download.isAudioOnly -> TealAccent
        download.status == DownloadStatus.COMPLETED -> SuccessGreen
        download.status == DownloadStatus.FAILED -> ErrorRed
        else -> Amber
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = accentColor.copy(alpha = 0.1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail or placeholder icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceMid),
                contentAlignment = Alignment.Center,
            ) {
                if (download.thumbnailUrl != null) {
                    AsyncImage(
                        model = download.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Tinted overlay
                    Box(Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.15f)))
                } else {
                    Icon(
                        imageVector = if (download.isAudioOnly) Icons.Rounded.MusicNote else Icons.Rounded.VideoFile,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))

                // Status line
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Format badge
                    Surface(
                        color = accentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = if (download.isAudioOnly) "AUDIO" else download.quality,
                            style = MaterialTheme.typography.labelSmall.copy(color = accentColor),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (download.status) {
                            DownloadStatus.COMPLETED -> "Terminé"
                            DownloadStatus.FAILED -> "Erreur"
                            DownloadStatus.CANCELLED -> "Annulé"
                            DownloadStatus.FETCHING_INFO -> "Récupération des infos…"
                            DownloadStatus.DOWNLOADING -> progress?.let {
                                val speed = formatSpeed(it.speedBytesPerSec)
                                "${it.percent}% · $speed${it.eta?.let { e -> " · $e" } ?: ""}"
                            } ?: "Téléchargement…"
                            DownloadStatus.PENDING -> "En attente…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Progress bar for active downloads
                if (isActive) {
                    Spacer(Modifier.height(8.dp))
                    val animProgress by animateFloatAsState(
                        targetValue = (progress?.percent ?: 0) / 100f,
                        animationSpec = tween(300),
                        label = "progress",
                    )
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.15f),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action buttons
            Row {
                if (download.status == DownloadStatus.COMPLETED) {
                    IconButton(
                        onClick = onMove,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DriveFileMove,
                            contentDescription = "Déplacer",
                            tint = Amber,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                
                IconButton(
                    onClick = if (isActive) onCancel else onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.Close else Icons.Rounded.DeleteOutline,
                        contentDescription = if (isActive) "Annuler" else "Supprimer",
                        tint = if (isActive) ErrorRed else TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─── URL Input Field ──────────────────────────────────────────────────────────

@Composable
fun UrlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPaste: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    GlassCard(modifier = modifier, cornerRadius = 24.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "Collez ou entrez une URL…",
                            style = MaterialTheme.typography.bodyLarge.copy(color = TextDisabled),
                        )
                    }
                    inner()
                },
            )

            Spacer(Modifier.width(8.dp))

            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Clear, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // Paste button
            TextButton(
                onClick = {
                    val text = clipboardManager.getText()?.text
                    if (!text.isNullOrBlank()) onPaste(text)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Rounded.ContentPaste, null, tint = Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Coller", style = MaterialTheme.typography.labelMedium.copy(color = Amber))
            }
        }

        if (value.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AmberButton(
                text = if (isLoading) "Analyse en cours…" else "Analyser la vidéo",
                onClick = onSearch,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = if (isLoading) null else {
                    { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) }
                },
            )
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun formatSpeed(bps: Long): String = when {
    bps == 0L -> ""
    bps < 1024 * 1024 -> "${bps / 1024}KB/s"
    else -> "${"%.1f".format(bps / (1024.0 * 1024.0))}MB/s"
}


