package com.hash.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object HashColors {
    val Navy = Color(0xFF0B1020)
    val Panel = Color(0xFF121933)
    val PanelHigh = Color(0xFF1B2347)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Ink = Color(0xFFE8ECFF)
    val InkDim = Color(0xFF8D96BE)
    val Green = Color(0xFF34D399)
    val Red = Color(0xFFF87171)
    val PrivateBg = Color(0xFF15102A)
    val accent = Brush.linearGradient(listOf(Cyan, Violet))
}

private val Dark = darkColorScheme(
    primary = HashColors.Cyan,
    onPrimary = HashColors.Navy,
    secondary = HashColors.Violet,
    onSecondary = Color.White,
    background = HashColors.Navy,
    onBackground = HashColors.Ink,
    surface = HashColors.Panel,
    onSurface = HashColors.Ink,
    surfaceVariant = HashColors.PanelHigh,
    onSurfaceVariant = HashColors.InkDim,
    surfaceContainer = HashColors.Panel,
    surfaceContainerHigh = HashColors.PanelHigh,
    surfaceContainerLow = HashColors.Panel,
    outline = Color(0xFF2E3765),
    error = HashColors.Red,
)

private val Light = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color.White,
    secondary = HashColors.Violet,
    background = Color(0xFFF4F6FC),
    onBackground = Color(0xFF0B1020),
    surface = Color.White,
    onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFE6EAF6),
    onSurfaceVariant = Color(0xFF4B5578),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF0F9),
    surfaceContainerLow = Color.White,
    outline = Color(0xFFC9CFE3),
)

@Composable
fun HashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** The brand mark: a forward-leaning # with one gradient stroke. */
@Composable
fun HashMark(size: Dp, modifier: Modifier = Modifier, light: Color = HashColors.Ink) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 108f
        val w = 8.8f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(light, p(28.2f, 44f), p(76.2f, 44f), w, StrokeCap.Round)
        drawLine(light.copy(alpha = 0.85f), p(31.8f, 64f), p(79.8f, 64f), w, StrokeCap.Round)
        drawLine(light, p(68.6f, 26f), p(59.8f, 82f), w, StrokeCap.Round)
        drawLine(
            Brush.linearGradient(listOf(HashColors.Cyan, HashColors.Violet), p(48.2f, 26f), p(39.4f, 82f)),
            p(48.2f, 26f), p(39.4f, 82f), w, StrokeCap.Round,
        )
    }
}
