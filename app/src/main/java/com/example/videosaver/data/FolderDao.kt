package com.example.videosaver.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM favorite_folders ORDER BY lastAccessedAt DESC")
    fun getAllFavorites(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Query("DELETE FROM favorite_folders WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_folders WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    @Query("UPDATE favorite_folders SET lastAccessedAt = :ts, videoCount = :count WHERE path = :path")
    suspend fun updateAccess(path: String, ts: Long, count: Int)
}
