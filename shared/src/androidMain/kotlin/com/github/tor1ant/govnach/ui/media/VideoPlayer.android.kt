package com.github.tor1ant.govnach.ui.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Inline-плеер: ExoPlayer + PlayerView со штатными контролами. Видео двача (mp4/webm)
 * играет без дополнительных зависимостей, кроме media3-exoplayer.
 */
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view -> view.player = player }
    )
}

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
