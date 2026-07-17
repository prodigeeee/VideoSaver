package com.example.videosaver.data

/**
 * Represents a downloadable format available for a given URL,
 * fetched from yt-dlp's metadata (--list-formats).
 */
data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,     // e.g. "1920x1080", "audio only"
    val fps: Int?,
    val filesize: Long?,
    val vcodec: String?,
    val acodec: String?,
    val label: String,          // Human-readable, e.g. "1080p • MP4"
    val isAudioOnly: Boolean,
)

/**
 * Metadata fetched before downloading: title, thumbnail, available formats.
 */
data class VideoInfo(
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val uploaderName: String?,
    val formats: List<VideoFormat>,
)
