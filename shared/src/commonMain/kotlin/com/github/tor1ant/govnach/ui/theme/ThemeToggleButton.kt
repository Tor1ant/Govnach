package com.github.tor1ant.govnach.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Маленькая кнопка темы для шапки: показывает текущую тему (солнце / луна),
 * клик переключает её. Иконки рисуются на canvas — зависимость на material-icons
 * ради двух глифов не нужна.
 *
 * Вне [LocalThemeState] (отдельное окно ОС, превью) кнопки нет: переключать нечего.
 */
@Composable
fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val themeState = LocalThemeState.current ?: return

    val next = themeState.theme.toggled()
    val description = if (next.isDark) "Включить тёмную тему" else "Включить светлую тему"
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick = themeState::toggle,
        modifier = modifier
            .size(40.dp)
            .semantics { contentDescription = description },
        colors = IconButtonDefaults.iconButtonColors(contentColor = iconColor)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                if (themeState.isDark) drawSun(iconColor) else drawMoon(iconColor)
            }
        }
    }
}

/** Солнце: круг и восемь лучей. */
private fun DrawScope.drawSun(color: Color) {
    val stroke = size.minDimension * 0.1f
    val radius = size.minDimension * 0.22f

    drawCircle(color = color, radius = radius, style = Stroke(width = stroke))

    val inner = radius + stroke * 1.2f
    val outer = size.minDimension * 0.48f
    val center = Offset(size.width / 2f, size.height / 2f)
    repeat(8) { index ->
        val angle = index * (PI / 4).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
            end = Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
            strokeWidth = stroke
        )
    }
}

/** Луна: диск, из которого вычтен сдвинутый круг — получается серп. */
private fun DrawScope.drawMoon(color: Color) {
    val radius = size.minDimension * 0.42f
    val center = Offset(size.width / 2f, size.height / 2f)

    val disc = Path().apply { addOval(Rect(center = center, radius = radius)) }
    val bite = Path().apply {
        addOval(Rect(center = Offset(center.x + radius * 0.55f, center.y - radius * 0.45f), radius = radius * 0.88f))
    }

    // Вычитание путей, а не Clear-режим: полупрозрачная дырка в иконке не нужна.
    drawPath(
        path = Path().apply { op(disc, bite, PathOperation.Difference) },
        color = color
    )
}
