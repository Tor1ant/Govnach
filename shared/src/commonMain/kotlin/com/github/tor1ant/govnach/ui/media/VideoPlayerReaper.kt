package com.github.tor1ant.govnach.ui.media

import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Освобождение плеера с оглядкой на его состояние.
 *
 * Нативный слой Windows роняет процесс, когда `dispose()` приходит во время открытия файла
 * (`nOpenMedia`): освобождение сносит ресурсы, которые открытие ещё использует. Поэтому
 * уходящий плеер сначала доводит открытие до конца и только потом освобождается.
 *
 * Ждём ограниченное время: если сеть не ответила, освобождение всё равно происходит —
 * иначе плеер, его декодер и сеть остались бы жить до конца процесса.
 */
internal object VideoPlayerReaper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun reap(state: VideoPlayerState) {
        scope.launch {
            waitUntilOpenSettles(state)
            runCatching { state.stop() }
            runCatching { state.dispose() }
        }
    }

    /**
     * Открытие завершено, когда оно либо принесло медиа, либо закончилось ошибкой.
     * Ориентир на результат, а не на `isLoading`: между вызовом `openUri` и моментом,
     * когда библиотека выставит свой флаг, есть окно, и освобождать в нём нельзя.
     */
    private suspend fun waitUntilOpenSettles(state: VideoPlayerState) {
        var waited = 0L
        while (waited < OpenSettleTimeoutMs) {
            val settled = !state.isLoading && (state.hasMedia || state.error != null)
            if (settled) return
            delay(PollIntervalMs)
            waited += PollIntervalMs
        }
    }

    private const val PollIntervalMs = 50L
    private const val OpenSettleTimeoutMs = 15_000L
}