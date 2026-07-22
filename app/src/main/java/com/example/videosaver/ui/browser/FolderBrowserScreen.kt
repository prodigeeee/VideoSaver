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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    RES_DESC("Résolution ↓"),
    RES_ASC("Résolution ↑"),
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
    var columns         by remember(state.folderPrefs) { mutableIntStateOf(state.folderPrefs.columns) }
    var sortBy          by remember(state.folderPrefs) { mutableStateOf(
        runCatching { BrowserSort.valueOf(state.folderPrefs.sortBy) }.getOrDefault(BrowserSort.NAME_ASC)
    ) }
    var showSortMenu    by remember { mutableStateOf(false) }
    var mediaFilter     by remember(state.folderPrefs) { mutableStateOf<String?>(state.folderPrefs.mediaFilter) }
    var sizeFilter      by remember(state.folderPrefs) { mutableStateOf<String?>(state.folderPrefs.sizeFilter) }
    var dimensionFilter by remember(state.folderPrefs) { mutableStateOf<String?>(state.folderPrefs.dimensionFilter) }
    var tagFilters      by remember(state.folderPrefs) {
        mutableStateOf(
            state.folderPrefs.tagFilter?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }

    // Commit changes to VM when they change
    LaunchedEffect(columns, sortBy, mediaFilter, sizeFilter, dimensionFilter, tagFilters) {
        val tagParam = if (tagFilters.isEmpty()) null else tagFilters.joinToString(",")
        vm.updateFolderPrefs(columns, sortBy.name, mediaFilter, sizeFilter, dimensionFilter, tagParam)
    }

    // Scroll state persistence per path
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentPath) {
        val pos = state.scrollPositions[state.currentPath.absolutePath]
        if (pos != null) {
            listState.scrollToItem(pos.first, pos.second)
        } else {
            listState.scrollToItem(0, 0)
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (!state.isLoading && state.entries.isNotEmpty()) {
            vm.saveScrollPosition(
                state.currentPath.absolutePath,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    // Multi-selection
    var selectedMedia by remember { mutableStateOf(emptySet<MediaFile>()) }
    val inSelectionMode = selectedMedia.isNotEmpty()
    var actionSheetType by remember { mutableStateOf<String?>(null) } // "COPY" or "MOVE"
    var tagDialogMedia by remember { mutableStateOf<MediaFile?>(null) }
    var showMultiTagDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentPath) {
        selectedMedia = emptySet()
    }

    val allKnownTags = remember(state.mediaInCurrentDir) {
        state.mediaInCurrentDir.flatMap { it.tags }.distinct().sorted()
    }

    if (tagDialogMedia != null) {
        com.example.videosaver.ui.library.TagEditDialog(
            initialTags = tagDialogMedia!!.tags,
            allKnownTags = allKnownTags,
            onDismiss = { tagDialogMedia = null },
            onSave = { newTags ->
                vm.updateFileTags(tagDialogMedia!!, newTags)
                tagDialogMedia = null
            }
        )
    }

    if (showMultiTagDialog && selectedMedia.isNotEmpty()) {
        val initialTags = remember(selectedMedia) {
            selectedMedia.flatMap { it.tags }.distinct().sorted()
        }
        com.example.videosaver.ui.library.TagEditDialog(
            initialTags = initialTags,
            allKnownTags = allKnownTags,
            onDismiss = { showMultiTagDialog = false },
            onSave = { newTags ->
                vm.updateTagsForMultiple(selectedMedia.toList(), newTags)
                showMultiTagDialog = false
                selectedMedia = emptySet()
            }
        )
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

    val availableTags = remember(state.mediaInCurrentDir) {
        listOf("UNTAGGED") + state.mediaInCurrentDir.flatMap { it.tags }.distinct().sorted()
    }

    // Sorted + filtered media in current dir
    val sortedMedia = remember(state.mediaInCurrentDir, sortBy, mediaFilter, sizeFilter, dimensionFilter, tagFilters) {
        state.mediaInCurrentDir
            .filter { f ->
                val mediaMatch = when (mediaFilter) {
                    "video" -> f.isVideo
                    "audio" -> f.isAudio
                    "image" -> f.isImage
                    else    -> true
                }
                val sizeMatch = when (sizeFilter) {
                    "<100M"   -> f.sizeBytes < 100 * 1024 * 1024
                    "100M-1G" -> f.sizeBytes in (100 * 1024 * 1024)..(1024 * 1024 * 1024)
                    ">1G"     -> f.sizeBytes > 1024 * 1024 * 1024
                    else      -> true
                }
                val dimMatch = when (dimensionFilter) {
                    "PORTRAIT"  -> f.videoHeight > f.videoWidth && f.videoHeight > 0
                    "LANDSCAPE" -> f.videoWidth >= f.videoHeight && f.videoWidth > 0
                    "HD"        -> maxOf(f.videoWidth, f.videoHeight) >= 1280 || minOf(f.videoWidth, f.videoHeight) >= 720
                    "4K"        -> maxOf(f.videoWidth, f.videoHeight) >= 3840 || minOf(f.videoWidth, f.videoHeight) >= 2160
                    else        -> true
                }
                val tagMatch = if (tagFilters.isEmpty()) true else {
                    val wantsUntagged = tagFilters.contains("UNTAGGED")
                    val selectedNormalTags = tagFilters.filter { it != "UNTAGGED" }
                    val untaggedOk = wantsUntagged && f.tags.isEmpty()
                    val normalOk = selectedNormalTags.isNotEmpty() && selectedNormalTags.all { f.tags.contains(it) }
                    if (wantsUntagged && selectedNormalTags.isNotEmpty()) untaggedOk || normalOk
                    else if (wantsUntagged) untaggedOk
                    else normalOk
                }

                mediaMatch && sizeMatch && dimMatch && tagMatch
            }
            .let { list ->
                when (sortBy) {
                    BrowserSort.NAME_ASC  -> list.sortedBy { it.name.lowercase() }
                    BrowserSort.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                    BrowserSort.DATE_DESC -> list.sortedByDescending { it.lastModified }
                    BrowserSort.DATE_ASC  -> list.sortedBy { it.lastModified }
                    BrowserSort.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                    BrowserSort.SIZE_ASC  -> list.sortedBy { it.sizeBytes }
                    BrowserSort.RES_DESC  -> list.sortedByDescending { it.videoWidth * it.videoHeight }
                    BrowserSort.RES_ASC   -> list.sortedBy { it.videoWidth * it.videoHeight }
                }
            }
    }

    // ── Shared media click handler (portrait + landscape) ────────────────────
    val onMediaClick = { media: MediaFile ->
        if (inSelectionMode) {
            selectedMedia = if (selectedMedia.contains(media)) selectedMedia - media else selectedMedia + media
        } else {
            if (media.isImage) {
                val images = sortedMedia.filter { it.isImage }
                onPlayMedia(images, images.indexOf(media))
            } else {
                val videos = sortedMedia.filter { !it.isImage }
                onPlayMedia(videos, videos.indexOf(media))
            }
        }
    }

    // ── Orientation-adaptive layout ───────────────────────────────────────────
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // ════════════════════════════════════════════════════════════════════
        // PAYSAGE : deux panneaux côte-à-côte
        //   Gauche  220dp  : navigation (retour, breadcrumb, dossiers)
        //   Droite  rest   : grille média + barre compacte sur 1 ligne
        // ════════════════════════════════════════════════════════════════════
        Row(modifier = modifier.fillMaxSize().background(Background)) {

            // ── Panneau gauche : navigation ──────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(SurfaceDark.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                // En-tête compact (1 ligne)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(SurfaceDark, Background)))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.breadcrumb.size > 1) {
                            IconButton(onClick = vm::navigateUp, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ArrowBackIosNew, "Retour", tint = Amber, modifier = Modifier.size(15.dp))
                            }
                        }
                        Text(
                            state.currentPath.name.ifBlank { "Stockage" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = vm::toggleFavorite, modifier = Modifier.size(30.dp)) {
                            Icon(
                                if (state.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                "Favori",
                                tint = if (state.isFavorite) Amber else TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Breadcrumb (scroll horizontal)
                if (state.breadcrumb.size > 1) {
                    item {
                        LazyRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.breadcrumb) { crumb ->
                                val isLast = crumb == state.breadcrumb.last()
                                Text(
                                    crumb.name.take(12),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isLast) Amber else TextSecondary,
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { if (!isLast) vm.navigateTo(crumb) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                                if (!isLast) {
                                    Text("›", style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled))
                                }
                            }
                        }
                    }
                }

                // Emplacements rapides
                if (state.breadcrumb.size <= 1 && vm.rootDirectories.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            Spacer(Modifier.height(4.dp))
                            Text("Emplacements", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(vm.rootDirectories) { root ->
                                    QuickAccessChip(root, onClick = { vm.navigateTo(root.file) })
                                }
                            }
                        }
                    }
                }

                // Favoris
                if (state.breadcrumb.size <= 1 && favorites.isNotEmpty()) {
                    item {
                        Text("Favoris", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp))
                    }
                    items(favorites) { fav ->
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                            FavoriteRow(fav, onClick = { vm.openFavorite(fav) })
                        }
                    }
                    item {
                        HorizontalDivider(color = GlassBorder,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                // Chargement
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                        }
                    }
                    return@LazyColumn
                }

                // Dossiers
                val dirs = state.entries.filter { it.isDirectory }
                if (dirs.isNotEmpty()) {
                    item {
                        Text("Dossiers (${dirs.size})", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 4.dp))
                    }
                    items(dirs, key = { it.file.absolutePath }) { entry ->
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                            FolderRow(entry, onClick = { vm.navigateTo(entry.file) })
                        }
                    }
                }

                // Dossier vide
                if (state.entries.isEmpty() && !state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                            Text("Dossier vide", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Séparateur vertical
            VerticalDivider(color = GlassBorder, thickness = 1.dp)

            // ── Panneau droit : grille média ─────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Barre compacte UNIQUE (filtres + grille + tri sur une seule ligne)
                    if (state.mediaInCurrentDir.isNotEmpty()) {
                        stickyHeader {
                            Surface(color = Background.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    LazyRow(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        // Types
                                        items(listOf(null to "Tous", "video" to "Vidéos", "image" to "Imgs", "audio" to "♪")) { (key, lbl) ->
                                            FilterChip(
                                                selected = mediaFilter == key,
                                                onClick = { mediaFilter = key },
                                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AmberGlow,
                                                    selectedLabelColor     = Amber,
                                                    containerColor         = GlassWhite,
                                                    labelColor             = TextSecondary,
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                            )
                                        }

                                        // Size
                                        items(listOf("<100M" to "<100M", "100M-1G" to "100M-1G", ">1G" to ">1G")) { (key, lbl) ->
                                            FilterChip(
                                                selected = sizeFilter == key,
                                                onClick = { sizeFilter = if (sizeFilter == key) null else key },
                                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AmberGlow,
                                                    selectedLabelColor     = Amber,
                                                    containerColor         = GlassWhite,
                                                    labelColor             = TextSecondary,
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                            )
                                        }

                                        // Dimension / Orientation
                                        items(listOf("PORTRAIT" to "📱", "LANDSCAPE" to "🖼️", "HD" to "HD", "4K" to "4K")) { (key, lbl) ->
                                            FilterChip(
                                                selected = dimensionFilter == key,
                                                onClick = { dimensionFilter = if (dimensionFilter == key) null else key },
                                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AmberGlow,
                                                    selectedLabelColor     = Amber,
                                                    containerColor         = GlassWhite,
                                                    labelColor             = TextSecondary,
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                            )
                                        }

                                        // Tags
                                        items(availableTags) { tag ->
                                            val isSel = tagFilters.contains(tag)
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    tagFilters = if (isSel) tagFilters - tag else tagFilters + tag
                                                },
                                                label = { Text(if (tag == "UNTAGGED") "🚫 Sans tag" else "#$tag", style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AmberGlow,
                                                    selectedLabelColor     = Amber,
                                                    containerColor         = GlassWhite,
                                                    labelColor             = TextSecondary,
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            "${sortedMedia.size} média(s)",
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                                        )
                                        // Grille
                                        Icon(Icons.Rounded.GridView, null, tint = TextSecondary,
                                            modifier = Modifier.size(12.dp))
                                        Slider(
                                            value         = (5 - columns).toFloat(),
                                            onValueChange = { columns = (5 - it.toInt()).coerceIn(1, 4) },
                                            valueRange    = 1f..4f,
                                            steps         = 2,
                                            colors        = SliderDefaults.colors(
                                                thumbColor         = Amber,
                                                activeTrackColor   = Amber,
                                                inactiveTrackColor = AmberDim.copy(0.3f),
                                            ),
                                            modifier = Modifier.width(72.dp),
                                        )
                                        Surface(color = AmberGlow, shape = RoundedCornerShape(5.dp)) {
                                            Text("${columns}×",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Amber),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                        // Tri
                                        Box {
                                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Rounded.SwapVert, "Trier", tint = Amber, modifier = Modifier.size(16.dp))
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

                    // Chargement
                    if (state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                                CircularProgressIndicator(color = Amber)
                            }
                        }
                        return@LazyColumn
                    }

                    // Grille médias
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
                                            media           = media,
                                            columns         = columns,
                                            isSelected      = isSelected,
                                            inSelectionMode = inSelectionMode,
                                            onClick         = { onMediaClick(media) },
                                            onLongClick     = { if (!inSelectionMode) selectedMedia = selectedMedia + media },
                                            onEditTags      = { tagDialogMedia = media },
                                        )
                                    }
                                }
                                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // Barre de sélection (paysage) — enveloppée dans un Box positionneur
                // pour éviter la résolution de RowScope.AnimatedVisibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = inSelectionMode,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                    Surface(
                        color  = SurfaceMid.copy(alpha = 0.95f),
                        shape  = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(onClick = { selectedMedia = emptySet() }) {
                                Icon(Icons.Rounded.Close, "Annuler", tint = TextPrimary)
                            }
                            Text("${selectedMedia.size} sélectionné(s)", style = MaterialTheme.typography.labelLarge)
                            Row {
                                IconButton(onClick = { if (selectedMedia.isNotEmpty()) showMultiTagDialog = true }) {
                                    Icon(Icons.Rounded.Tag, "Tags", tint = Amber)
                                }
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
        }
        // ── fin layout paysage ───────────────────────────────────────────────

    } else {
        // ════════════════════════════════════════════════════════════════════
        // PORTRAIT : layout existant avec listState et filtres étendus
        // ════════════════════════════════════════════════════════════════════
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
                    state = listState,
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
                                    // Toolbar: filter chips (Type + Size + Dimensions + Tags)
                                    LazyRow(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        items(listOf(null to "Tous", "video" to "Vidéos", "image" to "Images", "audio" to "Audio")) { (key, lbl) ->
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

                                        items(listOf("<100M" to "< 100 Mo", "100M-1G" to "100Mo-1Go", ">1G" to "> 1 Go")) { (key, lbl) ->
                                            FilterChip(
                                                selected = sizeFilter == key,
                                                onClick  = { sizeFilter = if (sizeFilter == key) null else key },
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

                                        items(listOf("PORTRAIT" to "📱 Portrait", "LANDSCAPE" to "🖼️ Paysage", "HD" to "HD 720p+", "4K" to "4K 2160p")) { (key, lbl) ->
                                            FilterChip(
                                                selected = dimensionFilter == key,
                                                onClick  = { dimensionFilter = if (dimensionFilter == key) null else key },
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

                                        items(availableTags) { tag ->
                                            val isSel = tagFilters.contains(tag)
                                            FilterChip(
                                                selected = isSel,
                                                onClick  = {
                                                    tagFilters = if (isSel) tagFilters - tag else tagFilters + tag
                                                },
                                                label    = { Text(if (tag == "UNTAGGED") "🚫 Sans tag" else "#$tag") },
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
                                            media           = media,
                                            columns         = columns,
                                            isSelected      = isSelected,
                                            inSelectionMode = inSelectionMode,
                                            onClick         = { onMediaClick(media) },
                                            onLongClick     = {
                                                if (!inSelectionMode) selectedMedia = selectedMedia + media
                                            },
                                            onEditTags      = { tagDialogMedia = media },
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
                            IconButton(onClick = { if (selectedMedia.isNotEmpty()) showMultiTagDialog = true }) {
                                Icon(Icons.Rounded.Tag, "Tags", tint = Amber)
                            }
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
    } // end orientation branch
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
    onEditTags: (() -> Unit)? = null,
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

                // Resolution badge (top-start)
                if (media.isVideo && media.videoWidth > 0 && media.videoHeight > 0) {
                    val resStr = when {
                        maxOf(media.videoWidth, media.videoHeight) >= 3840 || minOf(media.videoWidth, media.videoHeight) >= 2160 -> "4K"
                        maxOf(media.videoWidth, media.videoHeight) >= 2560 || minOf(media.videoWidth, media.videoHeight) >= 1440 -> "1440p"
                        maxOf(media.videoWidth, media.videoHeight) >= 1920 || minOf(media.videoWidth, media.videoHeight) >= 1080 -> "1080p"
                        maxOf(media.videoWidth, media.videoHeight) >= 1280 || minOf(media.videoWidth, media.videoHeight) >= 720  -> "720p"
                        else -> "${minOf(media.videoWidth, media.videoHeight)}p"
                    }
                    Surface(
                        color  = Color.Black.copy(0.6f),
                        shape  = RoundedCornerShape(5.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                    ) {
                        Text(
                            resStr,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                // Format badge & tag edit button (top-end)
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (onEditTags != null && !inSelectionMode) {
                        Surface(
                            color = Color.Black.copy(0.5f),
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp).clickable { onEditTags() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Tag, "Gérer les tags", tint = Amber, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Surface(
                        color  = accentColor.copy(0.2f),
                        shape  = RoundedCornerShape(5.dp),
                    ) {
                        Text(
                            media.extension.uppercase().take(4),
                            style = MaterialTheme.typography.labelSmall.copy(color = accentColor),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (media.sizeBytes > 0) {
                                    Text(
                                        formatFileSize(media.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(0.6f)),
                                    )
                                }
                                if (media.tags.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        media.tags.take(2).joinToString(" ") { "#$it" },
                                        style = MaterialTheme.typography.labelSmall.copy(color = Amber, fontSize = 9.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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
