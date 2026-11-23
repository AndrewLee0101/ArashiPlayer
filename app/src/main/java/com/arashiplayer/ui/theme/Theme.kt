package com.arashiplayer.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class UiStyleMode { LIQUID_GLASS, MATERIAL_YOU }
enum class DarkModePref { LIGHT, DARK, SYSTEM }

data class ThemePreset(
    val id: String,
    val name: String,
    val accent: Color
)

val ThemePresets = listOf(
    ThemePreset("glacier", "冰川蓝", Color(0xFF0A84FF)),
    ThemePreset("sunset", "落日橘", Color(0xFFFF6B35)),
    ThemePreset("mist_violet", "雾霭紫", Color(0xFF7C6CF6)),
    ThemePreset("sakura", "樱花粉", Color(0xFFFF4D7D)),
    ThemePreset("forest", "森系绿", Color(0xFF347C57)),
    ThemePreset("minimal_black", "极简黑", Color(0xFF1F242D))
)

data class ArashiThemeState(
    val style: UiStyleMode = UiStyleMode.LIQUID_GLASS,
    val darkMode: DarkModePref = DarkModePref.LIGHT,
    val presetId: String = "glacier",
    val customAccentArgb: Int? = null,
    val glassTransparency: Float = 0.65f,
    val dynamicMonet: Boolean = true
) {
    fun resolveAccent(): Color {
        customAccentArgb?.let { return Color(it) }
        return ThemePresets.firstOrNull { it.id == presetId }?.accent ?: ThemePresets.first().accent
    }
}

class ThemePrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("arashi_theme_v3", Context.MODE_PRIVATE)

    fun load(): ArashiThemeState {
        val style = runCatching {
            UiStyleMode.valueOf(prefs.getString("style", UiStyleMode.LIQUID_GLASS.name)!!)
        }.getOrDefault(UiStyleMode.LIQUID_GLASS)
        val darkMode = runCatching {
            DarkModePref.valueOf(prefs.getString("darkMode", DarkModePref.LIGHT.name)!!)
        }.getOrDefault(DarkModePref.LIGHT)
        val presetId = prefs.getString("presetId", "glacier") ?: "glacier"
        val hasCustom = prefs.getBoolean("hasCustom", false)
        val customArgb = if (hasCustom) prefs.getInt("customArgb", Color(0xFF0A84FF).toArgb()) else null
        val glassAlpha = prefs.getFloat("glassTransparency", 0.65f)
        val dynamicMonet = prefs.getBoolean("dynamicMonet", true)
        return ArashiThemeState(style, darkMode, presetId, customArgb, glassAlpha, dynamicMonet)
    }

    fun save(state: ArashiThemeState) {
        prefs.edit()
            .putString("style", state.style.name)
            .putString("darkMode", state.darkMode.name)
            .putString("presetId", state.presetId)
            .putBoolean("hasCustom", state.customAccentArgb != null)
            .putInt("customArgb", state.customAccentArgb ?: 0)
            .putFloat("glassTransparency", state.glassTransparency)
            .putBoolean("dynamicMonet", state.dynamicMonet)
            .apply()
    }
}

data class ArashiVisualTokens(
    val isDark: Boolean,
    val style: UiStyleMode,
    val accent: Color,
    val background: Color,
    val surfaceCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val glassTransparency: Float
)

val LocalArashiTokens = staticCompositionLocalOf {
    ArashiVisualTokens(
        isDark = false,
        style = UiStyleMode.LIQUID_GLASS,
        accent = Color(0xFF0A84FF),
        background = Color(0xFFEEF3F8),
        surfaceCard = Color(0xCCFFFFFF),
        textPrimary = Color(0xFF121620),
        textSecondary = Color(0xFF5D6678),
        glassTransparency = 0.65f
    )
}

