package com.github.tor1ant.govnach.client

import java.util.concurrent.Executors
import java.util.prefs.Preferences

/**
 * Что человек уже подтвердил на дваче.
 *
 * Сервер сам по себе не закрывает доски: браузер один раз показывает окно «вам 18 лет?»
 * и записывает `ageallow=1`. Это состояние живёт в браузере, а не в аккаунте, поэтому
 * приложение должно хранить его само — иначе каждый заход выглядит как первый.
 *
 * Набор намеренно маленький и явный: кладём только то, что относится к доступу к разделам,
 * и ничего личного. Значения берутся из ответов сервера, а не выдумываются.
 */
data class ServerAccess(
    /** Подтверждение возраста: его ставит сам двач после окна «18+». */
    val ageAllow: Boolean = false,

    /**
     * Ключ доступа, который двач выдаёт после отправки поста.
     *
     * Именно он открывает закрытые разделы (`/h/`, `/ho/`, `/e/`, `/nf/`, `/fet/` и др.):
     * без него каталог отвечает HTTP 500, с ним — обычным списком тредов. Проверено
     * на живом сервере: посторонний ключ не работает, выданный — работает.
     */
    val userCodeAuth: String? = null,

    /** Пароль для постинга: сервер ставит его после успешной отправки поста. */
    val passcodeAuth: String? = null,

    /** Код пользователя: сервер ставит его, когда пост принят. */
    val userCode: String? = null
) {

    /** Есть ли доступ к закрытым разделам. */
    val hasPostingKey: Boolean
        get() = !userCodeAuth.isNullOrBlank()

    /**
     * Строка `Cookie` для запроса. Пустая, если подтверждать нечего, — тогда заголовок
     * не отправляется вовсе, чтобы не выглядеть подозрительно для сервера.
     */
    fun cookieHeader(): String = buildList {
        if (ageAllow) add("ageallow=1")
        userCodeAuth?.takeIf { it.isNotBlank() }?.let { add("usercode_auth=$it") }
        passcodeAuth?.takeIf { it.isNotBlank() }?.let { add("passcode_auth=$it") }
        userCode?.takeIf { it.isNotBlank() }?.let { add("usercode=$it") }
    }.joinToString("; ")

    /**
     * Учесть ответ сервера: если он прислал `Set-Cookie`, запоминаем то, что относится
     * к доступу. Возвращает новое состояние или `null`, если запоминать нечего.
     */
    fun absorb(setCookieHeaders: List<String>): ServerAccess? {
        if (setCookieHeaders.isEmpty()) return null

        var next = this
        var changed = false
        for (header in setCookieHeaders) {
            val pair = header.substringBefore(';')
            val name = pair.substringBefore('=', "").trim().lowercase()
            val value = pair.substringAfter('=', "").trim()
            when (name) {
                "ageallow" -> if (!next.ageAllow && value.isNotEmpty() && value != "0") {
                    next = next.copy(ageAllow = true)
                    changed = true
                }

                // Главный ключ: двач выдаёт его после первого поста и дальше
                // открывает им закрытые разделы.
                "usercode_auth" -> if (value.isNotEmpty() && value != next.userCodeAuth) {
                    next = next.copy(userCodeAuth = value)
                    changed = true
                }

                "passcode_auth" -> if (value.isNotEmpty() && value != next.passcodeAuth) {
                    next = next.copy(passcodeAuth = value)
                    changed = true
                }

                "usercode" -> if (value.isNotEmpty() && value != next.userCode) {
                    next = next.copy(userCode = value)
                    changed = true
                }
            }
        }
        return if (changed) next else null
    }
}

/** Хранилище подтверждений между запусками. */
interface AccessStore {
    fun load(): ServerAccess

    fun save(access: ServerAccess) = Unit
}

/**
 * Настройки платформы: desktop и Android живут на JVM, поэтому хватает `java.util.prefs` —
 * тот же подход, что и у выбора темы. Ошибки хранилища не ломают приложение:
 * в худшем случае подтверждение придётся дать заново.
 */
