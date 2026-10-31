package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlin.math.roundToInt
import org.koin.compose.koinInject

private val HeaderHeight = 44.dp
private val ViewerMargin = 12.dp

/**
 * Просмотрщик для платформ без окон ОС: панель поверх экрана, прижатая к его краям.
 * Пропорции подбираются под медиа, окно таскается за заголовок, а контент растянут
 * по окну и сам относительно него не двигается.
 */
@Composable
fun FallbackMediaViewer(
    request: MediaViewerRequest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Панель живёт в composition приложения, поэтому адрес разрешается прямо здесь.
    val resolver: MediaUrlResolver = koinInject()
    val resolvedUrl = if (request.file.isVideo()) {
        rememberResolvedUrl(resolver, request.file.absoluteUrl())
    } else {
        null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .padding(ViewerMargin)
    ) {
        val density = LocalDensity.current
        val maxWidth = with(density) { maxWidth.toPx() }
        val maxHeight = with(density) { maxHeight.toPx() }
        val header = with(density) { HeaderHeight.toPx() }

        var mediaSize by remember(request.file) { mutableStateOf(IntSize.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        var position by remember { mutableStateOf<Offset?>(null) }

        // Медиа догрузилось и сообщило натуральный размер — панель принимает его пропорции.
        LaunchedEffect(mediaSize) {
            if (mediaSize.width > 0 && mediaSize.height > 0) {
                size = fitted(mediaSize, maxWidth.toInt(), maxHeight.toInt(), header.roundToInt())
            }
        }

        val current = size.takeIf { it.width > 0 }
            ?: fitted(IntSize.Zero, maxWidth.toInt(), maxHeight.toInt(), header.roundToInt())
        val currentPosition = position ?: Offset(
            ((maxWidth - current.width) / 2f).coerceAtLeast(0f),
            ((maxHeight - current.height) / 2f).coerceAtLeast(0f)
        )

        Surface(
            modifier = Modifier
                .offset { currentPosition.round() }
                .size(
                    width = with(density) { current.width.toDp() },
                    height = with(density) { current.height.toDp() }
                )
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeaderHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(current) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                position = Offset(
                                    (currentPosition.x + dragAmount.x)
                                        .coerceIn(0f, (maxWidth - current.width).coerceAtLeast(0f)),
                                    (currentPosition.y + dragAmount.y)
                                        .coerceIn(0f, (maxHeight - current.height).coerceAtLeast(0f))
                                )
                            }
                        }
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = request.file.displayname.ifEmpty { request.file.name },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("✕") }
                }

                MediaViewerContent(
                    file = request.file,
                    modifier = Modifier.fillMaxSize(),
                    resolvedUrl = resolvedUrl,
                    onNaturalSize = { width, height -> mediaSize = IntSize(width, height) }
                )
            }
        }
    }
}

/** Клиентская область с медиа, вписанная в экран: натуральный размер плюс место под заголовок. */
private fun fitted(media: IntSize, maxWidth: Int, maxHeight: Int, header: Int): IntSize {
    if (maxWidth <= 0 || maxHeight <= 0) return IntSize.Zero
    val availableHeight = (maxHeight - header).coerceAtLeast(1)
    val ratio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height.toFloat()
    } else {
        16f / 9f
    }

    var width = if (media.width > 0) media.width.toFloat() else maxWidth * 0.8f
    var height = width / ratio
    if (height > availableHeight || width > maxWidth) {
        val scale = minOf(maxWidth / width, availableHeight / height)
        width *= scale
        height *= scale
    }
    return IntSize(
        width.roundToInt().coerceAtLeast(1),
        (height.roundToInt() + header).coerceAtLeast(1)
    )
}
