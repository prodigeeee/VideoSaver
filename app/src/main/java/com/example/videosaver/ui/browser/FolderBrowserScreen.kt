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
    TAGS_ASC("Tags A→Z"),
    TAGS_DESC("Tags Z→A"),
    TAGGED_FIRST("Taggés en premier"),
    UNTAGGED_FIRST("Non taggés en premier"),
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

    var tagSearchQuery by remember { mutableStateOf("") }
    var isFilterToolbarExpanded by remember { mutableStateOf(true) }

    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (!state.isLoading && state.entries.isNotEmpty()) {
            vm.saveScrollPosition(
                state.currentPath.absolutePath,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
        val isScrollingDown = when {
            listState.firstVisibleItemIndex > lastScrollIndex -> true
            listState.firstVisibleItemIndex < lastScrollIndex -> false
            else -> listState.firstVisibleItemScrollOffset > lastScrollOffset + 25
        }
        if (listState.isScrollInProgress && isScrollingDown && listState.firstVisibleItemIndex > 0) {
            isFilterToolbarExpanded = false
        }
        lastScrollIndex = listState.firstVisibleItemIndex
        lastScrollOffset = listState.firstVisibleItemScrollOffset
    }

    // Multi-selection
    var selectedMedia by remember { mutableStateOf(emptySet<MediaFile>()) }
    val inSelectionMode = selectedMedia.isNotEmpty()
    var actionSheetType by remember { mutableStateOf<String?>(null) } // "COPY" or "MOVE"
    var tagDialogMedia by remember { mutableStateOf<MediaFile?>(null) }
    var showMultiTagDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentPath) {
        selectedMedia = emptySet()
        tagFilters = emptySet()
        tagSearchQuery = ""
    }

    if (actionSheetType != null) {
        com.example.videosaver.ui.components.MoveFileSheet(
            favorites = favorites,
            onSelectFolder = { targetFolder ->
                if (actionSheetType == "MOVE") {
                    vm.moveSelected(selectedMedia.toList(), targetFolder)
                } else {
                    vm.copySelected(selectedMedia.toList(), targetFolder)
                }
                selectedMedia = emptySet()
                actionSheetType = null
            },
            onDismiss = { actionSheetType = null }
        )
    }

    val allKnownTags = remember(state.mediaInCurrentDir, state.allKnownTags) {
        (state.mediaInCurrentDir.flatMap { it.tags } + state.allKnownTags).distinct().filter { it.isNotBlank() }.sorted()
    }

    val availableTags = remember(allKnownTags) {
        listOf("UNTAGGED") + allKnownTags
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
        val totalCount = selectedMedia.size
        val tagCounts = remember(selectedMedia) {
            val map = mutableMapOf<String, Int>()
            selectedMedia.flatMap { it.tags }.forEach { tag ->
                if (tag.isNotBlank()) {
                    map[tag] = (map[tag] ?: 0) + 1
                }
            }
            map
        }
        val commonTags = remember(tagCounts, totalCount) {
            tagCounts.filter { it.value == totalCount }.keys.toList().sorted()
        }
        val partialTags = remember(tagCounts, totalCount) {
            tagCounts.filter { it.value in 1 until totalCount }
        }

        com.example.videosaver.ui.library.TagEditDialog(
            initialTags = commonTags,
            allKnownTags = allKnownTags,
            totalSelectedCount = totalCount,
            initialPartialTags = partialTags,
            onDismiss = { showMultiTagDialog = false },
            onSave = { newCommonTags ->
                vm.updateTagsForMultiple(selectedMedia.toList(), newCommonTags)
                showMultiTagDialog = false
                selectedMedia = emptySet()
            }
        )
    }

    // Sorted + filtered media in current dir
    val sortedMedia = remember(state.mediaInCurrentDir, sortBy, mediaFilter, sizeFilter, dimensionFilter, tagFilters, tagSearchQuery) {
        val cleanQ = tagSearchQuery.trim().lowercase().removePrefix("#").trim()
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
                val queryMatch = if (cleanQ.isEmpty()) true else {
                    f.tags.any { it.lowercase().contains(cleanQ) } || f.name.lowercase().contains(cleanQ)
                }

                mediaMatch && sizeMatch && dimMatch && tagMatch && queryMatch
            }
            .let { list ->
                when (sortBy) {
                    BrowserSort.NAME_ASC       -> list.sortedBy { it.name.lowercase() }
                    BrowserSort.NAME_DESC      -> list.sortedByDescending { it.name.lowercase() }
                    BrowserSort.DATE_DESC      -> list.sortedByDescending { it.lastModified }
                    BrowserSort.DATE_ASC       -> list.sortedBy { it.lastModified }
                    BrowserSort.SIZE_DESC      -> list.sortedByDescending { it.sizeBytes }
                    BrowserSort.SIZE_ASC       -> list.sortedBy { it.sizeBytes }
                    BrowserSort.RES_DESC       -> list.sortedByDescending { it.videoWidth * it.videoHeight }
                    BrowserSort.RES_ASC        -> list.sortedBy { it.videoWidth * it.videoHeight }
                    BrowserSort.TAGS_ASC       -> list.sortedWith(compareBy<MediaFile> { it.tags.isEmpty() }.thenBy { it.tags.joinToString(",") }.thenBy { it.name.lowercase() })
                    BrowserSort.TAGS_DESC      -> list.sortedWith(compareBy<MediaFile> { it.tags.isEmpty() }.thenByDescending { it.tags.joinToString(",") }.thenBy { it.name.lowercase() })
                    BrowserSort.TAGGED_FIRST   -> list.sortedWith(compareBy<MediaFile> { it.tags.isEmpty() }.thenBy { it.name.lowercase() })
                    BrowserSort.UNTAGGED_FIRST -> list.sortedWith(compareBy<MediaFile> { !it.tags.isEmpty() }.thenBy { it.name.lowercase() })
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
                            TagFilterToolbar(
                                tagSearchQuery = tagSearchQuery,
                                onQueryChange = { tagSearchQuery = it },
                                availableTags = availableTags,
                                selectedTags = tagFilters,
                                onToggleTag = { tag ->
                                    tagFilters = if (tagFilters.contains(tag)) tagFilters - tag else tagFilters + tag
                                },
                                isExpanded = isFilterToolbarExpanded,
                                onToggleExpand = { isFilterToolbarExpanded = !isFilterToolbarExpanded },
                                mediaFilter = mediaFilter,
                                onMediaFilterChange = { mediaFilter = it },
                                sizeFilter = sizeFilter,
                                onSizeFilterChange = { sizeFilter = it },
                                dimensionFilter = dimensionFilter,
                                onDimensionFilterChange = { dimensionFilter = it },
                                sortBy = sortBy,
                                onSortByChange = { sortBy = it },
                                sortedMediaCount = sortedMedia.size,
                                columns = columns,
                                onColumnsChange = { columns = it },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
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
                            TagFilterToolbar(
                                tagSearchQuery = tagSearchQuery,
                                onQueryChange = { tagSearchQuery = it },
                                availableTags = availableTags,
                                selectedTags = tagFilters,
                                onToggleTag = { tag ->
                                    tagFilters = if (tagFilters.contains(tag)) tagFilters - tag else tagFilters + tag
                                },
                                isExpanded = isFilterToolbarExpanded,
                                onToggleExpand = { isFilterToolbarExpanded = !isFilterToolbarExpanded },
                                mediaFilter = mediaFilter,
                                onMediaFilterChange = { mediaFilter = it },
                                sizeFilter = sizeFilter,
                                onSizeFilterChange = { sizeFilter = it },
                                dimensionFilter = dimensionFilter,
                                onDimensionFilterChange = { dimensionFilter = it },
                                sortBy = sortBy,
                                onSortByChange = { sortBy = it },
                                sortedMediaCount = sortedMedia.size,
                                columns = columns,
                                onColumnsChange = { columns = it },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
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

                // Format badge (top-end)
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
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

        // Tag edit button overlay (placed directly on root Box so touches NEVER trigger parent video playback)
        if (onEditTags != null && !inSelectionMode) {
            Surface(
                onClick = onEditTags,
                color = Color.Black.copy(0.75f),
                shape = CircleShape,
                border = BorderStroke(1.dp, AmberGlow),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 48.dp)
                    .size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tag, "Gérer les tags", tint = Amber, modifier = Modifier.size(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterToolbar(
    tagSearchQuery: String,
    onQueryChange: (String) -> Unit,
    availableTags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    mediaFilter: String?,
    onMediaFilterChange: (String?) -> Unit,
    sizeFilter: String?,
    onSizeFilterChange: (String?) -> Unit,
    dimensionFilter: String?,
    onDimensionFilterChange: (String?) -> Unit,
    sortBy: BrowserSort,
    onSortByChange: (BrowserSort) -> Unit,
    sortedMediaCount: Int,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedTags = remember(availableTags, selectedTags) {
        availableTags.sortedWith(
            compareByDescending<String> { selectedTags.contains(it) }
                .thenBy { if (it == "UNTAGGED") " " else it.lowercase() }
        )
    }

    val tagSuggestions = remember(tagSearchQuery, availableTags, selectedTags) {
        val clean = tagSearchQuery.trim().lowercase().removePrefix("#").trim()
        if (clean.length >= 2) {
            availableTags.filter { tag ->
                tag != "UNTAGGED" && tag.lowercase().contains(clean) && !selectedTags.contains(tag)
            }
        } else emptyList()
    }

    Surface(
        color = Background.copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header Row: Search field or collapsed summary pill + Expand button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isExpanded) {
                    OutlinedTextField(
                        value = tagSearchQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Recherche tag (≥ 2 lettres)...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Rounded.Tag, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (tagSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, "Effacer", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassWhite,
                            unfocusedContainerColor = GlassWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                    )
                } else {
                    // Collapsed Summary Bar
                    Surface(
                        onClick = onToggleExpand,
                        color = GlassWhite,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AmberGlow),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.FilterList, null, tint = Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            val filterSummary = buildList {
                                if (selectedTags.isNotEmpty()) add("${selectedTags.size} tag(s)")
                                if (mediaFilter != null) add(mediaFilter)
                                if (sizeFilter != null) add(sizeFilter)
                                if (dimensionFilter != null) add(dimensionFilter)
                                add(sortBy.label)
                            }.joinToString(" • ")
                            Text(
                                filterSummary,
                                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Expand/Collapse Toggle Button
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Réduire" else "Déplier",
                        tint = Amber,
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    // Autocomplete suggestions (shown when query length >= 2)
                    if (tagSearchQuery.trim().length >= 2) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Suggestions :",
                            style = MaterialTheme.typography.labelSmall.copy(color = Amber),
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                        if (tagSuggestions.isEmpty()) {
                            Text(
                                "Aucun tag correspondant",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextDisabled),
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 6.dp),
                            ) {
                                items(tagSuggestions) { tag ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            onToggleTag(tag)
                                            onQueryChange("")
                                        },
                                        label = { Text("💡 #$tag", style = MaterialTheme.typography.labelSmall) },
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

                    Spacer(Modifier.height(6.dp))

                    // Row of active filter chips (Active Selected Tags FIRST, then Type + Size + Dimensions + Unselected Tags)
                    LazyRow(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 1. ACTIVE SELECTED TAGS FIRST (Position 0!)
                        val activeTagsList = selectedTags.toList()
                        if (activeTagsList.isNotEmpty()) {
                            items(activeTagsList) { tag ->
                                FilterChip(
                                    selected = true,
                                    onClick = { onToggleTag(tag) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (tag == "UNTAGGED") "🚫 Sans tag" else "#$tag", style = MaterialTheme.typography.labelSmall)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Rounded.Close, "Retirer", modifier = Modifier.size(12.dp))
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Amber,
                                        selectedLabelColor = Color.Black,
                                        selectedTrailingIconColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                        }

                        // 2. Types
                        items(listOf(null to "Tous", "video" to "Vidéos", "image" to "Images", "audio" to "Audio")) { (key, lbl) ->
                            FilterChip(
                                selected = mediaFilter == key,
                                onClick = { onMediaFilterChange(key) },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGlow,
                                    selectedLabelColor = Amber,
                                    containerColor = GlassWhite,
                                    labelColor = TextSecondary,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }

                        // 3. Size
                        items(listOf("<100M" to "<100M", "100M-1G" to "100M-1G", ">1G" to ">1G")) { (key, lbl) ->
                            FilterChip(
                                selected = sizeFilter == key,
                                onClick = { onSizeFilterChange(if (sizeFilter == key) null else key) },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGlow,
                                    selectedLabelColor = Amber,
                                    containerColor = GlassWhite,
                                    labelColor = TextSecondary,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }

                        // 4. Dimension
                        items(listOf("PORTRAIT" to "📱 Portrait", "LANDSCAPE" to "🖼️ Paysage", "HD" to "HD", "4K" to "4K")) { (key, lbl) ->
                            FilterChip(
                                selected = dimensionFilter == key,
                                onClick = { onDimensionFilterChange(if (dimensionFilter == key) null else key) },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGlow,
                                    selectedLabelColor = Amber,
                                    containerColor = GlassWhite,
                                    labelColor = TextSecondary,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }

                        // 5. Remaining Unselected Tags
                        val unselectedTags = availableTags.filter { !selectedTags.contains(it) }
                        items(unselectedTags) { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { onToggleTag(tag) },
                                label = { Text(if (tag == "UNTAGGED") "🚫 Sans tag" else "#$tag", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = GlassWhite,
                                    labelColor = TextSecondary,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Bottom Controls Row: Count + Sort Menu + Grid slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "$sortedMediaCount média(s)",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )

                        // Sort Menu Dropdown
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                onClick = { showSortMenu = true },
                                color = GlassWhite,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GlassBorder),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Rounded.Sort, null, tint = Amber, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(sortBy.label, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(SurfaceDark),
                            ) {
                                BrowserSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, style = MaterialTheme.typography.bodySmall, color = if (sortBy == option) Amber else TextPrimary) },
                                        onClick = {
                                            onSortByChange(option)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (sortBy == option) {
                                                Icon(Icons.Rounded.Check, null, tint = Amber, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = TextPrimary,
                                            leadingIconColor = Amber,
                                            trailingIconColor = Amber,
                                        )
                                    )
                                }
                            }
                        }

                        // Grid Columns Slider
                        Icon(Icons.Rounded.GridView, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Slider(
                            value = (5 - columns).toFloat(),
                            onValueChange = { onColumnsChange((5 - it.toInt()).coerceIn(1, 4)) },
                            valueRange = 1f..4f,
                            steps = 2,
                            colors = SliderDefaults.colors(
                                thumbColor = Amber,
                                activeTrackColor = Amber,
                                inactiveTrackColor = AmberDim.copy(0.3f),
                            ),
                            modifier = Modifier.width(64.dp),
                        )
                        Surface(color = AmberGlow, shape = RoundedCornerShape(5.dp)) {
                            Text("${columns}×",
                                style = MaterialTheme.typography.labelSmall.copy(color = Amber),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
