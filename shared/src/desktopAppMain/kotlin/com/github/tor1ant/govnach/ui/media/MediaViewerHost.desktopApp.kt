package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import org.koin.compose.koinInject
import org.openapitools.client.models.FileModel
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.roundToInt

actual fun platformMediaOpener(): PlatformMediaOpener = DesktopMediaOpener()

actual val mediaPlatformContext: PlatformUiContext
    @Composable
    get() = PlatformUiContext(Unit)

/**
 * Desktop-реализация открытия медиа: картинки и видео идут во встроенное окно,
 * музыка и прочие файлы — системному обработчику.
 */
class DesktopMediaOpener : PlatformMediaOpener {
    override fun open(context: PlatformUiContext, url: String): Boolean = openExternal(url)

    override fun openFile(
        context: PlatformUiContext,
        file: FileModel,
        url: String,
        pageUrl: String
    ): Boolean = when (file.kind()) {
        // Встроенное окно уже показывает файл — браузер поверх него только мешал бы.
        FileKind.Image, FileKind.Video, FileKind.Sticker -> true
        FileKind.Audio, FileKind.Other -> openExternal(url) || openExternal(pageUrl)
    }

    companion object {
        fun openExternal(url: String): Boolean = try {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            true
        } catch (e: Exception) {
            try {
                val command = when {
                    System.getProperty("os.name").lowercase().contains("win") ->
                        listOf("rundll32", "url.dll,FileProtocolHandler", url)
                    else -> listOf("xdg-open", url)
                }
                ProcessBuilder(command).start()
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}

/**
 * Встроенный просмотрщик desktop — настоящее окно ОС, поэтому его возит оконный
 * менеджер: оно свободно ездит по мониторам, разворачивается и тянется за любую
 * сторону. Возвращает true, если платформа умеет показать файл сама.
 */
@Composable
actual fun MediaViewerHost(request: MediaViewerRequest?, onClosed: () -> Unit): Boolean {
    if (request == null) return false

    // У окна ОС свой composition: DI виден только здесь, в composition приложения.
    val resolver: MediaUrlResolver = koinInject()
    val videoUrl = if (request.file.isVideo()) request.file.absoluteUrl() else null
    val resolvedUrl = videoUrl?.let { rememberResolvedUrl(resolver, it) }

    val state = rememberWindowState(
        position = WindowPosition.PlatformDefault,
        size = DpSize(InitialSize.width.dp, InitialSize.height.dp)
    )
    MediaViewerWindow(
        request = request,
        state = state,
        resolvedUrl = resolvedUrl,
        onClosed = onClosed
    )
    return true
}

/**
 * Окно просмотра. Живёт своей жизнью: повторный клик по медиа перерисовывает его
 * на месте, закрывает пользователь. Пока он не потянул рамку сам, клиентская область
 * подгоняется под натуральный размер кадра — ни полей, ни обрезки.
 *
 * [onClosed] вызывается, когда человек закрыл окно крестиком. Без этого вызова
 * вызывающая сторона считала бы файл открытым, а окно осталось бы невидимым —
 * повторно открыть медиа было бы невозможно.
 */
@Composable
private fun MediaViewerWindow(
    request: MediaViewerRequest,
    state: WindowState,
    resolvedUrl: String?,
    onClosed: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    // Размеры, которые задали мы или система при показе окна. Любое другое изменение
    // размера — действие пользователя, после которого автоподгонка не вмешивается.
    val ownSizes = remember { mutableSetOf<String>() }
    var pendingFit by remember { mutableStateOf(true) }

    Window(
        onCloseRequest = {
            // Окно закрывают, но пока его только прячут: показывать заново то же самое
            // — плохой опыт, поэтому файл убирается из просмотра, плеер освобождается,
            // и следующее открытие начнёт с чистого окна.
            open = false
            onClosed()
        },
        state = state,
        visible = open,
        title = request.file.displayname.ifEmpty { request.file.name },
        resizable = true
    ) {
        val window = window

        DisposableEffect(window) {
            val listener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    val key = "${window.width}x${window.height}"
                    if (key !in ownSizes) {
                        // Рамку потянул человек: размер дальше за ним.
                        pendingFit = false
                    }
                }
            }
            window.addComponentListener(listener)
            onDispose { window.removeComponentListener(listener) }
        }

        LaunchedEffect(request.file) {
            pendingFit = true
            // Окно для нового файла получает размер заново — прежние пометки не нужны.
            ownSizes.clear()
            ownSizes += "${window.width}x${window.height}"
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MediaViewerContent(
                file = request.file,
                modifier = Modifier.fillMaxSize(),
                resolvedUrl = resolvedUrl,
                onNaturalSize = { width, height ->
                    if (pendingFit && width > 0 && height > 0) {
                        val target = fittedClientSize(
                            mediaWidth = width,
                            mediaHeight = height,
                            area = window.graphicsConfiguration?.bounds
                                ?.let { Dimension(it.width, it.height) }
                        )
                        if (window.width != target.width || window.height != target.height) {
                            val insets = window.insets
                            ownSizes +=
                                "${target.width + insets.left + insets.right}x${target.height + insets.top + insets.bottom}"
                            window.setClientSize(target)
                        }
                    }
                }
            )
        }
    }
}

/** Размер клиентской области под пропорции медиа, вписанный в рабочую область экрана. */
private fun fittedClientSize(mediaWidth: Int, mediaHeight: Int, area: Dimension?): Dimension {
    val maxWidth = ((area?.width ?: FallbackScreenWidth) * ScreenFraction).roundToInt()
    val maxHeight = ((area?.height ?: FallbackScreenHeight) * ScreenFraction).roundToInt()
    val factor = minOf(
        1f,
        maxWidth.toFloat() / mediaWidth,
        maxHeight.toFloat() / mediaHeight
    )
    return Dimension(
        (mediaWidth * factor).roundToInt().coerceAtLeast(MinWindowWidthPx),
        (mediaHeight * factor).roundToInt().coerceAtLeast(MinWindowHeightPx)
    )
}

/** setSize на AWT-окне меряет окно вместе с рамкой, поэтому рамку вычитаем. */
private fun java.awt.Window.setClientSize(client: Dimension) {
    val frame = insets
    setSize(
        client.width + frame.left + frame.right,
        client.height + frame.top + frame.bottom
    )
    validate()
}

private val InitialSize = Dimension(960, 720)
private const val ScreenFraction = 0.9f
private const val FallbackScreenWidth = 1920
private const val FallbackScreenHeight = 1080
private const val MinWindowWidthPx = 320
private const val MinWindowHeightPx = 240
