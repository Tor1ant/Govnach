package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.isAdultContent
import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import org.koin.compose.koinInject
import org.openapitools.client.models.FileModel

/**
 * Асинхронная картинка по url. null bitmap при ошибке рисует пустую подложку,
 * чтобы пост не прыгал по высоте: размеры известны из метаданных файла.
 */
@Composable
fun AsyncMediaImage(
    url: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val loader: MediaImageLoader = koinInject()
    val bitmap by loader.observe(url)

    Box(
        modifier = modifier.then(aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = url.substringAfterLast('/'),
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * Загрузка картинки в compose-стейт. Держится на url, кэш loaders переиспользует битмапы.
 */
@Composable
private fun MediaImageLoader.observe(url: String): State<ImageBitmap?> = produceState(
    initialValue = null as ImageBitmap?,
    key1 = url
) {
    value = peek(url) ?: load(url)
}

/**
 * Превью файла. Взрослый ли контент, решает [isAdultContent]: флаг `nsfw` от двача,
 * а если его нет — признак взрослой доски ([adultBoard]). Взрослое превью размыто до тапа.
 */
@Composable
fun FileThumb(
    file: FileModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxThumbWidth: Float = 250f,
    maxThumbHeight: Float = 180f,
    adultBoard: Boolean = false
) {
    val adult = isAdultContent(file.nsfw, adultBoard)
    var revealed by remember(file, adult) { mutableStateOf(!adult) }

    val tnUrl = MediaPaths.thumbnail(file)
    val tnWidth = file.tnWidth.takeIf { it > 0 } ?: file.width
    val tnHeight = file.tnHeight.takeIf { it > 0 } ?: file.height
    val (boxWidth, boxHeight) = thumbBoxSize(tnWidth, tnHeight, maxThumbWidth, maxThumbHeight)

    Box(
        modifier = modifier
            .width(boxWidth.dp)
            .height(boxHeight.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { if (!revealed) revealed = true else onClick() },
                    onLongPress = { if (revealed) onClick() }
                )
            }
    ) {
        AsyncMediaImage(
            url = tnUrl,
            aspectRatio = aspectRatioOf(tnWidth, tnHeight),
            modifier = Modifier
                .fillMaxSize()
                .then(if (revealed) Modifier else Modifier.blur(18.dp)),
            contentScale = ContentScale.Fit
        )

        if (!revealed) {
            Text(
                text = "🔞 nsfw",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun FileMetaLine(file: FileModel, extra: String? = null) {
    Text(
        text = buildString {
            append(file.displayname.ifEmpty { file.name }.htmlToPlainText())
            append(" · ")
            append(formatSizeKb(file.propertySize))
            append(" · ")
            append(file.width)
            append("×")
            append(file.height)
            extra?.let { append(" · "); append(it) }
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Превью файла в посте: размеры известны из метаданных, поэтому лента не прыгает
 * по высоте, пока картинка догружается.
 */
private fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

/** Box для превью: нативный размер превью, вписанный в максимум, с сохранением пропорций. */
private fun thumbBoxSize(width: Int, height: Int, maxWidth: Float, maxHeight: Float): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return maxWidth.toInt() to maxHeight.toInt()
    val scale = minOf(1f, maxWidth / width, maxHeight / height)
    return (width * scale).toInt().coerceAtLeast(24) to (height * scale).toInt().coerceAtLeast(24)
}

fun formatSizeKb(sizeKb: Int): String =
    if (sizeKb >= 1024) String.format("%.1f МБ", sizeKb / 1024.0) else "$sizeKb КБ"
