package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import io.github.kdroidfilter.composemediaplayer.CacheConfig
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.createVideoPlayerState
import kotlinx.coroutines.delay

/**
 * Видеоплеер треда. Рендерится внутри Compose: на Android кадры идут через media3,
 * на desktop — через нативный бэкенд платформы (Media Foundation / AVPlayer / GStreamer).
 * Кэш включён, чтобы повторный просмотр того же файла не скачивался заново.
 *
 * [active] — окно видно на экране: пока false, поток не открывается, чтобы окно,
 * уехавшее за край, не продолжало тянуть сеть и декодировать видео.
 * [onVideoSize] сообщает натуральный размер видео, как только он известен,
 * по нему окно просмотра подбирает свои пропорции.
 */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onVideoSize: ((Int, Int) -> Unit)? = null
) {
    // Освобождение берём на себя: библиотечный rememberVideoPlayerState освобождает плеер
    // мгновенно, а если в этот момент ещё идёт открытие файла, нативный слой на Windows падает.
    // Поэтому createVideoPlayerState + отсроченное освобождение через [VideoPlayerReaper].
    val playerState = remember { createVideoPlayerState(cacheConfig = PlayerCache) }
    DisposableEffect(playerState) {
        onDispose { VideoPlayerReaper.reap(playerState) }
    }

    LaunchedEffect(url, active) {
        if (active) {
            playerState.openUri(url)
        } else {
            playerState.pause()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }

    // Метаданные — платформенный тип: на Android он приходит nullable, на desktop — нет.
    val videoWidth = playerState.metadata.width ?: 0
    val videoHeight = playerState.metadata.height ?: 0
    LaunchedEffect(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            onVideoSize?.invoke(videoWidth, videoHeight)
        }
    }

    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        VideoPlayerSurface(
            playerState = playerState,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            overlay = {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Тап по видео показывает/скрывает контролы, как в обычном плеере.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { controlsVisible = !controlsVisible }
                            }
                    )

                    if (playerState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }

                    playerState.error?.let { error ->
                        Text(
                            text = errorMessage(error),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }

                    if (controlsVisible) {
                        PlayerControls(
                            playerState = playerState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PlayerControls(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        val showVolume = maxWidth > 420.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(
                description = if (playerState.isPlaying) "Пауза" else "Играть",
                onClick = {
                    if (playerState.isPlaying) playerState.pause() else playerState.play()
                }
            ) {
                if (playerState.isPlaying) drawPauseIcon() else drawPlayIcon()
            }

            Text(
                text = playerState.positionText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )

            Slider(
                value = playerState.sliderPos,
                onValueChange = { playerState.seekStart(it) },
                onValueChangeFinished = { playerState.seekFinished() },
                valueRange = 0f..1000f,
                enabled = playerState.duration > 0.0,
                colors = playerSliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Text(
                text = playerState.durationText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )

            if (showVolume) {
                Spacer(Modifier.width(4.dp))
                PlayerIconButton(
                    description = if (playerState.volume > 0f) "Выключить звук" else "Включить звук",
                    onClick = { playerState.volume = if (playerState.volume > 0f) 0f else 1f }
                ) {
                    if (playerState.volume > 0f) drawVolumeIcon() else drawMutedIcon()
                }
                Slider(
                    value = playerState.volume,
                    onValueChange = { playerState.volume = it },
                    valueRange = 0f..1f,
                    colors = playerSliderColors(),
                    modifier = Modifier.width(72.dp)
                )
            }

            PlayerIconButton(
                description = if (playerState.isFullscreen) "Выйти из полного экрана" else "На весь экран",
                onClick = { playerState.toggleFullscreen() }
            ) {
                drawFullscreenIcon()
            }
        }
    }
}

@Composable
private fun playerSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Color.White,
    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    disabledThumbColor = Color.White.copy(alpha = 0.4f),
    disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
    disabledInactiveTrackColor = Color.White.copy(alpha = 0.15f)
)

@Composable
private fun PlayerIconButton(
    description: String,
    onClick: () -> Unit,
    draw: DrawScope.() -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Canvas(modifier = Modifier.size(20.dp)) { draw() }
    }
}

private fun DrawScope.drawPlayIcon() {
    val path = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.15f)
        lineTo(size.width * 0.85f, size.height * 0.5f)
        lineTo(size.width * 0.28f, size.height * 0.85f)
        close()
    }
    drawPath(path, color = Color.White)
}

