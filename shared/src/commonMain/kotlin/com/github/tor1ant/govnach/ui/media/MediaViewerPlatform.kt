package com.github.tor1ant.govnach.ui.media

import androidx.compose.runtime.Composable
import org.openapitools.client.models.FileModel

/** Opaque-обёртка над android Context, чтобы commonMain не знал про android. */
class PlatformUiContext(val value: Any)

/**
 * Открывает медиа вне окна приложения. Desktop показывает картинки и видео в собственном
 * окне ОС, Android — отдаёт файл системному приложению через intent.
 */
interface PlatformMediaOpener {
    fun open(context: PlatformUiContext, url: String): Boolean

    /**
     * Открыть файл поста. [url] — сам файл, [pageUrl] — страница треда на случай,
     * если показывать файл встроенно нечем и остаётся браузер.
     */
    fun openFile(
        context: PlatformUiContext,
        file: FileModel,
        url: String,
        pageUrl: String
    ): Boolean = open(context, url)
}

/** Фабрика для DI: AppModule собирает opener платформы без platform-классов в commonMain. */
expect fun platformMediaOpener(): PlatformMediaOpener

/** Context, из которого android запускает intent; на desktop — пустышка. */
expect val mediaPlatformContext: PlatformUiContext

/**
 * Показывает файл встроенно средствами платформы: на desktop это отдельное окно ОС, которое
 * возится оконным менеджером — его можно растягивать и переносить между мониторами.
 * Возвращает false, если платформа сама файл не показывает и нужен fallback.
 * [onClosed] платформа вызывает, когда человек закрыл просмотрщик сам.
 */
@Composable
expect fun MediaViewerHost(request: MediaViewerRequest?, onClosed: () -> Unit = {}): Boolean

/** Файл, или идущий в просмотрщике, плюс страница треда — для «открыть в браузере». */
data class MediaViewerRequest(val file: FileModel, val pageUrl: String = "")

/** Абсолютный адрес файла: метаданные поста иногда дают относительный путь. */
fun FileModel.absoluteUrl(): String = when {
    path.isEmpty() -> ""
    path.startsWith("http://") || path.startsWith("https://") -> path
    else -> MediaPaths.BASE_URL + path
}
