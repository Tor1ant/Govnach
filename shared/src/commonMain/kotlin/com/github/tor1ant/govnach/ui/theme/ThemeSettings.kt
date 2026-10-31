package com.github.tor1ant.govnach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.Executors
import java.util.prefs.Preferences

/** Оформление приложения: светлое или тёмное. Третьего состояния нет — кнопка в шапке их переключает. */
enum class AppTheme(val isDark: Boolean) {
    Light(isDark = false),
    Dark(isDark = true);

    fun toggled(): AppTheme = if (this == Light) Dark else Light
}

/**
 * Кому принадлежит выбор темы. Без [ThemeState] в composition (отдельное окно ОС, превью)
 * тема берётся из системной, а кнопка переключения там не показывается.
 */
val LocalThemeState = staticCompositionLocalOf<ThemeState?> { null }

/**
 * Выбранная тема. Живёт в одном composition приложения, поэтому одного экземпляра хватает
 * на все экраны; выбор запоминается между запусками, а до первого выбора тема следует
 * за системной.
 */
class ThemeState(
    initial: AppTheme,
    private val store: ThemeStore
) {

    var theme by mutableStateOf(initial)
        private set

    val isDark: Boolean
        get() = theme.isDark

    fun toggle() {
        select(theme.toggled())
    }

    fun select(value: AppTheme) {
        theme = value
        // Выбор применяется сразу, запись в хранилище — уже после отрисовки кадра.
        store.save(value)
    }
}

/** Хранилище выбора темы. null — человек ещё ничего не выбирал, тогда тема идёт из системы. */
interface ThemeStore {
    fun load(): AppTheme?

    /** Сохранить выбор. По умолчанию ничего не делает: хранилище может быть только для чтения. */
    fun save(theme: AppTheme) = Unit
}

/**
 * Настройки платформы. Desktop и Android живут на JVM, поэтому хватает java.util.prefs.
 * Ошибки хранилища не ломают приложение: остаётся системная тема или выбор на сессию.
 */
object PreferencesThemeStore : ThemeStore {

    private const val KEY = "ui.theme"

    private val prefs: Preferences? = try {
        Preferences.userRoot().node("com/github/tor1ant/govnach")
    } catch (e: Exception) {
        null
    }

    /** Запись — редкое действие, но диск в кадре не нужен: пишем в отдельном потоке. */
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "govnach-theme-save").apply { isDaemon = true }
    }

    override fun load(): AppTheme? = try {
        prefs?.get(KEY, null)?.let { saved ->
            AppTheme.entries.firstOrNull { it.name == saved }
        }
    } catch (e: Exception) {
        null
    }

    override fun save(theme: AppTheme) {
        val node = prefs ?: return
        try {
            writer.execute {
                try {
                    node.put(KEY, theme.name)
                    node.flush()
                } catch (e: Exception) {
                    // Не сумели сохранить — тема всё равно переключилась до конца сессии.
                }
            }
        } catch (e: Exception) {
            // Исполнитель уже остановлен — выбор останется в рамках текущей сессии.
        }
    }
}

/**
 * Тема по умолчанию: сохранённый выбор, а если его не было — системная.
 * [ThemeState] при этом не знает, откуда взялось начальное значение.
 */
@Composable
fun rememberThemeState(
    store: ThemeStore = PreferencesThemeStore
): ThemeState {
    val saved = remember(store) { store.load() }
    val system = systemTheme()
    return remember(store) { ThemeState(initial = saved ?: system, store = store) }
}

@Composable
private fun systemTheme(): AppTheme = if (isSystemInDarkTheme()) AppTheme.Dark else AppTheme.Light
