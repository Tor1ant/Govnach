package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.ui.media.MediaSourceResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Выбор источника для плеера: обычный файл играет по адресу, файл закрытой доски —
 * из скачанной копии.
 *
 * Плеер не умеет передавать заголовки, а без ключа доступа файлы `/h/`, `/ho/`, `/e/`,
 * `/nf/`, `/fet/` отдают 500. Поэтому такие файлы качает клиент приложения.
 */
class MediaSourceResolverTest {

    private val imageBytes = ByteArray(64) { 7 }

    /** Ответ на проверку доступа: закрытая доска отвечает 500, обычная — 200. */
    private fun anonymousEngine(locked: Boolean) = MockEngine {
        if (locked) {
            respond("<html>Not found</html>", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "text/html"))
        } else {
            respond(imageBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "video/mp4"))
        }
    }

    private fun authenticatedEngine(requested: MutableList<String>) = MockEngine { req ->
        requested += req.url.toString()
        respond(imageBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "video/mp4"))
    }

    @Test
    fun openFileIsPlayedDirectly() {
        val downloads = mutableListOf<String>()
        val resolver = MediaSourceResolver(
            http = HttpClient(authenticatedEngine(downloads)),
            anonymous = HttpClient(anonymousEngine(locked = false))
        )

        val url = "https://2ch.hk/b/src/1/1.mp4"
        val playable = runBlocking { resolver.playable(url) }

        assertEquals(url, playable, "файл обычной доски играет по своему адресу")
        assertTrue(downloads.isEmpty(), "скачивать нечего: файл доступен как есть")
    }

    @Test
    fun lockedBoardFileIsDownloaded() {
        val downloads = mutableListOf<String>()
        val resolver = MediaSourceResolver(
            http = HttpClient(authenticatedEngine(downloads)),
            anonymous = HttpClient(anonymousEngine(locked = true))
        )

        val url = "https://2ch.hk/h/src/1/1.webm"
        val playable = runBlocking { resolver.playable(url) }

        assertTrue(playable != url, "файл закрытой доски должен прийти из локальной копии")
        assertTrue(playable.endsWith(".webm"), "расширение важно плееру, пришло: $playable")
        assertEquals(1, downloads.size, "файл качается один раз")
        assertTrue(downloads[0].contains("/h/src/1/1.webm"), "качается тот же адрес: ${downloads[0]}")
    }

    /** У двача закрыт целый раздел, поэтому про каждую следующую доску спрашивать не нужно. */
    @Test
    fun lockedBoardIsRemembered() {
        var probes = 0
        val anonymous = MockEngine {
            probes++
            respond("<html>Not found</html>", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        val resolver = MediaSourceResolver(
            http = HttpClient(authenticatedEngine(mutableListOf())),
            anonymous = HttpClient(anonymous)
        )

        runBlocking {
            resolver.playable("https://2ch.hk/h/src/1/1.webm")
            resolver.playable("https://2ch.hk/h/src/1/2.webm")
        }

        assertEquals(1, probes, "доска проверяется один раз, пришло запросов: $probes")
    }

    /** Скачать не удалось — плееру уходит исходный адрес, и он покажет свою ошибку. */
    @Test
    fun failedDownloadFallsBackToTheUrl() {
        val failing = MockEngine {
            respond("oops", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        val resolver = MediaSourceResolver(
            http = HttpClient(failing),
            anonymous = HttpClient(anonymousEngine(locked = true))
        )

        val url = "https://2ch.hk/h/src/1/1.webm"
        assertEquals(url, runBlocking { resolver.playable(url) })
    }
}