object PreferencesAccessStore : AccessStore {

    private const val KEY_AGE = "access.ageallow"
    private const val KEY_USERCODE_AUTH = "access.usercodeauth"
    private const val KEY_PASSCODE = "access.passcode"
    private const val KEY_USERCODE = "access.usercode"

    private val prefs: Preferences? = try {
        Preferences.userRoot().node("com/github/tor1ant/govnach")
    } catch (e: Exception) {
        null
    }

    /** Запись редкая, но диск в кадре не нужен: пишем в отдельном потоке. */
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "govnach-access-save").apply { isDaemon = true }
    }

    override fun load(): ServerAccess = try {
        ServerAccess(
            ageAllow = prefs?.getBoolean(KEY_AGE, false) ?: false,
            userCodeAuth = prefs?.get(KEY_USERCODE_AUTH, null)?.takeIf { it.isNotEmpty() },
            passcodeAuth = prefs?.get(KEY_PASSCODE, null)?.takeIf { it.isNotEmpty() },
            userCode = prefs?.get(KEY_USERCODE, null)?.takeIf { it.isNotEmpty() }
        )
    } catch (e: Exception) {
        ServerAccess()
    }

    override fun save(access: ServerAccess) {
        val node = prefs ?: return
        try {
            writer.execute {
                try {
                    node.putBoolean(KEY_AGE, access.ageAllow)
                    putOrRemove(node, KEY_USERCODE_AUTH, access.userCodeAuth)
                    putOrRemove(node, KEY_PASSCODE, access.passcodeAuth)
                    putOrRemove(node, KEY_USERCODE, access.userCode)
                    node.flush()
                } catch (e: Exception) {
                    // Не сохранили — подтверждение проживёт до конца сессии.
                }
            }
        } catch (e: Exception) {
            // Исполнитель уже остановлен — состояние останется в рамках сессии.
        }
    }

    private fun putOrRemove(node: Preferences, key: String, value: String?) {
        if (value.isNullOrBlank()) node.remove(key) else node.put(key, value)
    }
}

/**
 * Текущие подтверждения плюс их запись в хранилище.
 *
 * Живёт одним экземпляром на приложение: клиент читает состояние перед запросом
 * и обновляет его после ответа, поэтому подтверждение, полученное при постинге,
 * сразу же действует на остальные запросы.
 */
class ServerAccessState(
    initial: ServerAccess = ServerAccess(),
    private val store: AccessStore = PreferencesAccessStore
) {

    @Volatile
    private var current: ServerAccess = initial

    /** Снимок подтверждений для текущего запроса. */
    fun snapshot(): ServerAccess = current

    /** Заголовок `Cookie` для запроса; пустая строка — заголовок не нужен. */
    fun cookieHeader(): String = current.cookieHeader()

    /** Запомнить то, что прислал сервер. Возвращает true, если состояние изменилось. */
    fun absorb(setCookieHeaders: List<String>): Boolean {
        val next = current.absorb(setCookieHeaders) ?: return false
        current = next
        store.save(next)
        return true
    }

    /** Отметить подтверждение возраста вручную (кнопка на экране ошибки). */
    fun allowAdult() {
        if (current.ageAllow) return
        val next = current.copy(ageAllow = true)
        current = next
        store.save(next)
    }

    /**
     * Сохранить ключ доступа к закрытым разделам.
     *
     * Ключ выдаёт сам двач: он приходит в `Set-Cookie` ответа на успешный пост. Плагин
     * ловит его автоматически, а этот метод нужен, когда ключ известен извне —
     * например, человек переносит его с другого устройства.
     */
    fun setUserCodeAuth(token: String) {
        val clean = token.trim()
        if (clean.isEmpty() || clean == current.userCodeAuth) return
        val next = current.copy(userCodeAuth = clean)
        current = next
        store.save(next)
    }
}