package com.example.videosaver

import android.app.Application
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

/**
 * Application class for VideoSaver: initializes the yt-dlp engine (YoutubeDL + FFmpeg + aria2c)
 * at startup so that downloads are ready as soon as the user opens the app.
 */
class VideoSaverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            e.printStackTrace()
        }
    }
}


