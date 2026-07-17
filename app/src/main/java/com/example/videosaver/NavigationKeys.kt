package com.example.videosaver

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey
import com.example.videosaver.data.MediaFile

@Serializable data object Home     : NavKey
@Serializable data object Library  : NavKey
@Serializable data object Browser  : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Main     : NavKey // template compat

/** Passed as a transient destination — not persisted to back-stack DB */
data class VideoPlayer(
    val playlist: List<MediaFile>,
    val startIndex: Int = 0,
)
