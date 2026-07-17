package com.example.videosaver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import com.example.videosaver.theme.VideoSaverTheme

class MainActivity : ComponentActivity() {
    companion object {
        var isVideoPlayerActive = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlayerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!android.os.Environment.isExternalStorageManager()) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = android.net.Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_MEDIA_VIDEO,
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_AUDIO,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    launcher.launch(
                        arrayOf(
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    )
                }
            }

            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("videosaver_prefs", android.content.Context.MODE_PRIVATE) }
            var themePref by remember { mutableStateOf(prefs.getString("theme_pref", "system") ?: "system") }

            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "theme_pref") {
                        themePref = sharedPreferences.getString("theme_pref", "system") ?: "system"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val isDarkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            VideoSaverTheme(darkTheme = isDarkTheme) {
                MainNavigation()
            }
        }
    }

    /** Handle URLs shared from other apps (e.g. "Share" from YouTube) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            // TODO: pass shared URL to DownloadViewModel via a shared state holder
        }
    }
}
