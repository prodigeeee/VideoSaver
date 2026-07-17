package com.example.videosaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a completed or in-progress video/audio download stored in the local database.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val filePath: String,
    val fileSize: Long = 0L,
    val durationSeconds: Long = 0L,
    val quality: String,            // e.g. "1080p", "720p", "best", "audio"
    val formatId: String = "best",  // yt-dlp format id
    val isAudioOnly: Boolean = false,
    val audioFormat: String = "mp3", // "mp3" | "m4a" | "opus"
    val status: DownloadStatus,
    val errorMessage: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val progress: Int = 0,           // 0–100
    val speedBytesPerSec: Long = 0L,
    val eta: String? = null,
)

enum class DownloadStatus {
    PENDING,
    FETCHING_INFO,   // Fetching video metadata
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}
