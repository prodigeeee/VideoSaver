package com.example.videosaver.ui.browser

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Composable that displays a video thumbnail using MediaMetadataRetriever on a CPU IO dispatcher.
 * This intentionally avoids Coil VideoFrameDecoder which opens hardware MediaCodec instances
 * and competes with ExoPlayer for the limited pool of hardware decoders on the device.
 */
@Composable
fun VideoThumbnail(
    file: File,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(file.absolutePath)
                    val raw = retriever.getFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: return@runCatching null
                    val maxDim = 300
                    val scale = maxDim.toFloat() / maxOf(raw.width, raw.height).coerceAtLeast(1)
                    if (scale < 1f) {
                        val w = (raw.width * scale).toInt().coerceAtLeast(1)
                        val h = (raw.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                        raw.recycle()
                        scaled
                    } else raw
                }
            }.getOrNull()
        }
    }

    val bmp = bitmap
    if (bmp != null && !bmp.isRecycled) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}