private fun DrawScope.drawPauseIcon() {
    val barWidth = size.width * 0.22f
    drawRect(
        color = Color.White,
        topLeft = Offset(size.width * 0.25f, size.height * 0.18f),
        size = Size(barWidth, size.height * 0.64f)
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(size.width * 0.55f, size.height * 0.18f),
        size = Size(barWidth, size.height * 0.64f)
    )
}

private fun DrawScope.drawVolumeIcon() {
    val body = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.38f)
        lineTo(size.width * 0.34f, size.height * 0.38f)
        lineTo(size.width * 0.55f, size.height * 0.2f)
        lineTo(size.width * 0.55f, size.height * 0.8f)
        lineTo(size.width * 0.34f, size.height * 0.62f)
        lineTo(size.width * 0.12f, size.height * 0.62f)
        close()
    }
    drawPath(body, color = Color.White)
    drawArc(
        color = Color.White,
        startAngle = -50f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(size.width * 0.4f, size.height * 0.3f),
        size = Size(size.width * 0.42f, size.height * 0.4f),
        style = Stroke(width = size.width * 0.09f)
    )
}

private fun DrawScope.drawMutedIcon() {
    val body = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.38f)
        lineTo(size.width * 0.34f, size.height * 0.38f)
        lineTo(size.width * 0.55f, size.height * 0.2f)
        lineTo(size.width * 0.55f, size.height * 0.8f)
        lineTo(size.width * 0.34f, size.height * 0.62f)
        lineTo(size.width * 0.12f, size.height * 0.62f)
        close()
    }
    drawPath(body, color = Color.White)
    val stroke = size.width * 0.09f
    drawLine(
        color = Color.White,
        start = Offset(size.width * 0.66f, size.height * 0.36f),
        end = Offset(size.width * 0.92f, size.height * 0.64f),
        strokeWidth = stroke
    )
    drawLine(
        color = Color.White,
        start = Offset(size.width * 0.92f, size.height * 0.36f),
        end = Offset(size.width * 0.66f, size.height * 0.64f),
        strokeWidth = stroke
    )
}

private fun DrawScope.drawFullscreenIcon() {
    val stroke = size.width * 0.09f
    val inset = size.width * 0.14f
    val arm = size.width * 0.28f
    val top = inset
    val bottom = size.height - inset
    val left = inset
    val right = size.width - inset

    // Четыре уголка: вход в полный экран и выход из него рисуются одинаково.
    drawLine(Color.White, Offset(left, top), Offset(left + arm, top), stroke)
    drawLine(Color.White, Offset(left, top), Offset(left, top + arm), stroke)
    drawLine(Color.White, Offset(right, top), Offset(right - arm, top), stroke)
    drawLine(Color.White, Offset(right, top), Offset(right, top + arm), stroke)
    drawLine(Color.White, Offset(left, bottom), Offset(left + arm, bottom), stroke)
    drawLine(Color.White, Offset(left, bottom), Offset(left, bottom - arm), stroke)
    drawLine(Color.White, Offset(right, bottom), Offset(right - arm, bottom), stroke)
    drawLine(Color.White, Offset(right, bottom), Offset(right, bottom - arm), stroke)
}

private fun errorMessage(error: VideoPlayerError): String = when (error) {
    is VideoPlayerError.CodecError -> "Кодек не поддерживается: ${error.message}"
    is VideoPlayerError.NetworkError -> "Ошибка сети при загрузке видео: ${error.message}"
    is VideoPlayerError.SourceError -> "Видео недоступно: ${error.message}"
    is VideoPlayerError.UnknownError -> "Не удалось воспроизвести видео: ${error.message}"
}

private const val CONTROLS_HIDE_DELAY_MS = 3000L

/** Кэш скачанных роликов: повторный просмотр того же файла не тянет его заново. */
private val PlayerCache = CacheConfig(
    enabled = true,
    maxCacheSizeBytes = 200L * 1024L * 1024L
)
