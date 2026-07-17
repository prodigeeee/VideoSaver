package com.example.videosaver.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary            = Amber,
    onPrimary          = Background,
    primaryContainer   = AmberGlow,
    onPrimaryContainer = AmberLight,
    secondary          = TealAccent,
    onSecondary        = Background,
    tertiary           = PurpleAccent,
    background         = Background,
    onBackground       = TextPrimary,
    surface            = SurfaceDark,
    onSurface          = TextPrimary,
    surfaceVariant     = SurfaceMid,
    onSurfaceVariant   = TextSecondary,
    outline            = AmberDim,
    error              = ErrorRed,
    onError            = Background,
)

@Composable
fun VideoSaverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = VideoSaverTypography,
        content     = content,
    )
}
