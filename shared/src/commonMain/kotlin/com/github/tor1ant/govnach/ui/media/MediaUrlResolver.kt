package com.github.tor1ant.govnach.ui.media

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.request
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Конечный адрес медиа. Двач отвечает 301 на другой хост (2ch.hk → 2ch.su), а нативный
 * плеер на Windows, получив редирект, перестаёт качать файл кусками и тянет его целиком
 * до первого кадра — отсюда задержка и рывки в начале. Здесь редирект разрешается один раз,
 * и плееру уходит уже прямой адрес.
 */
class MediaUrlResolver(
    private val http: HttpClient
) {
    private val mutex = Mutex()
    private val cache = HashMap<String, String>()

    suspend fun resolve(url: String): String {
        if (url.isEmpty()) return url
        mutex.withLock { cache[url] }?.let { return it }

        val resolved = try {
            // Нужны только заголовки: тело не читаем и сразу закрываем, иначе резолвер
            // сам скачает весь файл и задержит открытие на те же секунды, что и плеер.
            http.prepareGet(url).execute { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            url
        }

        mutex.withLock {
            if (cache.size > MAX_CACHED_URLS) cache.clear()
            cache[url] = resolved
        }
        return resolved
    }

    private companion object {
        const val MAX_CACHED_URLS = 200
    }
}