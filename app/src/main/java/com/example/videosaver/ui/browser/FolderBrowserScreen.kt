package com.example.videosaver.ui.browser

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.videosaver.data.BrowserEntry
import com.example.videosaver.data.FolderEntity
import com.example.videosaver.data.MediaFile
import com.example.videosaver.theme.*
import java.io.File

// ─── Sort options for the browser media view ──────────────────────────────────

private enum class BrowserSort(val label: String) {
    NAME_ASC("Nom A→Z"),
    NAME_DESC("Nom Z→A"),
    DATE_DESC("Plus récent"),
    DATE_ASC("Plus ancien"),
    SIZE_DESC("Plus grand"),
    SIZE_ASC("Plus petit"),
}

// ─── Main screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    onPlayMedia: (playlist: List<MediaFile>, startIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    vm: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory(LocalContext.current)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    // Media view controls
    var columns      by remember(state.folderPrefs) { mutableIntStateOf(state.folderPrefs.columns) }
    var sortBy       by remember(state.folderPrefs) { mutableStateOf(
        runCatching { BrowserSort.valueOf(state.folderPrefs.sortBy) }.getOrDefault(BrowserSort.NAME_ASC)
    ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var mediaFilter  by remember(state.folderPrefs) { mutableStateOf(state.folderPrefs.mediaFilter) }

    // Commit changes to VM when they change
    LaunchedEffect(columns, sortBy, mediaFilter) {
        vm.updateFolderPrefs(columns, sortBy.name, mediaFilter)
    }

    // Multi-selection
    var selectedMedia by remember { mutableStateOf(emptySet<MediaFile>()) }
    val inSelectionMode = selectedMedia.isNotEmpty()
    var actionSheetType by remember { mutableStateOf<String?>(null) } // "COPY" or "MOVE"

    LaunchedEffect(state.currentPath) {
        selectedMedia = emptySet()
    }

    if (actionSheetType != null) {
        com.example.videosaver.ui.components.MoveFileSheet(
            favorites = favorites,
            onSelectFolder = { folder ->
                if (actionSheetType == "MOVE") {
                    vm.moveSelected(selectedMedia.toList(), folder)
                } else {
                    vm.copySelected(selectedMedia.toList(), folder)
                }
                selectedMedia = emptySet()
                actionSheetType = null
            },
            onDismiss = { actionSheetType = null }
        )
    }

    // Storage permission
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.navigateTo(state.currentPath) }
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            ))
        } else {
            permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    // Sorted + filtered media in current dir
    val sortedMedia = remember(state.mediaInCurrentDir, sortBy, mediaFilter) {
        state.mediaInCurrentDir
            .filter { f ->
                when (mediaFilter) {
                    "video" -> f.isVideo
                    "audio" -> f.isAudio
                    "image" -> f.isImage
                    else    -> true
                }
            }
            .let { list ->
                when (sortBy) {
                    BrowserSort.NAME_ASC  -> list.sortedBy { it.name.lowercase() }
                    BrowserSort.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                    BrowserSort.DATE_DESC -> list.sortedByDescending { it.lastModified }
                    BrowserSort.DATE_ASC  -> list.sortedBy { it.lastModified }
                    BrowserSort.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                    BrowserSort.SIZE_ASC  -> list.sortedBy { it.sizeBytes }
                }
            }
    }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header bar ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(SurfaceDark, Background)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.breadcrumb.size > 1) {
                        IconButton(onClick = vm::navigateUp, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.ArrowBackIosNew, "Retour", tint = Amber, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.currentPath.name.ifBlank { "Stockage" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.mediaInCurrentDir.isNotEmpty()) {
                            Text(
                                "${state.mediaInCurrentDir.count { it.isVideo }} vidéo(s) • " +
                                "${state.mediaInCurrentDir.count { it.isImage }} image(s)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            "Favori",
                            tint = if (state.isFavorite) Amber else TextSecondary,
                        )
                    }
                }

                // Breadcrumb
                if (state.breadcrumb.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.breadcrumb) { crumb ->
                            val isLast = crumb == state.breadcrumb.last()
                            Text(
                                crumb.name.take(16),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isLast) Amber else TextSecondary,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { if (!isLast) vm.navigateTo(crumb) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            if (!isLast) {
                                Text("›", style = MaterialTheme.typography.labelMedium.copy(color = TextDisabled))
                            }
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                // ── Quick-access roots ─────────────────────────────────────────
                if (state.breadcrumb.size <= 1 && vm.rootDirectories.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text("Emplacements rapides", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(vm.rootDirectories) { root ->
                                    QuickAccessChip(root, onClick = { vm.navigateTo(root.file) })
                                }
                            }
                        }
                    }
                }

                // ── Favorites ─────────────────────────────────────────────────
                if (state.breadcrumb.size <= 1 && favorites.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text("Favoris", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        }
                    }
                    items(favorites) { fav ->
                        FavoriteRow(fav, onClick = { vm.openFavorite(fav) })
                    }
                    item {
                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }

                // ── Loading ────────────────────────────────────────────────────
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Amber)
                        }
                    }
                    return@LazyColumn
                }

                // ── Sub-folders ────────────────────────────────────────────────
                val dirs = state.entries.filter { it.isDirectory }
                if (dirs.isNotEmpty()) {
                    item {
                        Text("Dossiers (${dirs.size})",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
                    }
                    items(dirs, key = { it.file.absolutePath }) { entry ->
                        FolderRow(entry, onClick = { vm.navigateTo(entry.file) })
                    }
                }

                // ── Media section header with controls (STICKY) ────────────────
                if (state.mediaInCurrentDir.isNotEmpty()) {
                    stickyHeader {
                        Surface(color = Background.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                // Toolbar: filter chips
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(null to "Tous", "video" to "Vidéos", "image" to "Images", "audio" to "Audio").forEach { (key, lbl) ->
                                        FilterChip(
                                            selected = mediaFilter == key,
                                            onClick  = { mediaFilter = key },
                                            label    = { Text(lbl) },
                                            colors   = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor   = AmberGlow,
                                                selectedLabelColor       = Amber,
                                                containerColor           = GlassWhite,
                                                labelColor               = TextSecondary,
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Médias (${sortedMedia.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    )

                                    // Grid size slider (1–4)
                                    Icon(Icons.Rounded.GridView, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    Slider(
                                        value         = (5 - columns).toFloat(),
                                        onValueChange = { columns = (5 - it.toInt()).coerceIn(1, 4) },
                                        valueRange    = 1f..4f,
                                        steps         = 2,
                                        colors        = SliderDefaults.colors(
                                            thumbColor        = Amber,
                                            activeTrackColor  = Amber,
                                            inactiveTrackColor = AmberDim.copy(0.3f),
                                        ),
                                        modifier = Modifier.width(90.dp),
                                    )
                                    Surface(color = AmberGlow, shape = RoundedCornerShape(5.dp)) {
                                        Text("${columns}×",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Amber),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }

                                    // Sort dropdown
                                    Box {
                                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Rounded.SwapVert, "Trier", tint = Amber, modifier = Modifier.size(20.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            containerColor = SurfaceMid,
                                        ) {
                                            BrowserSort.entries.forEach { s ->
                                                DropdownMenuItem(
                                                    text = { Text(s.label, color = if (sortBy == s) Amber else TextPrimary) },
                                                    trailingIcon = {
                                                        if (sortBy == s) Icon(Icons.Rounded.Check, null, tint = Amber, modifier = Modifier.size(16.dp))
                                                    },
                                                    onClick = { sortBy = s; showSortMenu = false },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Media grid ────────────────────────────────────────────────
                if (sortedMedia.isEmpty() && state.entries.filter { !it.isDirectory }.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                            Text("Aucun média dans ce dossier", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    val rows = sortedMedia.chunked(columns)
                    items(rows) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (media in rowItems) {
                                val isSelected = selectedMedia.contains(media)
                                Box(Modifier.weight(1f)) {
                                    MediaGridCard(
                                        media = media,
                                        columns = columns,
                                        isSelected = isSelected,
                                        inSelectionMode = inSelectionMode,
                                        onClick = {
                                            if (inSelectionMode) {
                                                selectedMedia = if (isSelected) selectedMedia - media else selectedMedia + media
                                            } else {
                                                if (media.isImage) {
                                                    val images = sortedMedia.filter { it.isImage }
                                                    onPlayMedia(images, images.indexOf(media))
                                                } else {
                                                    val videos = sortedMedia.filter { !it.isImage }
                                                    onPlayMedia(videos, videos.indexOf(media))
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectionMode) {
                                                selectedMedia = selectedMedia + media
                                            }
                                        }
                                    )
                                }
                            }
                            // Fill empty slots in the last row
                            repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // Empty dir
                if (state.entries.isEmpty() && !state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                            Text("Dossier vide", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // ── Action Bar for Selection ──────────────────────────────────────────
        AnimatedVisibility(
            visible = inSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp).padding(horizontal = 16.dp)
        ) {
            Surface(
                color = SurfaceMid.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedMedia = emptySet() }) {
                        Icon(Icons.Rounded.Close, "Annuler", tint = TextPrimary)
                    }
                    Text("${selectedMedia.size} sélectionné(s)", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { actionSheetType = "COPY" }) {
                            Icon(Icons.Rounded.ContentCopy, "Copier", tint = Amber)
                        }
                        IconButton(onClick = { actionSheetType = "MOVE" }) {
                            Icon(Icons.Rounded.DriveFileMove, "Déplacer", tint = Amber)
                        }
                        IconButton(onClick = {
                            vm.deleteSelected(selectedMedia.toList())
                            selectedMedia = emptySet()
                        }) {
                            Icon(Icons.Rounded.Delete, "Supprimer", tint = ErrorRed)
                        }
                    }
                }
            }
        }
    }
}

// ─── Media Grid Card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridCard(
    media: MediaFile,
    columns: Int,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accentColor = if (media.isVideo) Amber else TealAccent
    val aspectRatio = when (columns) {
        1    -> 16f / 9f
        2    -> 0.75f
        3    -> 1f
        else -> 1.1f
    }

    val scale by animateFloatAsState(if (isSelected) 0.92f else 1f, label = "scale")
    val alpha by animateFloatAsState(if (inSelectionMode && !isSelected) 0.6f else 1f, label = "alpha")

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
        Surface(
            color = SurfaceMid,
            shape = RoundedCornerShape(if (columns <= 2) 14.dp else 10.dp),
            border = if (isSelected) BorderStroke(2.dp, Amber) else null,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .alpha(alpha)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (media.isVideo || media.isImage) {
                    AsyncImage(
                        model = media.file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Bottom gradient for text readability
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))))
                }

                Icon(
                    if (media.isVideo) Icons.Rounded.PlayCircle else if (media.isImage) Icons.Rounded.Image else Icons.Rounded.MusicNote,
                    null,
                    tint = if (media.isVideo || media.isImage) Color.White.copy(0.85f) else accentColor.copy(0.4f),
                    modifier = Modifier.size(if (columns == 1) 40.dp else 28.dp),
                )

                // Format badge
                Surface(
                    color  = accentColor.copy(0.2f),
                    shape  = RoundedCornerShape(5.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                ) {
                    Text(
                        media.extension.uppercase().take(4),
                        style = MaterialTheme.typography.labelSmall.copy(color = accentColor),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                // Filename at bottom (only for 1–2 col)
                if (columns <= 2) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                            .padding(8.dp),
                    ) {
                        Column {
                            Text(
                                media.name.removeSuffix(".${media.extension}"),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (media.sizeBytes > 0) {
                                Text(
                                    formatFileSize(media.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(0.6f)),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selection Checkbox
        if (inSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Amber else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

// ─── Sub-components (same as before) ─────────────────────────────────────────

@Composable
private fun QuickAccessChip(entry: BrowserEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = GlassWhite,
        shape   = RoundedCornerShape(12.dp),
        border  = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Folder, null, tint = Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(entry.name, style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary))
        }
    }
}

@Composable
private fun FavoriteRow(fav: FolderEntity, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        color    = GlassAmber,
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(1.dp, AmberDim.copy(0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Bookmark, null, tint = Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fav.displayName, style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary))
                Text(
                    "${fav.videoCount} vidéo(s) • ${formatPath(fav.path)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FolderRow(entry: BrowserEntry, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        color    = GlassWhite,
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.thumbnail != null) {
                AsyncImage(
                    model = entry.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceMid),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (entry.mediaCount > 0) Icons.Rounded.VideoLibrary else Icons.Rounded.Folder,
                        null,
                        tint = if (entry.mediaCount > 0) Amber else TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (entry.mediaCount > 0) append("${entry.mediaCount} média(s) • ")
                        append("${entry.childCount} élément(s)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Formatting ───────────────────────────────────────────────────────────────

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}

private fun formatPath(path: String): String {
    val parts = path.split("/")
    return if (parts.size > 3) "…/${parts.takeLast(2).joinToString("/")}" else path
}
