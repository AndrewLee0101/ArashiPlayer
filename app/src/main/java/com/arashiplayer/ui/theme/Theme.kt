package com.arashiplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF0A0B10)
val SurfaceGlass = Color(0xCC181B26)
val SurfaceCard = Color(0xFF161925)
val AccentCyan = Color(0xFF5CE1E6)
val AccentViolet = Color(0xFF9B82F3)
val ApplePink = Color(0xFFFF375F)
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF9BA3B5)

private val ArashiDarkScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentViolet,
    tertiary = ApplePink,
    background = ObsidianBg,
    surface = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ArashiPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArashiDarkScheme,
        content = content
    )
}
