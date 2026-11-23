package com.arashiplayer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val DeepSpaceBg = Color(0xFF060812)
val GlassCyan = Color(0xFF64E8FF)
val GlassViolet = Color(0xFFA78BFA)
val GlassRose = Color(0xFFFF4D7D)
val TextPrimary = Color(0xFFF8FAFF)
val TextSecondary = Color(0xFFB0B9CC)

private val ArashiLiquidScheme = darkColorScheme(
    primary = GlassCyan,
    secondary = GlassViolet,
    tertiary = GlassRose,
    background = DeepSpaceBg,
    surface = Color(0xFF121626),
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ArashiPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArashiLiquidScheme,
        content = content
    )
}

/**
 * 液态玻璃（Liquid Glass）底层流体光斑背景
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x552299CC), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.12f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.85f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x446C4CE6), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.42f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.12f, size.height * 0.42f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x38FF376B), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.82f),
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.78f, size.height * 0.82f)
            )
        }
        content()
    }
}

/**
 * 液态玻璃质感修饰符：半透明折射填充 + 左上高光描边 + 柔光投影
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    alphaTop: Float = 0.17f,
    alphaBottom: Float = 0.06f,
    elevation: Dp = 10.dp
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color(0x40000000),
        spotColor = Color(0x55000000)
    )
    .clip(shape)
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = alphaTop),
                Color(0xFF9EC5FF).copy(alpha = (alphaTop + alphaBottom) / 2.2f),
                Color.White.copy(alpha = alphaBottom)
            ),
            start = Offset(0f, 0f),
            end = Offset(600f, 600f)
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.46f),
                Color.White.copy(alpha = 0.12f),
                Color(0xFF64E8FF).copy(alpha = 0.24f)
            )
        ),
        shape = shape
    )
