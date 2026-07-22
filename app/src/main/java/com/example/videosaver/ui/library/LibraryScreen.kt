package com.example.videosaver.ui.library

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.videosaver.data.DownloadEntity
import com.example.videosaver.data.DownloadStatus
import com.example.videosaver.theme.*
import com.example.videosaver.ui.components.GlassCard
import com.example.videosaver.ui.download.DownloadViewModel
import java.io.File

// ─── Sort options ─────────────────────────────────────────────────────────────

enum class SortBy(val label: String, val icon: ImageVector) {
    DATE_DESC("Date (récent)", Icons.Rounded.CalendarToday),
    DATE_ASC("Date (ancien)", Icons.Rounded.DateRange),
    NAME_ASC("Nom (A→Z)", Icons.Rounded.SortByAlpha),
    NAME_DESC("Nom (Z→A)", Icons.Rounded.SortByAlpha),
    SIZE_DESC("Taille (grand)", Icons.Rounded.Storage),
    SIZE_ASC("Taille (petit)", Icons.Rounded.Storage),
}

enum class MediaFilter(val label: String, val icon: ImageVector) {
    ALL("Tout", Icons.Rounded.GridView),
    VIDEO("Vidéos", Icons.Rounded.VideoFile),
    AUDIO("Audio", Icons.Rounded.MusicNote),
}

