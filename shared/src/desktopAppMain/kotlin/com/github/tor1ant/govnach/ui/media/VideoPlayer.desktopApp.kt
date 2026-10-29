package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * В compose desktop нет video-рендеринга в swing-окне, поэтому видео открывается
 * системным плеером/браузером — браузер проигрывает и mp4, и webm.
 */
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("В compose desktop видео играет вне окна — в системном плеере или браузере.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = { DesktopMediaOpener.openExternal(url) }) { Text("Открыть видео") }
        Spacer(Modifier.height(8.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

actual fun platformMediaOpener(): PlatformMediaOpener = DesktopMediaOpener()

actual val mediaPlatformContext: PlatformUiContext
    @Composable
    get() = PlatformUiContext(Unit)

class DesktopMediaOpener : PlatformMediaOpener {
    override fun open(context: PlatformUiContext, url: String): Boolean = openExternal(url)

    companion object {
        fun openExternal(url: String): Boolean = try {
            // java.awt.Desktop не всегда доступен в headless/нетипичных окружениях — фолбэк на xdg-open/rundll32.
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
