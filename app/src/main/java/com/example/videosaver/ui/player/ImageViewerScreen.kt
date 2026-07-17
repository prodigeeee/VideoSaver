package com.example.videosaver.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.videosaver.data.MediaFile
import kotlinx.coroutines.delay

@Composable
fun ImageViewerScreen(
    playlist: List<MediaFile>,
    startIndex: Int = 0,
    onBack: () -> Unit,
) {
    var showControls by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { playlist.size })

    BackHandler { onBack() }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 40f) {
                        onBack()
                    }
                }
            }
    ) {
        // Image Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showControls = !showControls }
                )
        ) { page ->
            val media = playlist.getOrNull(page)
            if (media != null) {
                AsyncImage(
                    model = media.file,
                    contentDescription = media.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Gradient
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(tween(600)),
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
            )
        }

        // Top Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(tween(600)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBackIosNew, "Retour", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val currentMedia = playlist.getOrNull(pagerState.currentPage)
                    Text(
                        currentMedia?.name ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playlist.size > 1) {
                        Text(
                            "${pagerState.currentPage + 1} / ${playlist.size}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.6f)),
                        )
                    }
                }
            }
        }
    }
}
