package com.github.tor1ant.govnach.ui.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android-реализация открытия медиа вне приложения: аудио, стикеры и прочие файлы
 * отдаются системному обработчику intent'ов.
 */
class AndroidMediaOpener : PlatformMediaOpener {
    override fun open(context: PlatformUiContext, url: String): Boolean = try {
        startViewIntent(context.value as Context, url)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun platformMediaOpener(): PlatformMediaOpener = AndroidMediaOpener()

actual val mediaPlatformContext: PlatformUiContext
    @Composable
    get() = PlatformUiContext(LocalContext.current)

private fun startViewIntent(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
