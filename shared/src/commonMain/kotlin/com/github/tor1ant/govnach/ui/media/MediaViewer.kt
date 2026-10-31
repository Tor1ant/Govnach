package com.github.tor1ant.govnach.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Просмотр медиа поста. Если платформа умеет показать файл сама (desktop — настоящее
 * окно ОС, которое возится оконным менеджером), вызывающая сторона ничего не стекает
 * поверх приложения; иначе рисуется внутриэкранная панель.
 *
 * [onDismiss] зовётся, когда просмотр закрыт — вручную в панели или крестиком окна ОС.
 */
@Composable
fun MediaViewer(
    request: MediaViewerRequest?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val handledByPlatform = MediaViewerHost(request = request, onClosed = onDismiss)
    if (request != null && !handledByPlatform) {
        FallbackMediaViewer(request = request, onDismiss = onDismiss, modifier = modifier)
    }
}
