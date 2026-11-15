package com.github.tor1ant.govnach.client

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

/**
 * Подставляет сохранённые подтверждения в запросы и запоминает присланные сервером.
 *
 * Зачем это нужно поверх `HttpCookies`: тот держит cookie только в памяти процесса,
 * а окно «вам 18 лет?» человек проходит один раз и ожидает, что ответ запомнится
 * между запусками. Поэтому состояние живёт в [ServerAccessState] и переживает перезапуск.
 *
 * Порядок установки важен: плагин ставится **до** `HttpCookies`, потому что тот в
 * `onRequest` переписывает заголовок `Cookie` своим состоянием и без своей cookie
 * просто убирает его. Идущий первым плагин успевает положить сохранённое подтверждение,
 * а `HttpCookies` затем дополняет его тем, что пришло в ответах этого сеанса.
 */
fun serverAccessPlugin(state: ServerAccessState) = createClientPlugin("ServerAccessPlugin") {

    onRequest { request, _ ->
        val saved = state.cookieHeader()
        if (saved.isNotEmpty()) {
            val existing = request.headers[HttpHeaders.Cookie]
            request.headers[HttpHeaders.Cookie] =
                if (existing.isNullOrBlank()) saved else "$existing; $saved"
        }
    }

    onResponse { response ->
        val setCookie = response.headers.getAll(HttpHeaders.SetCookie)
        if (!setCookie.isNullOrEmpty()) {
            // Запоминаем молча: даже если запись не удалась, запрос уже выполнен.
            // Здесь важен `usercode_auth` — ключ, который двач выдаёт после первого поста
            // и которым потом открываются закрытые разделы.
            state.absorb(setCookie)
        }
    }
}