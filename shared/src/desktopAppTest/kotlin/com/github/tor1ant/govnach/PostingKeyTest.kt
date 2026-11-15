package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.ServerAccess
import com.github.tor1ant.govnach.client.ServerAccessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ключ доступа к закрытым разделам: то, что приходит в ответе на пост.
 *
 * Двач выдаёт `usercode_auth` после успешной отправки поста. Проверяем разбор реальных
 * вариантов заголовка `Set-Cookie`, которые он присылает: с атрибутами, в кавычках,
 * и среди посторонних cookie.
 */
class PostingKeyTest {

    private class MemoryStore : com.github.tor1ant.govnach.client.AccessStore {
        var saved: ServerAccess = ServerAccess()
        override fun load() = saved
        override fun save(access: ServerAccess) {
            saved = access
        }
    }

    @Test
    fun realSetCookieFormIsParsed() {
        val headers = listOf(
            "usercode_auth=L09w2QbRdwGN2waPVEMTVGq7bJbF32vFUG138K52AOM; " +
                "expires=Thu, 15 Oct 2026 11:36:36 GMT; Max-Age=31536000; path=/; secure; HttpOnly"
        )

        val next = ServerAccess().absorb(headers)

        assertEquals("L09w2QbRdwGN2waPVEMTVGq7bJbF32vFUG138K52AOM", next?.userCodeAuth)
        assertTrue(next?.hasPostingKey == true)
    }

    @Test
    fun keyAmongOtherCookiesAndOrderDoesNotMatter() {
        val headers = listOf(
            "ageallow=1; path=/",
            "usercode_auth=KEY_ABC; path=/",
            "some_analytics=xyz"
        )

        val next = ServerAccess().absorb(headers)

        assertEquals("KEY_ABC", next?.userCodeAuth)
        assertTrue(next?.ageAllow == true, "остальные подтверждения тоже должны сохраниться")
    }

    @Test
    fun keyIsSentTogetherWithAgeAllow() {
        val header = ServerAccess(ageAllow = true, userCodeAuth = "KEY_ABC").cookieHeader()

        assertTrue(header.contains("usercode_auth=KEY_ABC"), header)
        assertTrue(header.contains("ageallow=1"), header)
    }

    @Test
    fun postingKeyEndsUpInStore() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)

        state.absorb(listOf("usercode_auth=KEY_ABC; path=/"))

        assertEquals("KEY_ABC", store.saved.userCodeAuth)
    }

    @Test
    fun emptyKeyDoesNotOverwriteExisting() {
        val state = ServerAccessState(store = MemoryStore())
        state.setUserCodeAuth("REAL_KEY")

        state.absorb(listOf("usercode_auth=; path=/"))

        assertEquals("REAL_KEY", state.snapshot().userCodeAuth, "пустое значение не должно затирать ключ")
    }
}