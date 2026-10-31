package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.ui.theme.AppTheme
import com.github.tor1ant.govnach.ui.theme.ThemeState
import com.github.tor1ant.govnach.ui.theme.ThemeStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Логика переключателя темы: две темы, кнопка меняет их местами, выбор переживает
 * пересоздание состояния (то есть следующий запуск приложения).
 */
class ThemeStateTest {

    @Test
    fun togglesBetweenLightAndDark() {
        val state = ThemeState(initial = AppTheme.Light, store = InMemoryStore())

        assertFalse(state.isDark)
        state.toggle()
        assertTrue(state.isDark)
        state.toggle()
        assertFalse(state.isDark)
    }

    @Test
    fun toggleIsItsOwnInverse() {
        for (theme in AppTheme.entries) {
            assertEquals(theme, theme.toggled().toggled())
        }
    }

    @Test
    fun selectionIsSavedForNextLaunch() {
        val store = InMemoryStore()
        val state = ThemeState(initial = AppTheme.Light, store = store)

        state.toggle()

        assertEquals(AppTheme.Dark, store.saved)
        assertEquals(AppTheme.Dark, store.load())
        // Новая сессия берёт старт из сохранённого выбора.
        val next = ThemeState(initial = store.load() ?: AppTheme.Light, store = store)
        assertTrue(next.isDark)
    }

    @Test
    fun selectAppliesEvenWithoutStore() {
        val state = ThemeState(initial = AppTheme.Dark, store = InMemoryStore())

        state.select(AppTheme.Light)

        assertEquals(AppTheme.Light, state.theme)
    }

    private class InMemoryStore : ThemeStore {
        var saved: AppTheme? = null

        override fun load(): AppTheme? = saved

        override fun save(theme: AppTheme) {
            saved = theme
        }
    }
}

class ThemeStoreDefaultsTest {

    @Test
    fun emptyStoreMeansSystemTheme() {
        val store = object : ThemeStore {
            override fun load(): AppTheme? = null
        }

        assertNull(store.load())
    }
}
