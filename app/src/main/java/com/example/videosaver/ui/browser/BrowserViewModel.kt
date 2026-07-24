package com.example.videosaver.ui.browser

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videosaver.data.BrowserEntry
import com.example.videosaver.data.BrowserRepository
import com.example.videosaver.data.FolderEntity
import com.example.videosaver.data.FolderPrefs
import com.example.videosaver.data.MediaFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class BrowserUiState(
    val currentPath: File = Environment.getExternalStorageDirectory(),
    val entries: List<BrowserEntry> = emptyList(),
    val breadcrumb: List<File> = emptyList(),
    val isLoading: Boolean = false,
    val isFavorite: Boolean = false,
    val mediaInCurrentDir: List<MediaFile> = emptyList(),
    val allKnownTags: List<String> = emptyList(),
    val folderPrefs: FolderPrefs = FolderPrefs(),
    val scrollPositions: Map<String, Pair<Int, Int>> = emptyMap(),
)

class BrowserViewModel(
    private val repo: BrowserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState(isLoading = true))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    val favorites: StateFlow<List<FolderEntity>> = repo.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rootDirectories = repo.getRootDirectories()

    init {
        // Start at root storage directory (/storage/emulated/0) so all folders and favorites are displayed by default
        val startDir = Environment.getExternalStorageDirectory()
        navigateTo(startDir)
    }

    fun saveScrollPosition(path: String, index: Int, offset: Int) {
        _state.update { st ->
            val updatedMap = st.scrollPositions + (path to (index to offset))
            st.copy(scrollPositions = updatedMap)
        }
    }

    fun navigateTo(dir: File) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val entries = repo.listDirectory(dir)
            val isFav = repo.isFavorite(dir.absolutePath)
            val media = repo.scanMediaFiles(dir)
            val allKnown = repo.getAllKnownTags()
            val crumb = buildBreadcrumb(dir)
            val prefs = repo.getFolderPrefs(dir)
            _state.update {
                it.copy(
                    currentPath       = dir,
                    entries           = entries,
                    breadcrumb        = crumb,
                    isLoading         = false,
                    isFavorite        = isFav,
                    mediaInCurrentDir = media,
                    allKnownTags      = allKnown,
                    folderPrefs       = prefs,
                )
            }
        }
    }

    fun refreshCurrentDir() {
        val current = _state.value.currentPath
        if (current.exists()) {
            navigateTo(current)
        }
    }

    fun navigateUp() {
        val parent = _state.value.currentPath.parentFile ?: return
        navigateTo(parent)
    }

    fun toggleFavorite() {
        val st = _state.value
        viewModelScope.launch {
            if (st.isFavorite) {
                repo.removeFavorite(st.currentPath.absolutePath)
            } else {
                val videoCount = st.mediaInCurrentDir.count { it.isVideo }
                repo.addFavorite(st.currentPath, videoCount)
            }
            _state.update { it.copy(isFavorite = !it.isFavorite) }
        }
    }

    fun openFavorite(folder: FolderEntity) {
        viewModelScope.launch {
            repo.touchFavorite(folder.path, folder.videoCount)
            navigateTo(File(folder.path))
        }
    }

    private fun buildBreadcrumb(dir: File): List<File> {
        val crumb = mutableListOf<File>()
        var current: File? = dir
        val storageRoot = Environment.getExternalStorageDirectory()
        while (current != null && current != storageRoot.parentFile) {
            crumb.add(0, current)
            if (current == storageRoot) break
            current = current.parentFile
        }
        return crumb.takeLast(4) // show max 4 levels
    }

    fun updateFolderPrefs(
        columns: Int,
        sortBy: String,
        filter: String?,
        sizeFilter: String? = null,
        dimensionFilter: String? = null,
        tagFilter: String? = null,
    ) {
        val currentDir = _state.value.currentPath
        val prefs = FolderPrefs(columns, sortBy, filter, sizeFilter, dimensionFilter, tagFilter)
        repo.saveFolderPrefs(currentDir, prefs)
        _state.update { it.copy(folderPrefs = prefs) }
    }

    fun deleteSelected(files: List<MediaFile>) {
        viewModelScope.launch {
            repo.deleteFiles(files.map { it.file })
            navigateTo(_state.value.currentPath)
        }
    }

    fun moveSelected(files: List<MediaFile>, targetDir: File) {
        viewModelScope.launch {
            repo.moveFiles(files.map { it.file }, targetDir)
            navigateTo(_state.value.currentPath)
        }
    }

    fun copySelected(files: List<MediaFile>, targetDir: File) {
        viewModelScope.launch {
            repo.copyFiles(files.map { it.file }, targetDir)
            navigateTo(_state.value.currentPath)
        }
    }

    fun updateFileTags(media: MediaFile, tags: List<String>) {
        viewModelScope.launch {
            repo.updateFileTags(media.file, tags)
            navigateTo(_state.value.currentPath)
        }
    }

    /** Updates tags for a single file in-memory only (no full folder reload = scroll preserved). */
    fun updateTagsInPlace(media: MediaFile, tags: List<String>) {
        viewModelScope.launch {
            repo.updateFileTags(media.file, tags)
            val allKnown = repo.getAllKnownTags()
            _state.update { st ->
                val updated = st.mediaInCurrentDir.map { m ->
                    if (m.file.absolutePath == media.file.absolutePath) m.copy(tags = tags) else m
                }
                st.copy(mediaInCurrentDir = updated, allKnownTags = allKnown)
            }
        }
    }

    fun updateTagsForMultiple(files: List<MediaFile>, tagsToApply: List<String>) {
        viewModelScope.launch {
            files.forEach { media ->
                val mergedTags = (media.tags + tagsToApply).distinct().filter { it.isNotBlank() }
                repo.updateFileTags(media.file, mergedTags)
            }
            navigateTo(_state.value.currentPath)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BrowserViewModel(BrowserRepository(context.applicationContext)) as T
    }
}
