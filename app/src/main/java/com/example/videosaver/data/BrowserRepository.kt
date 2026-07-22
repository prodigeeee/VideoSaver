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
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val tags: List<String> = emptyList(),
)

data class FolderPrefs(
    val columns: Int = 2,
    val sortBy: String = "NAME_ASC",
    val mediaFilter: String? = null,
    val sizeFilter: String? = null,
    val dimensionFilter: String? = null,
    val tagFilter: String? = null,
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
    private val downloadDao: DownloadDao = AppDatabase.getInstance(context).downloadDao(),
    private val fileTagDao: FileTagDao = AppDatabase.getInstance(context).fileTagDao(),
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
            columns         = parts.getOrNull(0)?.toIntOrNull() ?: 2,
            sortBy          = parts.getOrNull(1) ?: "NAME_ASC",
            mediaFilter     = parts.getOrNull(2).takeIf { it != "null" },
            sizeFilter      = parts.getOrNull(3).takeIf { it != "null" },
            dimensionFilter = parts.getOrNull(4).takeIf { it != "null" },
            tagFilter       = parts.getOrNull(5).takeIf { it != "null" },
        )
    }

    fun saveFolderPrefs(dir: File, prefsObj: FolderPrefs) {
        val path = dir.absolutePath
        val str = "${prefsObj.columns}|${prefsObj.sortBy}|${prefsObj.mediaFilter ?: "null"}|${prefsObj.sizeFilter ?: "null"}|${prefsObj.dimensionFilter ?: "null"}|${prefsObj.tagFilter ?: "null"}"
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
            ?.filter { showHidden || !it.name.startsWith(".") }
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

        val tagsMap = try {
            val fromDl = downloadDao.getCompletedDownloadsList().associate { it.filePath to it.tags }
            val fromFileTag = fileTagDao.getAllFileTags().associate { it.filePath to it.tags }
            fromDl + fromFileTag
        } catch (e: Exception) {
            emptyMap()
        }

        dir.listFiles()?.forEach { f ->
            when {
                !f.isDirectory && (showHidden || !f.name.startsWith(".")) -> {
                    val tags = tagsMap[f.absolutePath] ?: emptyList()
                    if (isVideoFile(f)) {
                        val (w, h) = getVideoDimensions(f)
                        results.add(toMediaFile(f, isVideo = true, width = w, height = h, tags = tags))
                    }
                    else if (isAudioFile(f)) results.add(toMediaFile(f, isAudio = true, tags = tags))
                    else if (isImageFile(f)) results.add(toMediaFile(f, isImage = true, tags = tags))
                }
            }
        }
        
        results.sortedByDescending { it.lastModified }
    }

    private fun getVideoDimensions(f: File): Pair<Int, Int> {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(f.absolutePath)
            val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()
            if (rot == 90 || rot == 270) Pair(h, w) else Pair(w, h)
        } catch (e: Exception) {
            Pair(0, 0)
        }
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

    private fun toMediaFile(
        f: File,
        isVideo: Boolean = false,
        isAudio: Boolean = false,
        isImage: Boolean = false,
        width: Int = 0,
        height: Int = 0,
        tags: List<String> = emptyList(),
    ) = MediaFile(
        file         = f,
        name         = f.name,
        sizeBytes    = f.length(),
        lastModified = f.lastModified(),
        isVideo      = isVideo,
        isAudio      = isAudio,
        isImage      = isImage,
        extension    = f.extension.lowercase(),
        videoWidth   = width,
        videoHeight  = height,
        tags         = tags,
    )

    suspend fun updateFileTags(file: File, tags: List<String>) = withContext(Dispatchers.IO) {
        fileTagDao.insertOrUpdate(FileTagEntity(file.absolutePath, tags))
        downloadDao.purgeLocalFileEntries()

        val existing = downloadDao.getByFilePath(file.absolutePath)
        if (existing != null && !existing.url.startsWith("file://")) {
            downloadDao.updateTags(existing.id, tags)
        }

        // 1. Physical metadata: EXIF UserComment for images
        val ext = file.extension.lowercase()
        if (ext in setOf("jpg", "jpeg", "png", "webp") && file.canWrite()) {
            try {
                val exif = androidx.exifinterface.media.ExifInterface(file)
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT, tags.joinToString(", "))
                exif.saveAttributes()
            } catch (e: Exception) {
                // Ignore if read-only or unsupported format
            }
        }

        // 2. Physical metadata: Sidecar XMP file for video/audio/other files
        val xmpFile = File("${file.absolutePath}.xmp")
        if (tags.isEmpty()) {
            if (xmpFile.exists()) xmpFile.delete()
        } else {
            try {
                val tagsXml = tags.joinToString("\n") { "       <rdf:li>$it</rdf:li>" }
                val xmpContent = """
                    <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
                    <x:xmpmeta xmlns:x="adobe:ns:meta/">
                     <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                      <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                       <dc:subject>
                        <rdf:Bag>
                    $tagsXml
                        </rdf:Bag>
                       </dc:subject>
                      </rdf:Description>
                     </rdf:RDF>
                    </x:xmpmeta>
                    <?xpacket end="w"?>
                """.trimIndent()
                xmpFile.writeText(xmpContent)
            } catch (e: Exception) {
                // Ignore if cannot write
            }
        }
    }

    private fun friendlyName(f: File): String = when (f.name) {
        "VideoSaver"                           -> "⭐ VideoSaver"
        Environment.DIRECTORY_DOWNLOADS       -> "📥 Téléchargements"
        Environment.DIRECTORY_DCIM            -> "📷 DCIM"
        Environment.DIRECTORY_MOVIES          -> "🎬 Films"
        Environment.DIRECTORY_MUSIC           -> "🎵 Musique"
        else                                   -> f.name
    }
}
