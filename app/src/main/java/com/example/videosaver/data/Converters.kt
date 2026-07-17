package com.example.videosaver.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromTagsList(tags: List<String>): String = tags.joinToString("|")

    @TypeConverter
    fun toTagsList(data: String): List<String> = if (data.isEmpty()) emptyList() else data.split("|")
}
