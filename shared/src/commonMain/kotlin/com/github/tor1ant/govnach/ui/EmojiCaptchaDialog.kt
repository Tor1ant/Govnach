package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.EmojiCaptcha
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Решение emoji капчи двача. Двач показывает полоску с несколькими emoji, а под ней — клавиатуру
 * вариантов; клик по подходящему варианту подтверждает очередной emoji, клавиатура при этом
 * обновляется. После последнего emoji апи отдаёт id, который и уходит в пост.
 *
 * Поэтому здесь важен порядок: [picks] — сколько emoji уже выбрано.
 */
@Composable
fun EmojiCaptchaDialog(
    captcha: EmojiCaptcha,
    picks: Int,
    isBusy: Boolean,
    error: String?,
    onEmojiClick: (Int) -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Капча двача") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (picks == 0) {
                        "Выберите emoji с картинки по порядку — слева направо."
                    } else {
                        "Выбрано emoji: $picks. Продолжайте по порядку."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                captcha.imageBase64?.toImageBitmap()?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = "Какие emoji нужно выбрать",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(image.width.toFloat() / image.height.toFloat())
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                if (captcha.keyboardBase64.isNotEmpty()) {
                    Text(
                        text = "Варианты:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(captcha.keyboardBase64) { index, base64 ->
                            EmojiKey(
                                base64 = base64,
                                enabled = !isBusy,
                                onClick = { onEmojiClick(index) }
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !isBusy) {
                Text("Другая капча")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isBusy) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun EmojiKey(base64: String, enabled: Boolean, onClick: () -> Unit) {
    val bitmap = base64.toImageBitmap() ?: return

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Вариант emoji",
            modifier = Modifier.size(44.dp)
        )
    }
}

/**
 * Двач отдаёт PNG в base64. Декодируем мягко: кривая строка просто не покажет картинку,
 * вместо того чтобы уронить диалог.
 */
@OptIn(ExperimentalEncodingApi::class)
private fun String.toImageBitmap(): ImageBitmap? = try {
    Base64.decode(this).decodeToImageBitmap()
} catch (e: IllegalArgumentException) {
    null
}
