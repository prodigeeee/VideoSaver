package com.example.videosaver.data

// This file is kept for compilation compatibility.
// The actual data access is handled by DownloadRepository.

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
    val data: Flow<List<String>>
}

class DefaultDataRepository : DataRepository {
    override val data: Flow<List<String>> = flow { emit(emptyList()) }
}
