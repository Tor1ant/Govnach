package com.github.tor1ant.govnach.client

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url

/**
 * Хранилище cookie, которое отдаёт сохранённые подтверждения для адресов двача.
 *
 * Зачем не хватает `serverAccessPlugin`: тот кладёт заголовок `Cookie` в запрос, но
 * `HttpCookies` на этапе отправки переписывает этот заголовок своим хранилищем — и если
 * для адреса в хранилище ничего нет, заголовок **удаляется**. Для картинок это и был баг:
 * двач отвечает на `/h/thumb/...` редиректом `2ch.hk → 2ch.su`, на новом хосте в хранилище
 * пусто, ключ пропадал, и сервер отдавал 500 «Not found». Видео не работало по той же причине:
 * его открывает нативный плеер, который заголовков не принимает вовсе.
 *
 * Хосты ограничены намеренно. Ключ доступа — это фактически пароль от закрытых разделов,
 * а двач отдаёт на часть адресов редирект на чужие сайты (`/fur/catalog.json` уводит
 * на google.com). Без проверки хоста ключ ушёл бы по этому редиректу третьей стороне,
 * поэтому подтверждения отдаются только самому двачу и его рабочему зеркалу.
 */
class ServerAccessCookies(
    private val state: ServerAccessState,
    /** Хосты, которым можно показывать ключ. */
    private val trustedHosts: Set<String> = DEFAULT_TRUSTED_HOSTS
) : CookiesStorage {

    override suspend fun get(requestUrl: Url): List<Cookie> =
        if (requestUrl.host in trustedHosts) state.snapshot().cookies() else emptyList()

    /**
     * Записывать сюда нечего: состояние подтверждений ведёт [ServerAccessState],
     * и оно уже пополняется из `Set-Cookie` через [serverAccessPlugin].
     */
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = Unit

    override fun close() = Unit

    companion object {
        /** `2ch.hk` и его рабочее зеркало `2ch.su`, на которое двач сам и перенаправляет. */
        val DEFAULT_TRUSTED_HOSTS: Set<String> = setOf("2ch.hk", "2ch.su")
    }
}