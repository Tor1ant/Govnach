package com.github.tor1ant.govnach.ui.media

import androidx.compose.runtime.Composable

/**
 * Desktop-реализация открытия медиа вне приложения: прочие файлы отдаются
 * системному обработчику, видео теперь играет внутри окна.
 */
actual fun platformMediaOpener(): PlatformMediaOpener = DesktopMediaOpener()

actual val mediaPlatformContext: PlatformUiContext
    @Composable
    get() = PlatformUiContext(Unit)

class DesktopMediaOpener : PlatformMediaOpener {
    override fun open(context: PlatformUiContext, url: String): Boolean = openExternal(url)

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
