package com.example.videosaver.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [DownloadEntity::class, FolderEntity::class, FileTagEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun folderDao(): FolderDao
    abstract fun fileTagDao(): FileTagDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS file_tags (filePath TEXT NOT NULL, tags TEXT NOT NULL, PRIMARY KEY(filePath))")
                database.execSQL("DELETE FROM downloads WHERE url LIKE 'file://%'")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "videosaver_db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
                .also { INSTANCE = it }
            }
    }
}
