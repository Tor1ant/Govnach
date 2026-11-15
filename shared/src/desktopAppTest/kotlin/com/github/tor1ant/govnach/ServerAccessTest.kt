package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.ServerAccess
import com.github.tor1ant.govnach.client.ServerAccessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Подтверждения двача: что уходит в запросе и что запоминается из ответа.
 *
 * Это состояние раньше не сохранялось вообще — приложение не держало cookie между
 * запросами. Тесты фиксируют, что теперь оно и отправляется, и переживает перезапуск.
 */
class ServerAccessTest {

    private class MemoryStore(var stored: ServerAccess = ServerAccess()) : AccessStore {
        override fun load(): ServerAccess = stored
        override fun save(access: ServerAccess) {
            stored = access
        }
    }

    @Test
    fun nothingToSendByDefault() {
        assertEquals("", ServerAccess().cookieHeader(), "без подтверждений заголовок не нужен")
    }

    @Test
    fun ageAllowBecomesCookie() {
        assertEquals("ageallow=1", ServerAccess(ageAllow = true).cookieHeader())
    }

    @Test
    fun allConfirmationsGoTogether() {
        val header = ServerAccess(ageAllow = true, passcodeAuth = "abc", userCode = "def").cookieHeader()

        assertEquals("ageallow=1; passcode_auth=abc; usercode=def", header)
    }

    @Test
    fun blankValuesAreNotSent() {
        val header = ServerAccess(ageAllow = false, passcodeAuth = "  ", userCode = "").cookieHeader()

        assertEquals("", header, "пустые значения не должны попадать в заголовок")
    }

    @Test
    fun setCookieAgeAllowIsRemembered() {
        val next = ServerAccess().absorb(listOf("ageallow=1; expires=Wed, 01 Jan 2027 00:00:00 GMT; path=/"))

        assertTrue(next?.ageAllow == true, "подтверждение возраста должно запоминаться")
    }

    @Test
    fun setCookieWinsOverUnrelatedCookies() {
        val headers = listOf(
            "some_other=1; path=/",
            "passcode_auth=secret; path=/",
            "ageallow=1"
        )

        val next = ServerAccess().absorb(headers)

        assertTrue(next?.ageAllow == true)
        assertEquals("secret", next?.passcodeAuth)
    }

    @Test
    fun zeroAgeAllowDoesNotCount() {
        assertNull(ServerAccess().absorb(listOf("ageallow=0")), "0 — это отказ, а не подтверждение")
    }

    @Test
    fun emptySetCookieChangesNothing() {
        assertNull(ServerAccess().absorb(emptyList()))
        assertNull(ServerAccess(ageAllow = true).absorb(listOf("ageallow=1")), "уже подтверждено")
    }

    @Test
    fun statePersistsThroughStore() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)

        state.absorb(listOf("ageallow=1"))

        assertTrue(store.stored.ageAllow, "состояние должно уйти в хранилище")
        // Новый запуск приложения читает то же хранилище.
        assertEquals("ageallow=1", ServerAccessState(initial = store.load(), store = store).cookieHeader())
    }

    @Test
    fun allowAdultIsIdempotentAndSaved() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)

        state.allowAdult()
        state.allowAdult()

        assertTrue(state.snapshot().ageAllow)
        assertTrue(store.stored.ageAllow)
    }

    @Test
    fun absorbReportsWhetherSomethingChanged() {
        val state = ServerAccessState(store = MemoryStore())

        assertTrue(state.absorb(listOf("ageallow=1")), "первое подтверждение меняет состояние")
        assertFalse(state.absorb(listOf("ageallow=1")), "повторное — уже нет")
    }

    /**
     * `usercode_auth` — ключ, который двач выдаёт после первого поста и которым
     * открываются закрытые разделы. Без него каталог `/h/`, `/fet/` отвечает HTTP 500.
     */
    @Test
    fun userCodeAuthIsRememberedAndSaved() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)

        state.absorb(listOf("usercode_auth=SECRET123; expires=Wed, 01 Jan 2027 00:00:00 GMT; path=/"))

        assertEquals("SECRET123", state.snapshot().userCodeAuth)
        assertEquals("SECRET123", store.stored.userCodeAuth, "ключ должен попасть в хранилище")
        assertTrue(state.snapshot().hasPostingKey)
    }

    @Test
    fun userCodeAuthGoesIntoCookieHeader() {
        val header = ServerAccess(ageAllow = true, userCodeAuth = "SECRET123").cookieHeader()

        assertTrue(header.contains("usercode_auth=SECRET123"), "ключ должен уходить в запросах: $header")
    }

    @Test
    fun manualTokenIsStoredAndTrimmed() {
        val store = MemoryStore()
        val state = ServerAccessState(store = store)

        state.setUserCodeAuth("  SECRET123  ")

        assertEquals("SECRET123", state.snapshot().userCodeAuth)
        assertEquals("SECRET123", store.stored.userCodeAuth)
    }

    @Test
    fun blankTokenIsIgnored() {
        val state = ServerAccessState(store = MemoryStore())

        state.setUserCodeAuth("   ")

        assertFalse(state.snapshot().hasPostingKey, "пустой ключ не должен сохраняться")
    }

    @Test
    fun accessKeySurvivesRestart() {
        val store = MemoryStore()
        ServerAccessState(store = store).setUserCodeAuth("SECRET123")

        val restarted = ServerAccessState(initial = store.load(), store = store)

        assertTrue(restarted.snapshot().hasPostingKey, "ключ должен пережить перезапуск")
        assertTrue(restarted.cookieHeader().contains("usercode_auth=SECRET123"))
    }
}