package com.corall.agrotrack.presentation.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = ColdBlue80,
    onPrimary        = SurfaceDark,
    primaryContainer = ColdBlue40,
    secondary        = IceGreen80,
    onSecondary      = SurfaceDark,
    error            = AlertRed80,
    background       = SurfaceDark,
    surface          = SurfaceMid,
    onSurface        = OnSurfaceLight,
    onBackground     = OnSurfaceLight,
)

@Composable
fun AgroTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AgroTrackTypography,
        content     = content,
    )
}