@Composable
fun ArashiPlayerTheme(
    themeState: ArashiThemeState,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeState.darkMode) {
        DarkModePref.LIGHT -> false
        DarkModePref.DARK -> true
        DarkModePref.SYSTEM -> systemDark
    }

    val chosenAccent = themeState.resolveAccent()
    val useDynamic = themeState.style == UiStyleMode.MATERIAL_YOU &&
        themeState.dynamicMonet &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && isDark -> dynamicDarkColorScheme(context)
        useDynamic && !isDark -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme(
            primary = chosenAccent,
            secondary = chosenAccent.copy(alpha = 0.75f),
            background = if (themeState.style == UiStyleMode.MATERIAL_YOU) Color(0xFF131613) else Color(0xFF0B0E14),
            surface = if (themeState.style == UiStyleMode.MATERIAL_YOU) Color(0xFF1E231E) else Color(0xFF151A24),
            onBackground = Color(0xFFF5F7FB),
            onSurface = Color(0xFFF5F7FB)
        )
        else -> lightColorScheme(
            primary = chosenAccent,
            secondary = chosenAccent.copy(alpha = 0.75f),
            background = if (themeState.style == UiStyleMode.MATERIAL_YOU) Color(0xFFF3F4EB) else Color(0xFFEAF0F6),
            surface = if (themeState.style == UiStyleMode.MATERIAL_YOU) Color(0xFFFFFFFF) else Color(0xFFF7FAFD),
            onBackground = Color(0xFF11151E),
            onSurface = Color(0xFF11151E)
        )
    }

    val tokens = ArashiVisualTokens(
        isDark = isDark,
        style = themeState.style,
        accent = if (useDynamic) colorScheme.primary else chosenAccent,
        background = colorScheme.background,
        surfaceCard = if (themeState.style == UiStyleMode.MATERIAL_YOU) {
            colorScheme.surfaceVariant
        } else if (isDark) {
            Color(0xFF1B2130)
        } else {
            Color.White
        },
        textPrimary = colorScheme.onBackground,
        textSecondary = colorScheme.onBackground.copy(alpha = 0.62f),
        glassTransparency = themeState.glassTransparency
    )

    CompositionLocalProvider(LocalArashiTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

@Composable
fun ArashiBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalArashiTokens.current
    val bgBrush = if (tokens.style == UiStyleMode.LIQUID_GLASS) {
        if (tokens.isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0B101B),
                    tokens.accent.copy(alpha = 0.14f),
                    Color(0xFF0E1320)
                ),
                start = Offset(0f, 0f),
                end = Offset(1000f, 1800f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF1F5FA),
                    tokens.accent.copy(alpha = 0.08f),
                    Color(0xFFE5ECF4)
                ),
                start = Offset(0f, 0f),
                end = Offset(1000f, 1800f)
            )
        }
    } else {
        Brush.verticalGradient(listOf(tokens.background, tokens.background))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        content()
    }
}

@Composable
fun Modifier.arashiSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 6.dp
): Modifier {
    val tokens = LocalArashiTokens.current
    return if (tokens.style == UiStyleMode.LIQUID_GLASS) {
        val opacity = (1.12f - tokens.glassTransparency * 0.55f).coerceIn(0.32f, 0.90f)
        val fillTop = if (tokens.isDark) Color.White.copy(alpha = opacity * 0.16f) else Color.White.copy(alpha = opacity)
        val fillBottom = if (tokens.isDark) Color.White.copy(alpha = opacity * 0.07f) else Color.White.copy(alpha = (opacity - 0.18f).coerceAtLeast(0.28f))
        val borderTop = if (tokens.isDark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.92f)
        val borderBottom = if (tokens.isDark) Color.White.copy(alpha = 0.08f) else Color(0x22000000)
        this
            .shadow(elevation, shape, ambientColor = Color(0x18000000), spotColor = Color(0x22000000))
            .clip(shape)
            .background(Brush.linearGradient(listOf(fillTop, fillBottom)))
            .border(1.dp, Brush.linearGradient(listOf(borderTop, borderBottom)), shape)
    } else {
        this
            .clip(shape)
            .background(tokens.surfaceCard)
    }
}
