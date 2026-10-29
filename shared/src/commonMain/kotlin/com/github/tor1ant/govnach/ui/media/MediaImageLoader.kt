package com.github.tor1ant.govnach.ui.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Загрузка картинок двача в ImageBitmap. Держит LRU-кэш декодированных битмапов и
 * дедуплицирует параллельные запросы одного url; сами байты кэширует okhttp-движок (see AppModule).
 */
class MediaImageLoader(
    private val http: HttpClient
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private val inflight = HashMap<String, Deferred<ImageBitmap?>>()

    /** Кэш без запроса в сеть: используется внутри стейта загрузки. */
    suspend fun peek(url: String): ImageBitmap? = mutex.withLock { cache[url] }

    suspend fun load(url: String): ImageBitmap? = coroutineScope {
        val job: Deferred<ImageBitmap?> = mutex.withLock {
            inflight[url]?.takeUnless { it.isCancelled } ?: async { fetch(url) }.also { inflight[url] = it }
        }

        val bitmap = job.await()

        mutex.withLock {
            inflight.remove(url, job)
            if (bitmap != null) {
                cache.putIfAbsent(url, bitmap)
                if (cache.size > MAX_CACHED_IMAGES) cache.remove(cache.keys.first())
            }
            bitmap
        }
    }

    private suspend fun fetch(url: String): ImageBitmap? = try {
        val response = http.get(url)
        if (response.status.value !in 200..299) null
        else {
            val bytes: ByteArray = response.bodyAsChannel().readRemaining().readBytes()
            bytes.decodeToImageBitmap()
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_CACHED_IMAGES = 140
    }
}
