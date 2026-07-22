package com.example.videosaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "file_tags")
data class FileTagEntity(
    @PrimaryKey val filePath: String,
    val tags: List<String>,
)

@Dao
interface FileTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(fileTag: FileTagEntity)

    @Query("SELECT * FROM file_tags")
    suspend fun getAllFileTags(): List<FileTagEntity>

    @Query("DELETE FROM file_tags WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)
}
