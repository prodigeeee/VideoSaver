package com.example.videosaver.data

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo as YtVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Central repository that wraps yt-dlp operations and the local Room database.
 */
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao = AppDatabase.getInstance(context).downloadDao(),
) {
    // ─── Flows ─────────────────────────────────────────────────────────────────
    val allDownloads: Flow<List<DownloadEntity>> = dao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = dao.getCompletedDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = dao.getActiveDownloads()

    // ─── Metadata fetching ──────────────────────────────────────────────────────
    /**
     * Fetches video metadata (title, thumbnail, available formats) without downloading.
     */
    suspend fun fetchVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--skip-download")
        }
        val response = YoutubeDL.getInstance().execute(request)
        parseVideoInfo(JSONObject(response.out))
    }

    private fun parseVideoInfo(json: JSONObject): VideoInfo {
        val title = json.optString("title", "Untitled")
        val thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() }
        val duration = json.optLong("duration", 0L)
        val uploader = json.optString("uploader").takeIf { it.isNotBlank() }

        val formats = mutableListOf<VideoFormat>()
        val seenLabels = mutableSetOf<String>()

        // Parse available formats
        val formatsArray: JSONArray? = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.getJSONObject(i)
                val vcodec = f.optString("vcodec", "none").takeIf { it != "none" }
                val acodec = f.optString("acodec", "none").takeIf { it != "none" }
                val ext = f.optString("ext", "?")
                val formatId = f.optString("format_id", "")
                val width = f.optInt("width", 0)
                val height = f.optInt("height", 0)
                val fps = f.optInt("fps", 0).takeIf { it > 0 }
                val filesize = f.optLong("filesize", 0L).takeIf { it > 0L }

                val isAudioOnly = vcodec == null && acodec != null
                val resolution = if (isAudioOnly) "audio only"
                    else if (width > 0 && height > 0) "${width}x${height}"
                    else "unknown"

                val label = if (isAudioOnly) {
                    "Audio • ${ext.uppercase()}"
                } else {
                    val res = if (height > 0) "${height}p" else resolution
                    val fpsStr = if (fps != null && fps > 30) " ${fps}fps" else ""
                    "$res$fpsStr • ${ext.uppercase()}"
                }

                // Deduplicate similar labels for cleaner picker
                if (label !in seenLabels && formatId.isNotBlank()) {
                    seenLabels.add(label)
                    formats.add(
                        VideoFormat(
                            formatId = formatId,
                            ext = ext,
                            resolution = resolution,
                            fps = fps,
                            filesize = filesize,
                            vcodec = vcodec,
                            acodec = acodec,
                            label = label,
                            isAudioOnly = isAudioOnly,
                        )
                    )
                }
            }
        }

        // Add "Best quality" shortcut at the top
        val bestVideo = VideoFormat(
            formatId = "bestvideo+bestaudio/best",
            ext = "mp4",
            resolution = "best",
            fps = null, filesize = null, vcodec = null, acodec = null,
            label = "Meilleure qualité (auto)",
            isAudioOnly = false,
        )
        val bestAudio = VideoFormat(
            formatId = "bestaudio",
            ext = "mp3",
            resolution = "audio only",
            fps = null, filesize = null, vcodec = null, acodec = null,
            label = "Audio uniquement • MP3",
            isAudioOnly = true,
        )

        // Sort: best first, then by height descending, then audio-only last
        val sorted = formats.sortedWith(
            compareByDescending<VideoFormat> { !it.isAudioOnly }
                .thenByDescending {
                    it.resolution.substringBefore("x").toIntOrNull() ?: 0
                }
        )

        return VideoInfo(
            title = title,
            thumbnailUrl = thumbnail,
            durationSeconds = duration,
            uploaderName = uploader,
            formats = listOf(bestVideo, bestAudio) + sorted,
        )
    }

    // ─── Download operations ────────────────────────────────────────────────────
    /**
     * Starts a download and tracks it in the Room database.
     * Calls [onProgress] with (percent 0-100, speed string, eta string) updates.
     */
    suspend fun startDownload(
        url: String,
        format: VideoFormat,
        videoInfo: VideoInfo,
        isAudioOnly: Boolean,
        audioFormat: String = "mp3",
        onProgress: suspend (id: Long, progress: Int, speed: Long, eta: String?) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val outputDir = getDownloadDir(isAudioOnly)
        outputDir.mkdirs()

        // Insert pending record
        val entity = DownloadEntity(
            url = url,
            title = videoInfo.title,
            thumbnailUrl = videoInfo.thumbnailUrl,
            filePath = outputDir.absolutePath,
            quality = format.label,
            formatId = format.formatId,
            isAudioOnly = isAudioOnly,
            audioFormat = audioFormat,
            durationSeconds = videoInfo.durationSeconds,
            status = DownloadStatus.FETCHING_INFO,
        )
        val id = dao.insert(entity)

        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--restrict-filenames")

                if (isAudioOnly) {
                    addOption("-f", "bestaudio")
                    addOption("-x")
                    addOption("--audio-format", audioFormat)
                    addOption("--audio-quality", "0")
                } else {
                    addOption("-f", format.formatId)
                    addOption("--merge-output-format", "mp4")
                }

                // Use aria2c for faster downloads
                addOption("--external-downloader", "aria2c")
                addOption("--external-downloader-args", "aria2c:-x 16 -k 1M")
            }

            dao.updateProgress(id, DownloadStatus.DOWNLOADING, 0, 0L, null)

            YoutubeDL.getInstance().execute(request) { progressPercent, etaInSeconds, line ->
                val speedBps = parseSpeedFromLine(line)
                val etaStr = if (etaInSeconds > 0) formatEta(etaInSeconds.toLong()) else null
                // Fire progress callback (non-blocking via coroutine bridge)
                kotlinx.coroutines.runBlocking {
                    onProgress(id, progressPercent.toInt(), speedBps, etaStr)
                    dao.updateProgress(id, DownloadStatus.DOWNLOADING, progressPercent.toInt(), speedBps, etaStr)
                }
            }

            // Find the output file
            var outputFile = outputDir.listFiles()
                ?.filter { it.lastModified() > entity.createdAt }
                ?.maxByOrNull { it.lastModified() }

            // If yt-dlp skipped download because the file already exists, it might not have touched lastModified.
            if (outputFile == null) {
                val targetExt = if (isAudioOnly) audioFormat else "mp4"
                outputFile = outputDir.listFiles()
                    ?.filter { it.extension.equals(targetExt, ignoreCase = true) }
                    ?.maxByOrNull { it.lastModified() }
            }

            dao.markCompleted(id, System.currentTimeMillis(), outputFile?.absolutePath ?: outputDir.absolutePath)
        } catch (e: Exception) {
            dao.markFailed(id, e.message ?: "Unknown error")
            throw e
        }

        id
    }

    suspend fun cancelDownload(id: Long) {
        val entity = dao.getById(id) ?: return
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        dao.markCancelled(id)
    }

    suspend fun deleteDownload(id: Long, deleteFile: Boolean = true) {
        val dl = dao.getById(id) ?: return
        if (dl.status == DownloadStatus.DOWNLOADING) {
            YoutubeDL.getInstance().destroyProcessById(id.toString())
        }
        if (deleteFile) {
            val file = File(dl.filePath)
            if (file.exists()) file.delete()
        }
        dao.deleteById(id)
    }

    suspend fun moveDownload(id: Long, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        val dl = dao.getById(id) ?: return@withContext false
        val file = File(dl.filePath)
        if (!file.exists() || !file.isFile) return@withContext false
        if (!targetDir.exists()) targetDir.mkdirs()
        
        val newFile = File(targetDir, file.name)
        try {
            file.copyTo(newFile, overwrite = true)
            if (file.delete()) {
                dao.deleteById(id)
                return@withContext true
            }
        } catch (e: Exception) { }
        return@withContext false
    }

    suspend fun updateTags(id: Long, tags: List<String>) {
        dao.updateTags(id, tags)
    }

    // ─── yt-dlp updates ──────────────────────────────────────────────────────
    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE).toString()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun getDownloadDir(audioOnly: Boolean): File {
        val base = Environment.getExternalStoragePublicDirectory(
            if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS
        )
        return File(base, "VideoSaver")
    }

    private fun parseSpeedFromLine(line: String): Long {
        // yt-dlp prints lines like "15.0MiB/s" or "512KiB/s"
        val regex = Regex("""([\d.]+)(K|M|G)iB/s""")
        val match = regex.find(line) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (match.groupValues[2]) {
            "K" -> (value * 1024).toLong()
            "M" -> (value * 1024 * 1024).toLong()
            "G" -> (value * 1024 * 1024 * 1024).toLong()
            else -> 0L
        }
    }

    private fun formatEta(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
