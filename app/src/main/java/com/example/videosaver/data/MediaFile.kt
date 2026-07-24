package com.example.videosaver.data

import java.io.File

/** A media file entry representation */
data class MediaFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean,
    val extension: String,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val tags: List<String> = emptyList(),
)
