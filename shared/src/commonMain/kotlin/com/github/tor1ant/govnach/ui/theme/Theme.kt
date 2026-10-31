package com.github.tor1ant.govnach.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Классическая светлая палитра: тёплая бумажная подложка, тёмный текст,
 * приглушённый кирпичный акцент — как у старых досок, без ярких заливок.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8C3B22),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E3D8),
    onPrimaryContainer = Color(0xFF4A1D0E),
    secondary = Color(0xFF6B5B4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE4DA),
    onSecondaryContainer = Color(0xFF3A302A),
    tertiary = Color(0xFF4E6151),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF2B2723),
    surface = Color(0xFFFDFBF7),
    onSurface = Color(0xFF2B2723),
    surfaceVariant = Color(0xFFEDE6DC),
    onSurfaceVariant = Color(0xFF6A6158),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F2),
    surfaceContainer = Color(0xFFF5F1E9),
    surfaceContainerHigh = Color(0xFFEFEAE1),
    surfaceContainerHighest = Color(0xFFE9E3D9),
    outline = Color(0xFFB3A897),
    outlineVariant = Color(0xFFDCD4C7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF32302C),
    inverseOnSurface = Color(0xFFF4F0E9),
    inversePrimary = Color(0xFFE8B69F),
    scrim = Color(0xFF000000)
)

/**
 * Тёмная палитра: тот же тёплый характер, но с графитовой подложкой.
 * Акцент светлее, чтобы читаться на тёмном фоне.
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE2A184),
    onPrimary = Color(0xFF45200F),
    primaryContainer = Color(0xFF5C2A18),
    onPrimaryContainer = Color(0xFFF7D8C8),
    secondary = Color(0xFFC9B8A6),
    onSecondary = Color(0xFF352C24),
    secondaryContainer = Color(0xFF453B31),
    onSecondaryContainer = Color(0xFFE9DFD3),
    tertiary = Color(0xFFA8BCA9),
    onTertiary = Color(0xFF1F3324),
    background = Color(0xFF161513),
    onBackground = Color(0xFFE8E2D9),
    surface = Color(0xFF1C1B18),
    onSurface = Color(0xFFE8E2D9),
    surfaceVariant = Color(0xFF3A352E),
    onSurfaceVariant = Color(0xFFB8AFA2),
    surfaceContainerLowest = Color(0xFF100F0E),
    surfaceContainerLow = Color(0xFF1A1917),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF292723),
    surfaceContainerHighest = Color(0xFF332F2A),
    outline = Color(0xFF7E7568),
    outlineVariant = Color(0xFF4A443C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Color(0xFFE8E2D9),
    inverseOnSurface = Color(0xFF1C1B18),
    inversePrimary = Color(0xFF8C3B22),
    scrim = Color(0xFF000000)
)

/**
 * Типографика без внешних шрифтов: системный шрифт платформы, чуть плотнее дефолтного,
 * чтобы текст постов читался в ленте.
 */
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
)

/** Скругления: карточки и панели в одном характере, без «таблеток». */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Корень оформления: цвета, типографика и формы плюс доступ к переключателю темы
 * через [LocalThemeState]. Все экраны читают цвета только отсюда.
 */
@Composable
fun GovnachTheme(
    themeState: ThemeState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(
            colorScheme = if (themeState.isDark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
