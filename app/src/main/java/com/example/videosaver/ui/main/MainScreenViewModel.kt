package com.example.videosaver.ui.main

// Template compatibility stub — logic moved to DownloadViewModel.

import androidx.lifecycle.ViewModel
import com.example.videosaver.data.DefaultDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class MainScreenUiState {
    object Loading : MainScreenUiState()
    data class Success(val data: List<String>) : MainScreenUiState()
    data class Error(val throwable: Throwable) : MainScreenUiState()
}

class MainScreenViewModel(repo: DefaultDataRepository) : ViewModel() {
    val uiState: StateFlow<MainScreenUiState> = MutableStateFlow(MainScreenUiState.Success(emptyList()))
}
