package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.isAdult
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.openapitools.client.apis.DefaultApi

/**
 * Живая проверка того, что взрослые доски читаются так же, как обычные: список досок,
 * треды, посты и выдача emoji капчи. Постинг не делаем — тест не должен оставлять следов на дваче.
 *
 * Включается так: `GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest --tests "*AdultBoardsApiTest*"`.
 */
class AdultBoardsApiTest {

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
    fun adultBoardsAreReadableAndPostable() {
        if (System.getenv("GOVNACH_API_TEST") != "1") return

        runBlocking {
            val client = client()

            val boards = client.getBoards()
            val adultBoards = boards.filter { it.isAdult() }
            println("adult boards: ${adultBoards.joinToString { "/${it.id}/" }}")
            assertTrue(adultBoards.isNotEmpty(), "в апи должна быть хотя бы одна доска категории «Взрослым»")

            val board = adultBoards.first()

            // Чтение идёт так же, как на обычной доске: отдельной капчи или пасскода для каталога нет.
            val threads = client.getThreads(board.id)
            println("/${board.id}/ threads: ${threads.size}")
            assertTrue(threads.isNotEmpty(), "треды взрослой доски должны быть доступны")

            val thread = threads.first { (it.closed ?: 0) == 0 }
            val posts = client.getThreadPosts(board.id, thread.num)
            println("/${board.id}/${thread.num} posts: ${posts.size}")
            assertTrue(posts.isNotEmpty(), "посты взрослой доски должны быть доступны")

            // Что нужно для постинга: доска разрешает пост и выдаёт капчу под пост.
            assertTrue(board.enablePosting, "/${board.id}/ должна разрешать постинг")
            val captcha = client.requestEmojiCaptcha(board.id, thread.num)
            println("/${board.id}/${thread.num} captcha id: ${captcha.id.take(24)}…")
            assertTrue(captcha.id.isNotBlank(), "капча для взрослой доски должна выдаваться")

            // Флаг nsfw у файлов заполняется не везде: на взрослую доску UI размывает по категории.
            val filesWithFlag = posts.flatMap { it.files.orEmpty() }.count { it.nsfw != null }
            println("/${board.id}/${thread.num} files: ${posts.flatMap { it.files.orEmpty() }.size}, with nsfw flag: $filesWithFlag")
        }
    }
}
