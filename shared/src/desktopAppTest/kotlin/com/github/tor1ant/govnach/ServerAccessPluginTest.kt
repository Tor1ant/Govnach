package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.ServerAccess
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
 * Сквозная проверка плагина: подтверждение доходит до второго запроса.
 *
 * Это тот случай, который раньше не работал: сервер отвечал `Set-Cookie`, ответ
 * принимался, а следующий запрос уходил без cookie — как от нового посетителя.
 */
class ServerAccessPluginTest {

    private class MemoryStore(var stored: ServerAccess = ServerAccess()) : AccessStore {
        override fun load(): ServerAccess = stored
        override fun save(access: ServerAccess) {
            stored = access
        }
    }

    private fun clientWith(state: ServerAccessState, engine: MockEngine) = HttpClient(engine) {
        // Порядок как в приложении: подтверждения ставятся до HttpCookies.
        install(serverAccessPlugin(state))
        install(HttpCookies)
    }

    @Test
    fun setCookieFromServerIsSentOnNextRequest() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)
        val seenCookies = mutableListOf<String?>()
        var request = 0

        val engine = MockEngine { req ->
            seenCookies += req.headers[HttpHeaders.Cookie]
            request++
            if (request == 1) {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf("ageallow=1; path=/")
                    )
                )
            } else {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        runBlocking {
            clientWith(state, engine).get("https://2ch.org/fet/catalog.json")
            clientWith(state, engine).get("https://2ch.org/fet/catalog.json")
        }

        assertTrue(store.stored.ageAllow, "подтверждение из Set-Cookie должно сохраниться")
        assertTrue(
            seenCookies[1]?.contains("ageallow=1") == true,
            "второй запрос должен нести cookie, пришло: ${seenCookies[1]}"
        )
    }

    @Test
    fun manualConfirmationIsSentFromTheVeryFirstRequest() {
        val state = ServerAccessState(store = MemoryStore())
        state.allowAdult()

        var seenCookie: String? = null
        val engine = MockEngine { req ->
            seenCookie = req.headers[HttpHeaders.Cookie]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        runBlocking { clientWith(state, engine).get("https://2ch.org/fet/catalog.json") }

        assertEquals("ageallow=1", seenCookie)
    }

    @Test
    fun noCookieHeaderWhenNothingConfirmed() {
        val state = ServerAccessState(store = MemoryStore())

        var sawHeader = true
        val engine = MockEngine { req ->
            sawHeader = req.headers[HttpHeaders.Cookie] != null
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        runBlocking { clientWith(state, engine).get("https://2ch.org/b/catalog.json") }

        assertEquals(false, sawHeader, "без подтверждений пустой заголовок не нужен")
    }
}