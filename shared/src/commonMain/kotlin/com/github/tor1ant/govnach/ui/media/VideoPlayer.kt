package com.github.tor1ant.govnach.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Видеоплеер треда. На android играет inline через media3, на desktop отдаёт ссылку
 * системному плееру: нормального video-рендеринга в swing-окне compose desktop нет.
 */
@Composable
expect fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier
)

/** Оpaque-обёртка над android Context, чтобы commonMain не знал про android. */
class PlatformUiContext(val value: Any)

/**
 * Открывает медиа вне приложения (audio/sticker/other на обеих платформах, video на desktop).
 * Context для intent прокидывается из composable вызывающей стороны.
 */
interface PlatformMediaOpener {
    fun open(context: PlatformUiContext, url: String): Boolean
}

/** Фабрика для DI: AppModule собирает opener платформы без platform-классов в commonMain. */
expect fun platformMediaOpener(): PlatformMediaOpener

/** Context, из которого android запускает intent; на desktop — пустышка. */
expect val mediaPlatformContext: PlatformUiContext
