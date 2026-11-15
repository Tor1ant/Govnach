package com.github.tor1ant.govnach.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Строка поиска.
 *
 * Поле контролируемое: текстом владеет вызывающая сторона, компонент только рисует
 * и сообщает об изменениях. Дебаунса здесь нет — он живёт в [SearchQuery] вьюмодели,
 * чтобы ввод и отрисовка не ждали таймер.
 *
 * Enter отправляет запрос сразу ([onSubmit]), крестик очищает ([onClear]),
 * [isSearching] крутит спиннер справа: это про данные, а не про ввод.
 *
 * [onClose] включает режим «строка поиска в шапке»: слева появляется стрелка назад,
 * которая сворачивает поиск и возвращает обычную шапку экрана.
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Поиск",
    isSearching: Boolean = false,
    onSubmit: () -> Unit = {},
    onClear: () -> Unit = {},
    autoFocus: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Экран поиска открывают, чтобы сразу печатать: фокус ставим сами.
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        leadingIcon = {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    BackGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SearchGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingIcon = {
            when {
                isSearching -> Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                query.isNotEmpty() -> IconButton(onClick = onClear) {
                    ClearGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSubmit()
                focusManager.clearFocus()
            }
        )
    )
}

/** Лупа для шапки экрана: открывает поиск. */
@Composable
fun SearchActionButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        SearchGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Кнопка консоли для шапки главной: три полоски, как у значка терминала.
 * Иконки не тянем — рисуем сами, как и остальные глифы.
 */
@Composable
fun ConsoleActionButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        ConsoleGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Значок консоли: три горизонтальные полоски текста в рамке. */
@Composable
internal fun ConsoleGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = size.minDimension * 0.1f
        val left = size.width * 0.14f
        val right = size.width * 0.86f

        // Рамка терминала.
        drawRoundRect(
            color = color,
            topLeft = Offset(left, size.height * 0.14f),
            size = androidx.compose.ui.geometry.Size(right - left, size.height * 0.72f),
            cornerRadius = CornerRadius(size.minDimension * 0.12f),
            style = Stroke(stroke)
        )
        // Строки «вывода»: первая короче, вторая длиннее — как обычный лог.
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.38f),
            end = Offset(size.width * 0.5f, size.height * 0.38f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.6f),
            end = Offset(size.width * 0.72f, size.height * 0.6f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Лупа: окружность и ручка. Иконки не тянем — dependency на material-icons не добавляем. */
@Composable
internal fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = size.minDimension * 0.12f
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)

        drawCircle(color = color, radius = radius, center = center, style = Stroke(stroke))
        drawLine(
            color = color,
            start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Стрелка «назад»: сворачивает поиск и возвращает обычную шапку экрана. */
@Composable
internal fun BackGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = size.minDimension * 0.14f

        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.5f),
            end = Offset(size.width * 0.84f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.5f),
            end = Offset(size.width * 0.46f, size.height * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.5f),
            end = Offset(size.width * 0.46f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Крестик очистки строки поиска. */@Composable
internal fun ClearGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = size.minDimension * 0.14f
        val inset = size.minDimension * 0.22f

        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
