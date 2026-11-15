package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.ServerAccess
import com.github.tor1ant.govnach.client.ServerAccessCookies
import com.github.tor1ant.govnach.client.ServerAccessState
import com.github.tor1ant.govnach.client.serverAccessPlugin
import com.github.tor1ant.govnach.ui.media.MediaSourceResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import org.openapitools.client.models.CatalogThreads
import org.openapitools.client.models.FileModel
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Живая проверка: контент закрытых разделов грузится так же, как на обычных досках.
 *
 * Проверяются оба места, где контент отваливался: превью и файлы (их тянет медиа-клиент)
 * и посты треда (их тянет клиент апи). Ключ доступа берётся из `REPRO_KEY`.
 *
 * Включается так: `REPRO_KEY=<ключ> GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest`.
 */
class LockedBoardContentApiTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private class MemoryStore(var saved: ServerAccess) : AccessStore {
        override fun load() = saved

        override fun save(access: ServerAccess) {
            saved = access
        }
    }

    private fun mediaClient(state: ServerAccessState) = HttpClient(OkHttp) {
        install(serverAccessPlugin(state))
        install(HttpCookies) { storage = ServerAccessCookies(state) }
    }

    @Test
    fun lockedBoardContentLoads() {
        if (System.getenv("GOVNACH_API_TEST") != "1") return
        val key = System.getenv("REPRO_KEY").orEmpty()
        if (key.isBlank()) return

        val state = ServerAccessState(initial = ServerAccess(userCodeAuth = key), store = MemoryStore(ServerAccess()))

        runBlocking {
            mediaClient(state).use { http ->
                for (board in LOCKED_BOARDS) {
                    val catalog = http.get("https://2ch.hk/$board/catalog.json")
                    assertTrue(
                        catalog.status.value in 200..299,
                        "/$board/catalog.json должен открываться с ключом, пришло ${catalog.status.value}"
                    )

                    val threads = json.decodeFromString<CatalogThreads>(catalog.bodyText()).threads.orEmpty()
                    val thread = threads.firstOrNull { !it.files.isNullOrEmpty() }
                    if (thread == null) {
                        println("/$board/: нет треда с файлами, пропуск")
                        continue
                    }

                    // Посты треда: их грузит клиент апи, здесь проверяется сам адрес.
                    val posts = http.get("https://2ch.hk/api/mobile/v2/after/$board/${thread.num}/${thread.num}")
                    assertTrue(posts.status.value in 200..299, "посты /$board/${thread.num} должны отдаваться")

                    // Медиа: именно это не грузилось — превью отвечало 500 «Not found».
                    // 404 значит, что файл удалён с сервера, и это не наша проблема:
                    // проверяем, что доступ больше не отбивается кодом 500.
                    val file: FileModel = thread.files!!.first()
                    val thumb = http.get("https://2ch.hk${file.thumbnail}")
                    assertTrue(
                        thumb.status.value != 500,
                        "превью ${file.thumbnail} не должно отбиваться 500 (итоговый адрес ${thumb.request.url})"
                    )
                    println("/$board/${thread.num}: catalog=${catalog.status.value} posts=${posts.status.value} thumb=${thumb.status.value}")
                }
            }

            // Видео закрытого раздела: плеер заголовков не умеет, поэтому источник
            // подменяется на локальную копию — проверяем, что плееру достаётся файл.
            val resolver = MediaSourceResolver(
                http = mediaClient(state),
                anonymous = HttpClient(OkHttp)
            )
            val video = "https://2ch.hk/h/src/138241/15551831739220.webm"
            val playable = resolver.playable(video)
            println("/h/ video playable: $playable")
            assertTrue(playable != video, "видео закрытой доски должно играться из локальной копии")
            assertTrue(java.io.File(playable).let { it.isFile && it.length() > 0 }, "локальная копия не должна быть пустой")
        }
    }

    private suspend fun io.ktor.client.statement.HttpResponse.bodyText(): String =
        String(bodyAsChannel().readRemaining().readBytes())

    private companion object {
        /** Разделы, каталог которых без ключа отвечает 500. */
        val LOCKED_BOARDS = listOf("h", "ho", "e", "nf", "fet")
    }
}