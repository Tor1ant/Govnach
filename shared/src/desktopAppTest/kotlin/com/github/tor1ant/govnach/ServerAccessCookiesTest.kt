package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.ServerAccess
import com.github.tor1ant.govnach.client.ServerAccessCookies
import com.github.tor1ant.govnach.client.ServerAccessState
import com.github.tor1ant.govnach.client.serverAccessPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ключ доступа должен уходить в каждом запросе, а не только в том, который его получил.
 *
 * Здесь проверяется то, что ломалось: `HttpCookies` на этапе отправки переписывает заголовок
 * `Cookie` своим хранилищем, и без [ServerAccessCookies] сохранённый ключ терялся — доска
 * отвечала 500, превью и видео не грузились.
 */
class ServerAccessCookiesTest {

    private class MemoryStore(var saved: ServerAccess = ServerAccess()) : AccessStore {
        override fun load() = saved

        override fun save(access: ServerAccess) {
            saved = access
        }
    }

    private fun client(state: ServerAccessState, engine: MockEngine) = HttpClient(engine) {
        install(serverAccessPlugin(state))
        install(HttpCookies) { storage = ServerAccessCookies(state) }
    }

    @Test
    fun savedKeyIsSentFromTheVeryFirstRequest() {
        val state = ServerAccessState(
            initial = ServerAccess(userCodeAuth = "KEY123"),
            store = MemoryStore()
        )

        val seen = mutableListOf<String?>()
        val engine = MockEngine { req ->
            seen += req.headers[HttpHeaders.Cookie]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        runBlocking {
            client(state, engine).get("https://2ch.hk/h/catalog.json")
            client(state, engine).get("https://2ch.hk/h/catalog.json")
        }

        assertTrue(seen[0]?.contains("usercode_auth=KEY123") == true, "ключ должен уйти сразу, пришло: ${seen[0]}")
        assertTrue(seen[1]?.contains("usercode_auth=KEY123") == true, "ключ должен уйти и дальше, пришло: ${seen[1]}")
    }

    /**
     * Двач отвечает на файлы редиректом `2ch.hk → 2ch.su`. Cookie привязана к хосту, поэтому
     * на новом хосте обычное хранилище пусто — и ключ пропадал ровно на медиа. Заголовок,
     * который ставит плагин, переживает редирект, потому что Ktor копирует его в новый запрос.
     */
    @Test
    fun keySurvivesRedirectToAnotherHost() {
        val state = ServerAccessState(
            initial = ServerAccess(userCodeAuth = "KEY123"),
            store = MemoryStore()
        )

        val cookiesSeen = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { req ->
            cookiesSeen += req.url.host to req.headers[HttpHeaders.Cookie]
            if (req.url.host == "2ch.hk") {
                respond(
                    content = "",
                    status = HttpStatusCode.MovedPermanently,
                    headers = headersOf(
                        HttpHeaders.Location to listOf("https://2ch.su/h/thumb/1/1s.jpg"),
                        HttpHeaders.ContentType to listOf("text/html")
                    )
                )
            } else {
                respond("bytes", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
        }

        runBlocking { client(state, engine).get("https://2ch.hk/h/thumb/1/1s.jpg") }

        assertEquals(2, cookiesSeen.size, "должно быть два запроса: исходный и по редиректу")
        assertTrue(
            cookiesSeen[1].second?.contains("usercode_auth=KEY123") == true,
            "после редиректа ключ должен остаться, пришло: ${cookiesSeen[1].second}"
        )
    }

    @Test
    fun noCookieHeaderWhenNothingConfirmed() {
        val state = ServerAccessState(store = MemoryStore())

        var sawHeader = true
        val engine = MockEngine { req ->
            sawHeader = req.headers[HttpHeaders.Cookie] != null
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        runBlocking { client(state, engine).get("https://2ch.hk/b/catalog.json") }

        assertEquals(false, sawHeader, "без подтверждений заголовок не нужен")
    }

    /**
     * Ключ доступа — фактически пароль от закрытых разделов, и двач уводит часть адресов
     * на чужие сайты (`/fur/catalog.json` → google.com). По такому редиректу он уходить не должен.
     */
    @Test
    fun keyIsNotSentToForeignHosts() {
        val state = ServerAccessState(
            initial = ServerAccess(userCodeAuth = "KEY123"),
            store = MemoryStore()
        )

        val cookiesSeen = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { req ->
            cookiesSeen += req.url.host to req.headers[HttpHeaders.Cookie]
            if (req.url.host == "2ch.hk") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location to listOf("https://google.com/"),
                        HttpHeaders.ContentType to listOf("text/html")
                    )
                )
            } else {
                respond("<html>ok</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
            }
        }

        runBlocking { client(state, engine).get("https://2ch.hk/fur/catalog.json") }

        assertEquals(2, cookiesSeen.size, "должно быть два запроса: исходный и по редиректу")
        assertTrue(
            cookiesSeen[0].second?.contains("usercode_auth=KEY123") == true,
            "самому двачу ключ уходит, пришло: ${cookiesSeen[0].second}"
        )
        assertEquals(
            null,
            cookiesSeen[1].second,
            "чужому хосту ключ отдавать нельзя, пришло: ${cookiesSeen[1].second}"
        )
    }
}