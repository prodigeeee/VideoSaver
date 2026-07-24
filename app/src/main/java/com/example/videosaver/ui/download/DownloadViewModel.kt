package com.example.videosaver.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videosaver.data.DownloadRepository
import com.example.videosaver.data.VideoFormat
import com.example.videosaver.data.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

// ─── UI State ────────────────────────────────────────────────────────────────

sealed class UrlFetchState {
    data object Idle : UrlFetchState()
    data object Loading : UrlFetchState()
    data class Success(val info: VideoInfo) : UrlFetchState()
    data class Error(val message: String) : UrlFetchState()
}

data class DownloadProgress(
    val percent: Int,
    val speedBytesPerSec: Long,
    val eta: String?
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class DownloadViewModel(
    private val repository: DownloadRepository,
) : ViewModel() {

    val urlInput = MutableStateFlow("")

    private val _fetchState = MutableStateFlow<UrlFetchState>(UrlFetchState.Idle)
    val fetchState: StateFlow<UrlFetchState> = _fetchState.asStateFlow()

    val showFormatPicker = MutableStateFlow(false)
    val progressMap = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())

    val downloads = repository.allDownloads

    fun onUrlChanged(url: String) {
        urlInput.value = url
    }

    fun onPasteFromClipboard(url: String) {
        urlInput.value = url
        fetchVideoInfo(url)
    }

    fun dismissFormatPicker() {
        showFormatPicker.value = false
    }

    fun fetchVideoInfo(url: String = urlInput.value) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _fetchState.value = UrlFetchState.Loading
            try {
                val info = repository.fetchVideoInfo(url)
                _fetchState.value = UrlFetchState.Success(info)
                showFormatPicker.value = true
            } catch (e: Exception) {
                _fetchState.value = UrlFetchState.Error(e.message ?: "Une erreur est survenue")
            }
        }
    }

    fun startDownload(
        format: VideoFormat,
        isAudioOnly: Boolean,
        audioFormat: String
    ) {
        val state = _fetchState.value
        if (state is UrlFetchState.Success) {
            viewModelScope.launch {
                showFormatPicker.value = false
                try {
                    repository.startDownload(
                        url = urlInput.value,
                        format = format,
                        videoInfo = state.info,
                        isAudioOnly = isAudioOnly,
                        audioFormat = audioFormat,
                        onProgress = { id, percent, speed, eta ->
                            val current = progressMap.value.toMutableMap()
                            current[id] = DownloadProgress(percent, speed, eta)
                            progressMap.value = current
                        }
                    )
                    urlInput.value = ""
                    _fetchState.value = UrlFetchState.Idle
                } catch (e: Exception) {
                    // Handled inside repo mostly
                }
            }
        }
    }

    fun cancelDownload(id: Long) {
        viewModelScope.launch {
            repository.cancelDownload(id)
        }
    }

    fun deleteDownload(id: Long, deleteFile: Boolean = true) {
        viewModelScope.launch {
            repository.deleteDownload(id, deleteFile)
        }
    }

    fun deleteDownloads(ids: Set<Long>, deleteFile: Boolean = true) {
        viewModelScope.launch {
            ids.forEach { repository.deleteDownload(it, deleteFile) }
        }
    }

    fun updateTags(id: Long, tags: List<String>) {
        viewModelScope.launch {
            repository.updateTags(id, tags)
        }
    }

    fun moveDownload(id: Long, folder: File) {
        viewModelScope.launch {
            repository.moveDownload(id, folder)
        }
    }

    fun moveDownloads(ids: Set<Long>, folder: File) {
        viewModelScope.launch {
            ids.forEach { repository.moveDownload(it, folder) }
        }
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            repository.updateYtDlp()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = com.example.videosaver.data.AppDatabase.getInstance(context)
            val repo = DownloadRepository(context, db.downloadDao())
            return DownloadViewModel(repo) as T
        }
    }
}