// ─── Main screen ──────────────────────────────────────────────────────────────

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    vm: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory(LocalContext.current)),
    onPlayAudio: (List<com.example.videosaver.data.MediaFile>, Int) -> Unit = { _, _ -> }
) {
    val downloads by vm.downloads.collectAsStateWithLifecycle(emptyList())
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val favorites by vm.favorites.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    // Local UI state for controls
    var mediaFilter  by remember { mutableStateOf(MediaFilter.ALL) }
    var sortBy       by remember { mutableStateOf(SortBy.DATE_DESC) }
    var columns      by remember { mutableStateOf(2) }          // 1–4
    var showSortMenu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var selectedTag  by remember { mutableStateOf<String?>(null) }
    var moveTargetDownload by remember { mutableStateOf<com.example.videosaver.data.DownloadEntity?>(null) }
    var moveMultiple by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val inSelectionMode = selectedIds.isNotEmpty()

    val allTags = remember(completed) {
        completed.flatMap { it.tags }.distinct().sorted()
    }

    // Apply filter + sort
    val filtered = completed
        .filter { dl ->
            val mediaMatch = when (mediaFilter) {
                MediaFilter.ALL   -> true
                MediaFilter.VIDEO -> !dl.isAudioOnly
                MediaFilter.AUDIO -> dl.isAudioOnly
            }
            val tagMatch = selectedTag == null || dl.tags.contains(selectedTag)
            mediaMatch && tagMatch
        }
        .let { list ->
            when (sortBy) {
                SortBy.DATE_DESC  -> list.sortedByDescending { it.completedAt ?: it.createdAt }
                SortBy.DATE_ASC   -> list.sortedBy { it.completedAt ?: it.createdAt }
                SortBy.NAME_ASC   -> list.sortedBy { it.title.lowercase() }
                SortBy.NAME_DESC  -> list.sortedByDescending { it.title.lowercase() }
                SortBy.SIZE_DESC  -> list.sortedByDescending { it.fileSize }
                SortBy.SIZE_ASC   -> list.sortedBy { it.fileSize }
            }
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        if (completed.isEmpty()) {
            EmptyLibrary(Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    top    = 0.dp,
                    start  = 12.dp,
                    end    = 12.dp,
                    bottom = 100.dp,
                ),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Header ──────────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                        if (inSelectionMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedIds = emptySet() }) {
                                    Icon(Icons.Rounded.Close, "Annuler", tint = TextPrimary)
                                }
                                Text("${selectedIds.size} sélectionné(s)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { moveMultiple = true }) {
                                    Icon(Icons.Rounded.DriveFileMove, "Déplacer", tint = Amber)
                                }
                                IconButton(onClick = { vm.deleteDownloads(selectedIds); selectedIds = emptySet() }) {
                                    Icon(Icons.Rounded.Delete, "Supprimer", tint = ErrorRed)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Bibliothèque", style = MaterialTheme.typography.headlineLarge)
                                    Text(
                                        "${filtered.size} / ${completed.size} fichier${if (completed.size > 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                // Toggle controls panel
                                IconButton(onClick = { showControls = !showControls }) {
                                    Icon(
                                        if (showControls) Icons.Rounded.Tune else Icons.Rounded.Tune,
                                        "Affichage",
                                        tint = if (showControls) Amber else TextSecondary,
                                    )
                                }
                            }
                        }

                        // ── Controls panel (collapsible) ───────────────────
                        AnimatedVisibility(visible = showControls) {
                            Column {
                                Spacer(Modifier.height(12.dp))

                                // Media type filter chips
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MediaFilter.entries.forEach { f ->
                                        FilterChip(
                                            selected   = mediaFilter == f,
                                            onClick    = { mediaFilter = f },
                                            label      = { Text(f.label) },
                                            leadingIcon = { Icon(f.icon, null, Modifier.size(14.dp)) },
                                            colors     = filterChipColors(),
                                            shape      = RoundedCornerShape(10.dp),
                                        )
                                    }
                                }

                                if (allTags.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            FilterChip(
                                                selected = selectedTag == null,
                                                onClick = { selectedTag = null },
                                                label = { Text("Tous les tags") },
                                                colors = filterChipColors(),
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                        }
                                        items(allTags) { tag ->
                                            FilterChip(
                                                selected = selectedTag == tag,
                                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                                label = { Text("#$tag") },
                                                colors = filterChipColors(),
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Thumbnail size slider + sort button on same row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Thumbnail size (1–4 columns)
                                    Icon(
                                        Icons.Rounded.GridView,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Slider(
                                        value          = (5 - columns).toFloat(), // invert: right = bigger
                                        onValueChange  = { columns = (5 - it.toInt()).coerceIn(1, 4) },
                                        valueRange     = 1f..4f,
                                        steps          = 2,
                                        colors         = SliderDefaults.colors(
                                            thumbColor       = Amber,
                                            activeTrackColor = Amber,
                                            inactiveTrackColor = AmberDim.copy(0.3f),
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    // Column count badge
                                    Surface(
                                        color = AmberGlow,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "${columns}×",
                                            style = MaterialTheme.typography.labelMedium.copy(color = Amber),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }

                                    Spacer(Modifier.width(4.dp))

                                    // Sort button
                                    Box {
                                        OutlinedButton(
                                            onClick = { showSortMenu = true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            border = BorderStroke(1.dp, AmberDim.copy(0.4f)),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Icon(sortBy.icon, null, tint = Amber, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                sortBy.label.split(" ").first(),
                                                style = MaterialTheme.typography.labelMedium.copy(color = Amber),
                                            )
                                        }
                                        DropdownMenu(
                                            expanded          = showSortMenu,
                                            onDismissRequest  = { showSortMenu = false },
                                            containerColor    = SurfaceMid,
                                        ) {
                                            SortBy.entries.forEach { s ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            s.label,
                                                            color = if (sortBy == s) Amber else TextPrimary,
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(s.icon, null,
                                                            tint = if (sortBy == s) Amber else TextSecondary,
                                                            modifier = Modifier.size(18.dp))
                                                    },
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

                // ── Media grid ───────────────────────────────────────────────
                items(filtered, key = { it.id }) { dl ->
                    AnimatedVisibility(
                        visible      = true,
                        enter        = fadeIn() + scaleIn(initialScale = 0.9f),
                    ) {
                        MediaCard(
                            download = dl,
                            columns  = columns,
                            selected = selectedIds.contains(dl.id),
                            inSelectionMode = inSelectionMode,
                            onToggleSelect = {
                                selectedIds = if (selectedIds.contains(dl.id)) selectedIds - dl.id else selectedIds + dl.id
                            },
                            onLongClick = {
                                if (!inSelectionMode) {
                                    selectedIds = setOf(dl.id)
                                }
                            },
                            onDelete = { vm.deleteDownload(dl.id) },
                            onUpdateTags = { tags -> vm.updateTags(dl.id, tags) },
                            onMove = { moveTargetDownload = dl },
                            onPlay = { 
                                if (dl.isAudioOnly) {
                                    val f = File(dl.filePath)
                                    val media = com.example.videosaver.data.MediaFile(
                                        file = f,
                                        name = dl.title,
                                        sizeBytes = dl.fileSize,
                                        lastModified = dl.completedAt ?: dl.createdAt,
                                        isVideo = false,
                                        isAudio = true,
                                        isImage = false,
                                        extension = f.extension
                                    )
                                    onPlayAudio(listOf(media), 0)
                                } else {
                                    openFile(context, dl)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Move File Sheet ───────────────────────────────────────────────────────
    if (moveTargetDownload != null) {
        com.example.videosaver.ui.components.MoveFileSheet(
            favorites = favorites,
            onSelectFolder = { folder ->
                vm.moveDownload(moveTargetDownload!!.id, folder)
                moveTargetDownload = null
            },
            onDismiss = { moveTargetDownload = null }
        )
    }

    if (moveMultiple) {
        com.example.videosaver.ui.components.MoveFileSheet(
            favorites = favorites,
            onSelectFolder = { folder ->
                vm.moveDownloads(selectedIds, folder)
                selectedIds = emptySet()
                moveMultiple = false
            },
            onDismiss = { moveMultiple = false }
        )
    }
}

// ─── Media Card ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCard(
    download: DownloadEntity,
    columns: Int,
    selected: Boolean,
    inSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onUpdateTags: (List<String>) -> Unit,
    onMove: () -> Unit,
    onPlay: () -> Unit,
) {
    val context     = LocalContext.current
    var showMenu    by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    val accentColor = if (download.isAudioOnly) TealAccent else Amber

    // Aspect ratio adapts: single column = landscape, multi = portrait/square
    val aspectRatio = when (columns) {
        1    -> 16f / 9f
        2    -> 0.75f
        3    -> 1f
        else -> 1.1f
    }

    GlassCard(
        modifier      = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .combinedClickable(
                onClick = { if (inSelectionMode) onToggleSelect() else onPlay() },
                onLongClick = onLongClick
            ),
        cornerRadius  = if (columns <= 2) 16.dp else 12.dp,
        glowColor     = if (selected) Amber.copy(0.4f) else accentColor.copy(0.06f),
        borderStroke  = if (selected) BorderStroke(2.dp, Amber) else BorderStroke(1.dp, GlassBorder)
    ) {
        // Thumbnail / icon area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(if (columns <= 2) 10.dp else 8.dp))
                .background(SurfaceMid),
            contentAlignment = Alignment.Center,
        ) {
            val videoFile = File(download.filePath)
            val imageModel = download.thumbnailUrl ?: if (!download.isAudioOnly && videoFile.exists()) videoFile else null

            if (imageModel != null && !download.isAudioOnly) {
                AsyncImage(
                    model            = imageModel,
                    contentDescription = null,
                    contentScale     = ContentScale.Crop,
                    modifier         = Modifier.fillMaxSize(),
                )
                // Bottom gradient
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Background.copy(0.7f)))
                    )
                )
                // Play icon overlay
                Icon(
                    Icons.Rounded.PlayCircle,
                    null,
                    tint = Color.White.copy(0.85f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (columns == 1) 48.dp else 36.dp),
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    tint = TealAccent.copy(0.5f),
                    modifier = Modifier.size(if (columns == 1) 40.dp else 28.dp),
                )
            }

            // Format badge (top-right)
            Surface(
                color  = accentColor.copy(0.18f),
                shape  = RoundedCornerShape(5.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            ) {
                Text(
                    if (download.isAudioOnly) download.audioFormat.uppercase()
                    else download.quality.take(5),
                    style = MaterialTheme.typography.labelSmall.copy(color = accentColor),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            if (selected) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Amber.copy(0.3f))
                )
                Icon(
                    Icons.Rounded.CheckCircle, 
                    null, 
                    tint = Amber, 
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
            }
        }

        // Only show title + controls when columns <= 3
        if (columns <= 3) {
            Spacer(Modifier.height(6.dp))
            Text(
                download.title,
                style    = when (columns) {
                    1    -> MaterialTheme.typography.bodyLarge.copy(color = TextPrimary)
                    else -> MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                },
                maxLines = if (columns == 1) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (columns <= 2 && download.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                download.tags.take(3).forEach { tag ->
                    Surface(color = SurfaceMid, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (columns <= 2) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play button
                IconButton(
                    onClick = { if (inSelectionMode) onToggleSelect() else onPlay() },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                // More menu
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.MoreVert, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = SurfaceMid,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gérer les tags") },
                            leadingIcon = { Icon(Icons.Rounded.Label, null, tint = TextPrimary) },
                            onClick = { showMenu = false; showTagDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Déplacer") },
                            leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null, tint = Amber) },
                            onClick = { showMenu = false; onMove() }
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = ErrorRed) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = ErrorRed) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }

    if (showTagDialog) {
        TagEditDialog(
            initialTags = download.tags,
            onDismiss = { showTagDialog = false },
            onSave = { newTags ->
                onUpdateTags(newTags)
                showTagDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    initialTags: List<String>,
    allKnownTags: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var tags by remember { mutableStateOf(initialTags) }
    var inputText by remember { mutableStateOf("") }

    val suggestions = remember(inputText, allKnownTags, tags) {
        if (inputText.trim().isEmpty()) emptyList()
        else allKnownTags.filter { it.contains(inputText.trim(), ignoreCase = true) && !tags.contains(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Background,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text("Gérer les tags", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "Tags actuellement attribués (${tags.size}) :",
                    style = MaterialTheme.typography.labelSmall.copy(color = Amber),
                )
                Spacer(Modifier.height(6.dp))
                if (tags.isEmpty()) {
                    Text(
                        "Aucun tag attribué pour l'instant.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags = tags - tag },
                                label = { Text("#$tag") },
                                trailingIcon = { Icon(Icons.Rounded.Close, "Supprimer", Modifier.size(16.dp)) },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = AmberGlow,
                                    selectedLabelColor = Amber,
                                    selectedTrailingIconColor = Amber,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Ajouter un tag") },
                    placeholder = { Text("Tapez un mot-clé...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber,
                        focusedLabelColor = Amber,
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val newTag = inputText.trim().lowercase()
                            if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                                tags = tags + newTag
                                inputText = ""
                            }
                        }) {
                            Icon(Icons.Rounded.Add, "Ajouter", tint = Amber)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Suggestions d'autocomplétion :", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { sug ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (!tags.contains(sug)) tags = tags + sug
                                    inputText = ""
                                },
                                label = { Text("💡 #$sug") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = AmberGlow,
                                    labelColor = Amber,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tags) }) { Text("Enregistrer", color = Amber) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = TextSecondary) }
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun openFile(context: android.content.Context, dl: DownloadEntity) {
    val file = File(dl.filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val mime = if (dl.isAudioOnly) "audio/*" else "video/*"
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = AmberDim, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Bibliothèque vide", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Vos vidéos et fichiers audio téléchargés apparaîtront ici",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor  = AmberGlow,
    selectedLabelColor      = Amber,
    selectedLeadingIconColor = Amber,
    containerColor          = GlassWhite,
    labelColor              = TextSecondary,
    iconColor               = TextSecondary,
)
