package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.ServerAccess
import com.github.tor1ant.govnach.client.ServerAccessState
import com.github.tor1ant.govnach.client.serverAccessPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Пост → ключ доступа → закрытые доски.
 *
 * Это тот путь, ради которого всё делалось: человек один раз постит из приложения,
 * сервер в ответе присылает `usercode_auth`, и после этого закрытые разделы
 * открываются сами, без ручного ввода ключа.
 */
class PostingUnlocksBoardsTest {

    private class MemoryStore(var saved: ServerAccess = ServerAccess()) : AccessStore {
        override fun load() = saved
        override fun save(access: ServerAccess) {
            saved = access
        }
    }

    private fun client(state: ServerAccessState, engine: MockEngine) = HttpClient(engine) {
        install(serverAccessPlugin(state))
        install(HttpCookies)
    }

    @Test
    fun postResponseKeyIsPickedUpAutomatically() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)
        val cookiesSeen = mutableListOf<String?>()
        var reqNo = 0

        val engine = MockEngine { req ->
            reqNo++
            cookiesSeen += req.headers[HttpHeaders.Cookie]
            if (reqNo == 1) {
                // Ответ на пост: сервер выдаёт ключ доступа.
                respond(
                    content = """{"result":1,"num":123}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf(
                            "usercode_auth=L09w2QbRdwGN2waPVEMTVGq7bJbF32vFUG138K52AOM; " +
                                "expires=Thu, 15 Oct 2026 11:36:36 GMT; Max-Age=31536000; path=/; secure; HttpOnly",
                            "ageallow=1; path=/"
                        )
                    )
                )
            } else {
                respond(
                    content = """{"threads":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        runBlocking {
            val c = client(state, engine)
            // 1. постим
            c.post("https://2ch.org/user/posting")
            // 2. идём на закрытую доску
            c.post("https://2ch.org/fet/catalog.json")
        }

        // Ключ подхвачен без участия человека.
        assertEquals(
            "L09w2QbRdwGN2waPVEMTVGq7bJbF32vFUG138K52AOM",
            state.snapshot().userCodeAuth,
            "ключ из ответа на пост должен сохраниться автоматически"
        )
        assertTrue(store.saved.hasPostingKey, "ключ должен попасть в постоянное хранилище")
        assertFalse(
            cookiesSeen[0]?.contains("usercode_auth") == true,
            "до поста ключа быть не должно"
        )
        assertTrue(
            cookiesSeen[1]?.contains("usercode_auth=") == true,
            "после поста запрос должен нести ключ, пришло: ${cookiesSeen[1]}"
        )
        assertTrue(
            cookiesSeen[1]?.contains("ageallow=1") == true,
            "подтверждение возраста тоже должно уйти: ${cookiesSeen[1]}"
        )
    }

    @Test
    fun keySurvivesFullRestart() {
        val store = MemoryStore()
        // Сессия 1: пост выдал ключ.
        runBlocking {
            val engine = MockEngine {
                respond(
                    "", HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf("usercode_auth=KEY_FROM_POST; path=/")
                    )
                )
            }
            client(ServerAccessState(store = store), engine).post("https://2ch.org/user/posting")
        }

        // Сессия 2: приложение запустилось заново, ключ читается из хранилища.
        val restarted = ServerAccessState(initial = store.load(), store = store)
        var seen: String? = null
        runBlocking {
            val engine = MockEngine { req ->
                seen = req.headers[HttpHeaders.Cookie]
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            client(restarted, engine).post("https://2ch.org/fet/catalog.json")
        }

        assertTrue(seen?.contains("usercode_auth=KEY_FROM_POST") == true, "ключ должен уйти: $seen")
    }
}