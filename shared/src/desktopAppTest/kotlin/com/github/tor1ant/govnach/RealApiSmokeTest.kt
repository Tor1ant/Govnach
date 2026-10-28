package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.Client
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.openapitools.client.apis.DefaultApi
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Интеграционный прогон по реальному апи двача: доски → треды доски → посты треда.
 *
 * Тест обращается в сеть, поэтому по умолчанию пропускается. Включается так:
 * `GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest`
 */
class RealApiSmokeTest {

    private fun client(): Client = Client(
        DefaultApi(
            httpClientConfig = { config ->
                config.install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }
        )
    )

    @Test
    fun boardsThreadsPosts() {
        if (System.getenv("GOVNACH_API_TEST") != "1") return

        runBlocking {
            val client = client()

            val boards = client.getBoards()
            println("boards: ${boards.size}, first: /${boards.first().id}/ ${boards.first().name}")
            assertTrue(boards.isNotEmpty(), "апи должен вернуть доски")

            val threads = client.getThreads("b")
            println("threads on /b/: ${threads.size}")
            assertTrue(threads.isNotEmpty(), "catalog.json должен вернуть треды")

            val thread = threads.filter { (it.sticky ?: 0) == 0 && (it.closed ?: 0) == 0 }
                .minByOrNull { it.postsCount ?: Int.MAX_VALUE }
                ?: threads.first()

            val posts = client.getThreadPosts(thread.board, thread.num)
            println("thread /${thread.board}/${thread.num}/ posts=${thread.postsCount} loaded=${posts.size}")
            println("first post: ${posts.first().num} ${posts.first().subject} ${posts.first().date}")
            assertTrue(posts.isNotEmpty(), "тред должен вернуть посты")
            assertTrue(posts.first().num == thread.num, "первый пост — ОП-пост треда")

            val info = client.getThreadInfo(thread.board, thread.num)
            println("info: num=${info.num} posts=${info.posts} timestamp=${info.timestamp}")
            assertTrue(info.num == thread.num, "info должен вернуть номер треда")
            assertTrue(info.timestamp > 0, "info должен вернуть время создания треда")
        }
    }
}
