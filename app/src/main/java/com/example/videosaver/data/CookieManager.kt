package com.example.videosaver.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages yt-dlp session cookies.
 *
 * Cookies can be imported in Netscape format (cookies.txt) exported from
 * browser extensions such as "Get cookies.txt LOCALLY" (Chrome) or "Export Cookies"
 * (Firefox). Once saved, yt-dlp automatically uses them for private/gated content.
 */
class CookieManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("videosaver_prefs", Context.MODE_PRIVATE)

    /** Internal path where the cookie file is stored */
    val cookieFile: File
        get() = File(context.filesDir, "cookies.txt")

    /** True if a non-empty cookie file exists */
    val hasCookies: Boolean
        get() = cookieFile.exists() && cookieFile.length() > 0

    /** Number of cookie entries (rough count of non-comment lines) */
    val cookieCount: Int
        get() = if (!hasCookies) 0
                else cookieFile.readLines().count { it.isNotBlank() && !it.startsWith("#") }

    /** Import a cookies.txt content string (Netscape format) */
    suspend fun importCookies(content: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val lines = content.lines()
            val valid = lines.filter { line ->
                line.isBlank() || line.startsWith("#") || line.split("\t").size >= 7
            }
            if (valid.isEmpty()) return@withContext Result.failure(
                IllegalArgumentException("Format invalide — assurez-vous d'exporter en format Netscape (cookies.txt)")
            )
            cookieFile.writeText(valid.joinToString("\n"))
            val count = valid.count { it.isNotBlank() && !it.startsWith("#") }
            prefs.edit { putLong("cookies_imported_at", System.currentTimeMillis()) }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete stored cookies */
    fun clearCookies() {
        cookieFile.delete()
        prefs.edit { remove("cookies_imported_at") }
    }

    /** Timestamp when cookies were last imported (ms) */
    fun importedAt(): Long = prefs.getLong("cookies_imported_at", 0L)

    /** Returns the --cookies argument for yt-dlp if available */
    fun ytdlpCookieArg(): String? =
        if (hasCookies) cookieFile.absolutePath else null
}
