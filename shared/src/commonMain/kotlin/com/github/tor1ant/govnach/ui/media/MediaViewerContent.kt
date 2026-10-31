package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.openapitools.client.models.FileModel

/**
 * Содержимое окна просмотра, растянутое на всё окно. Геометрию окна контент не диктует:
 * натуральный размер уходит наверх колбэком, а размер самого окна остаётся за пользователем.
 * Картинка сама по себе не таскается — двигает её только зум, и не дальше своих краёв.
 *
 * [resolvedUrl] — адрес видео уже без редиректа. Разрешает его вызывающая сторона: у окна ОС
 * свой composition, и доступа к DI из него нет.
 */
@Composable
fun MediaViewerContent(
    file: FileModel,
    modifier: Modifier = Modifier,
    resolvedUrl: String? = null,
    onNaturalSize: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            file.isVideo() -> VideoPlayer(
                url = resolvedUrl ?: file.absoluteUrl(),
                modifier = Modifier.fillMaxSize(),
                onVideoSize = onNaturalSize
            )

            else -> ViewerImage(
                url = MediaPaths.src(file),
                modifier = Modifier.fillMaxSize(),
                onNaturalSize = onNaturalSize
            )
        }
    }
}

/**
 * Картинка во весь кадр: колесо/пинч — зум, перетаскивание — панорама в пределах картинки,
 * двойной клик — 1:1 с пикселем под курсором и обратно. Натуральный размер отдаётся наверх,
 * чтобы окно подогнало свои пропорции под картинку, а не показывало её в чёрных полях.
 */
@Composable
private fun ViewerImage(
    url: String,
    modifier: Modifier = Modifier,
    onNaturalSize: (Int, Int) -> Unit
) {
    val loader: MediaImageLoader = koinInject()
    val bitmap by rememberImage(loader, url)
    val current = bitmap

    var scale by remember(url) { mutableFloatStateOf(1f) }
    var pan by remember(url) { mutableStateOf(Offset.Zero) }

    val reportSize by rememberUpdatedState(onNaturalSize)
    val contentScale by rememberUpdatedState(scale)
    val contentPan by rememberUpdatedState(pan)

    LaunchedEffect(current) {
        current?.let { reportSize(it.width, it.height) }
        scale = 1f
        pan = Offset.Zero
    }

    Box(
        modifier = modifier
            .pointerInput(url, current) {
                val frame = current ?: return@pointerInput
                awaitEachGesture {
                    // Кадр окна нужен в лямбдах ниже, где `this` уже не PointerInputScope.
                    val canvas = size
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break

                        val zoom = event.calculateZoom()
                        val next = (contentScale * zoom).coerceIn(MinViewerScale, MaxViewerScale)
                        if (zoom != 1f && next != contentScale) {
                            // Держим точку под пальцами/курсором на месте, пока масштаб меняется.
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(canvas.width / 2f, canvas.height / 2f)
                            val factor = next / contentScale
                            pan = centroid - center - (centroid - center - contentPan) * factor
                        }
                        val drag = event.calculatePan()
                        if (drag != Offset.Zero) {
                            // Перетаскивание — только при увеличенной картинке.
                            if (next > 1f) pan += drag
                        }
                        scale = next
                        pan = clampPan(pan, fitOf(frame, canvas), next)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .pointerInput(url, current) {
                val frame = current ?: return@pointerInput
                val canvas = size
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val next = if (contentScale > 1f) 1f else DoubleTapZoom
                        pan = if (next == 1f) {
                            Offset.Zero
                        } else {
                            clampPan(
                                Offset(canvas.width / 2f, canvas.height / 2f) - tap,
                                fitOf(frame, canvas),
                                next
                            )
                        }
                        scale = next
                    }
                )
            }
    ) {
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        }
    }
}

/** Битмап по url: кэш [MediaImageLoader] переиспользуется и в отдельном окне. */
@Composable
private fun rememberImage(loader: MediaImageLoader, url: String): State<ImageBitmap?> =
    produceState(initialValue = null as ImageBitmap?, key1 = url) {
        value = loader.peek(url) ?: loader.load(url)
    }

/**
 * Адрес видео без редиректа: двач отвечает 301 на другой хост, а плеер на Windows, получив
 * редирект, качает файл целиком до первого кадра — отсюда задержка и рывки в начале.
 * Пока адрес разрешается, плеер стоит на исходном: если редиректа нет, ролик играет сразу.
 */
@Composable
fun rememberResolvedUrl(resolver: MediaUrlResolver, url: String): String {
    val resolved by produceState(initialValue = url, key1 = url) {
        value = resolver.resolve(url)
    }
    return resolved
}

private const val MinViewerScale = 1f
private const val DoubleTapZoom = 2.5f
private const val MaxViewerScale = 8f

/**
 * Размер картинки после вписывания в кадр: именно его нужно держать внутри окна,
 * а не размер самого окна — иначе у картинки с другими пропорциями будут чёрные поля.
 */
private fun fitOf(bitmap: ImageBitmap, viewport: androidx.compose.ui.unit.IntSize): androidx.compose.ui.geometry.Size {
    if (viewport.width <= 0 || viewport.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
        return androidx.compose.ui.geometry.Size.Zero
    }
    val factor = minOf(
        viewport.width.toFloat() / bitmap.width,
        viewport.height.toFloat() / bitmap.height
    )
    return androidx.compose.ui.geometry.Size(bitmap.width * factor, bitmap.height * factor)
}

/** Панорама не выводит картинку за край: при масштабе 1 она всегда стоит по центру. */
private fun clampPan(pan: Offset, fitted: androidx.compose.ui.geometry.Size, scale: Float): Offset {
    if (fitted.width <= 0f || fitted.height <= 0f) return Offset.Zero
    val maxX = (fitted.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (fitted.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

