package com.example.videosaver

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Application class: initializes the yt-dlp engine (YoutubeDL + FFmpeg + aria2c)
 * at startup so that downloads are ready as soon as the user opens the app.
 */
class VideoSaverApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            // Log but don't crash — init failures are typically environment-related
            e.printStackTrace()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            // ── NO VideoFrameDecoder: it uses hardware MediaCodec which competes with ExoPlayer ──
            // Coil falls back to its built-in BitmapFactory for images.
            // Videos are handled by our custom fetcher in MediaGridCard (MediaMetadataRetriever = CPU only).
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15) // 15% of RAM for thumbnails
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "coil_thumbnails").toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                    .build()
            }
            // Limit concurrent decode operations to avoid exhausting hardware decoders
            .fetcherCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(2))
            .decoderCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(2))
            .build()
    }
}

