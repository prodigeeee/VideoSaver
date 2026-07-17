package com.example.videosaver.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** A media file entry found while scanning a directory */
data class MediaFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean,
    val extension: String,
)

data class FolderPrefs(
    val columns: Int = 2,
    val sortBy: String = "NAME_ASC",
    val mediaFilter: String? = null
)

/** A directory entry shown in the browser */
data class BrowserEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val childCount: Int = 0,       // sub-items count (for directories)
    val mediaCount: Int = 0,       // media files inside (for directories)
    val sizeBytes: Long = 0L,      // for files
    val lastModified: Long = 0L,
    val thumbnail: File? = null,
)

/** Repository for filesystem browsing and folder favorites management */
class BrowserRepository(
    private val context: Context,
    private val folderDao: FolderDao = AppDatabase.getInstance(context).folderDao(),
) {
    private val prefs = context.getSharedPreferences("videosaver_prefs", Context.MODE_PRIVATE)
    private fun showHiddenFiles(): Boolean = prefs.getBoolean("show_hidden_files", false)

    // ─── Favorites ────────────────────────────────────────────────────────────
    val favorites: Flow<List<FolderEntity>> = folderDao.getAllFavorites()

    suspend fun addFavorite(dir: File, videoCount: Int) {
        folderDao.insert(FolderEntity(
            path        = dir.absolutePath,
            displayName = dir.name,
            videoCount  = videoCount,
        ))
    }

    suspend fun removeFavorite(path: String) = folderDao.deleteByPath(path)

    suspend fun isFavorite(path: String) = folderDao.isFavorite(path)

    suspend fun touchFavorite(path: String, count: Int) =
        folderDao.updateAccess(path, System.currentTimeMillis(), count)

    // ─── Folder Preferences ───────────────────────────────────────────────────
    fun getFolderPrefs(dir: File): FolderPrefs {
        val path = dir.absolutePath
        val str = prefs.getString("pref_$path", null) ?: return FolderPrefs()
        val parts = str.split("|")
        return FolderPrefs(
            columns = parts.getOrNull(0)?.toIntOrNull() ?: 2,
            sortBy = parts.getOrNull(1) ?: "NAME_ASC",
            mediaFilter = parts.getOrNull(2).takeIf { it != "null" }
        )
    }

    fun saveFolderPrefs(dir: File, prefsObj: FolderPrefs) {
        val path = dir.absolutePath
        val str = "${prefsObj.columns}|${prefsObj.sortBy}|${prefsObj.mediaFilter ?: "null"}"
        prefs.edit().putString("pref_$path", str).apply()
    }

    // ─── File Operations ──────────────────────────────────────────────────────
    suspend fun deleteFiles(files: List<File>): Boolean = withContext(Dispatchers.IO) {
        var success = true
        for (f in files) {
            if (f.exists()) {
                if (!f.delete()) success = false
            }
        }
        success
    }

    suspend fun moveFiles(files: List<File>, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) targetDir.mkdirs()
        var success = true
        for (f in files) {
            if (f.exists() && f.isFile) {
                val newFile = File(targetDir, f.name)
                try {
                    f.copyTo(newFile, overwrite = true)
                    f.delete()
                } catch (e: Exception) {
                    success = false
                }
            }
        }
        success
    }

    suspend fun copyFiles(files: List<File>, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) targetDir.mkdirs()
        var success = true
        for (f in files) {
            if (f.exists() && f.isFile) {
                val newFile = File(targetDir, f.name)
                try {
                    f.copyTo(newFile, overwrite = true)
                } catch (e: Exception) {
                    success = false
                }
            }
        }
        success
    }

    // ─── Filesystem browsing ──────────────────────────────────────────────────
    /** Lists entries (dirs + media files) in a directory, sorted: dirs first then files. */
    suspend fun listDirectory(dir: File): List<BrowserEntry> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()

        val showHidden = showHiddenFiles()

        dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") } // hide hidden
            ?.map { f ->
                if (f.isDirectory) {
                    val childArray = f.list() ?: emptyArray()
                    val firstMediaName = childArray.firstOrNull { isMediaFileName(it) }
                    val thumbnailFile = firstMediaName?.let { File(f, it) }
                    BrowserEntry(
                        file        = f,
                        name        = f.name,
                        isDirectory = true,
                        childCount  = childArray.size,
                        mediaCount  = childArray.count { isMediaFileName(it) },
                        lastModified = f.lastModified(),
                        thumbnail   = thumbnailFile,
                    )
                } else {
                    BrowserEntry(
                        file         = f,
                        name         = f.name,
                        isDirectory  = false,
                        sizeBytes    = f.length(),
                        lastModified = f.lastModified(),
                    )
                }
            }
            ?.filter { it.isDirectory || isMediaFile(it.file) }
            ?.sortedWith(
                compareByDescending<BrowserEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            ?: emptyList()
    }

    /** Scans a directory for media files (non-recursive to be fast) */
    suspend fun scanMediaFiles(dir: File): List<MediaFile> = withContext(Dispatchers.IO) {
        val showHidden = showHiddenFiles()
        val results = mutableListOf<MediaFile>()
        
        dir.listFiles()?.forEach { f ->
            when {
                !f.isDirectory && (showHidden || !f.name.startsWith(".")) -> {
                    if (isVideoFile(f)) results.add(toMediaFile(f, isVideo = true))
                    else if (isAudioFile(f)) results.add(toMediaFile(f, isAudio = true))
                    else if (isImageFile(f)) results.add(toMediaFile(f, isImage = true))
                }
            }
        }
        
        results.sortedByDescending { it.lastModified }
    }

    /** Returns the quick-access root directories the user cares about */
    fun getRootDirectories(): List<BrowserEntry> {
        val roots = mutableListOf<File>()
        // Primary external storage
        Environment.getExternalStorageDirectory()?.let { roots.add(it) }
        // Downloads
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { roots.add(it) }
        // DCIM
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let { roots.add(it) }
        // Movies
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.let { roots.add(it) }
        // Music
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { roots.add(it) }
        // VideoSaver folder itself
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VideoSaver").let {
            if (it.exists()) roots.add(0, it)
        }

        return roots.filter { it.exists() && it.canRead() }.map { f ->
            BrowserEntry(file = f, name = friendlyName(f), isDirectory = true)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "ts", "3gp")
    private val audioExts = setOf("mp3", "m4a", "opus", "flac", "wav", "ogg", "aac")
    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    private fun getExtension(name: String) = name.substringAfterLast('.', "").lowercase()
    private fun isMediaFileName(name: String): Boolean {
        val ext = getExtension(name)
        return ext in videoExts || ext in audioExts || ext in imageExts
    }

    private fun isVideoFile(f: File) = f.extension.lowercase() in videoExts
    private fun isAudioFile(f: File) = f.extension.lowercase() in audioExts
    private fun isImageFile(f: File) = f.extension.lowercase() in imageExts
    private fun isMediaFile(f: File) = isMediaFileName(f.name)

    private fun toMediaFile(f: File, isVideo: Boolean = false, isAudio: Boolean = false, isImage: Boolean = false) = MediaFile(
        file         = f,
        name         = f.name,
        sizeBytes    = f.length(),
        lastModified = f.lastModified(),
        isVideo      = isVideo,
        isAudio      = isAudio,
        isImage      = isImage,
        extension    = f.extension.lowercase(),
    )

    private fun friendlyName(f: File): String = when (f.name) {
        "VideoSaver"                           -> "⭐ VideoSaver"
        Environment.DIRECTORY_DOWNLOADS       -> "📥 Téléchargements"
        Environment.DIRECTORY_DCIM            -> "📷 DCIM"
        Environment.DIRECTORY_MOVIES          -> "🎬 Films"
        Environment.DIRECTORY_MUSIC           -> "🎵 Musique"
        else                                   -> f.name
    }
}
