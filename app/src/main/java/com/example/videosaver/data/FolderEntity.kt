package com.example.videosaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a folder bookmarked as a favorite in the browser.
 */
@Entity(tableName = "favorite_folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
)
