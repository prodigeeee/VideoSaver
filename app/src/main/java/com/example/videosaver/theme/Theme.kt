package com.example.videosaver.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

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

private val LightColorScheme = lightColorScheme(
    primary            = Amber,
    onPrimary          = BackgroundLight,
    primaryContainer   = AmberLight,
    onPrimaryContainer = BackgroundLight,
    secondary          = TealAccent,
    onSecondary        = BackgroundLight,
    tertiary           = PurpleAccent,
    background         = BackgroundLight,
    onBackground       = TextPrimaryLight,
    surface            = SurfaceLight,
    onSurface          = TextPrimaryLight,
    surfaceVariant     = SurfaceMidLight,
    onSurfaceVariant   = TextSecondaryLight,
    outline            = AmberDim,
    error              = ErrorRed,
    onError            = BackgroundLight,
)

@Composable
fun VideoSaverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = VideoSaverTypography,
        content     = content,
    )
}
