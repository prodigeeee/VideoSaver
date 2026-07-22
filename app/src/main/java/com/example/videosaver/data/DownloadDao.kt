package com.example.videosaver.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedDownloadsList(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'FETCHING_INFO', 'DOWNLOADING') ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE downloads SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: List<String>)

    @Query("UPDATE downloads SET filePath = :filePath WHERE id = :id")
    suspend fun updateFilePath(id: Long, filePath: String)

    @Query("UPDATE downloads SET status = :status, progress = :progress, speedBytesPerSec = :speed, eta = :eta WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Int, speed: Long, eta: String?)

    @Query("UPDATE downloads SET status = 'COMPLETED', completedAt = :completedAt, progress = 100, filePath = :filePath WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long, filePath: String)

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE downloads SET status = 'CANCELLED' WHERE id = :id")
    suspend fun markCancelled(id: Long)
}
