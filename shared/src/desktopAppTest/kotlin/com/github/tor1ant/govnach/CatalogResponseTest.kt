package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.ApiResponseException
import com.github.tor1ant.govnach.client.AppLog
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.LogLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.openapitools.client.apis.DefaultApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Доски, у которых вместо JSON приходит HTML.
 *
 * Так отвечает взрослая доска /h/ (и /ho/, /nf/, /e/, /fet/): `catalog.json` отдаёт страницу
 * «Not found» со статусом 500. Ktor не находит конвертер под `text/html`, пропускает ответ как есть,
 * и `body()` падает с `NoTransformationFoundException` — в интерфейсе это выглядело как «доска
 * не открывается» с именем класса библиотеки. Клиент обязан отдавать свою ошибку со статусом и куском
 * ответа, чтобы экран показал внятный текст.
 */
class CatalogResponseTest {

    private val notFoundHtml = """
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ru">
            <head>
                <title>Not found</title>
            </head>
            <body>Страница не найдена</body>
        </html>
    """.trimIndent()

    private fun client(engine: MockEngine): Client = Client(
        DefaultApi(
            httpClientEngine = engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
        )
    )

    /** Клиент с общим журналом: консоль должна получать записи об ошибках доски. */
    private fun client(engine: MockEngine, log: AppLog): Client = Client(
        client = DefaultApi(
            httpClientEngine = engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
        ),
        log = log
    )

    private fun htmlErrorEngine(status: HttpStatusCode, body: String) = MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
        )
    }

    @Test
    fun catalogHtmlErrorBecomesReadableException() {
        val client = client(htmlErrorEngine(HttpStatusCode.InternalServerError, notFoundHtml))

        val failure = runCatching { runBlocking { client.getThreads("h") } }.exceptionOrNull()

        assertTrue(failure is ApiResponseException, "HTML вместо JSON — это ошибка ответа, а не исключение Ktor")
        failure as ApiResponseException
        assertEquals(500, failure.status, "статус сервера должен сохраниться")
        assertTrue(failure.contentType?.startsWith("text/html") == true, "content type должен сохраниться")
        assertTrue(failure.message?.contains("/h/") == true, "в ошибке видно, что именно грузили")
        assertTrue(failure.message?.contains("500") == true, "в ошибке виден статус")
        assertFalse(
            failure.message?.contains("NoTransformationFoundException") == true,
            "пользователь не должен видеть имя класса Ktor"
        )
        assertTrue(
            failure.cause is io.ktor.client.call.NoTransformationFoundException,
            "исходная ошибка Ktor остаётся в причине для диагностики"
        )
    }

    @Test
    fun htmlWithOkStatusIsAlsoReported() {
        // /fur/catalog.json отвечает 200 и HTML стороннего сайта: статус тут ничему не мешает.
        val client = client(htmlErrorEngine(HttpStatusCode.OK, "<html><title>Google</title></html>"))

        val failure = runCatching { runBlocking { client.getThreads("fur") } }.exceptionOrNull()

        assertTrue(failure is ApiResponseException)
        assertEquals(200, (failure as ApiResponseException).status)
        assertTrue(failure.message?.contains("/fur/") == true)
    }

    @Test
    fun validCatalogStillParses() {
        val json = """
            {"threads":[{"num":123,"parent":0,"board":"sex","timestamp":1,"date":"01/01/26",
            "comment":"<b>ОП</b>","files":[{"name":"a.png","fullname":"a.png","displayname":"a.png",
            "path":"/sex/src/123/a.png","thumbnail":"/sex/thumb/123/a.png","type":2,"size":10,
            "width":1,"height":1,"tn_width":1,"tn_height":1}]}]}
        """.trimIndent()

        val engine = MockEngine {
            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }

        val threads = runBlocking { client(engine).getThreads("sex") }

        assertEquals(1, threads.size)
        assertEquals(123, threads.first().num)
        assertEquals(1, threads.first().files?.size)
    }

    @Test
    fun emptyCatalogIsNotAnError() {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }

        assertTrue(runBlocking { client(engine).getThreads("gg") }.isEmpty())
    }

    /**
     * Ошибку доски должно быть видно в консоли: именно за этим её и открывают.
     * В записи нужны и понятный текст, и технические детали для багрепорта.
     */
    @Test
    fun failureIsRecordedInConsole() {
        val log = AppLog()
        val client = client(htmlErrorEngine(HttpStatusCode.InternalServerError, notFoundHtml), log)

        runCatching { runBlocking { client.getThreads("h") } }

        val entry = log.entries.value.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertTrue(entry.message.contains("/h/"), "в записи видно доску: ${entry.message}")
        val detail = entry.detail.orEmpty()
        assertTrue(detail.contains("500"), "виден статус ответа")
        assertTrue(detail.contains("text/html"), "виден content-type")
        assertTrue(detail.contains("Not found"), "виден кусок тела ответа — по нему понятно, что пришло")
    }

    /** Успешная доска не должна засорять журнал ошибками. */
    @Test
    fun successfulLoadWritesNoError() {
        val log = AppLog()
        val engine = MockEngine {
            respond(
                content = """{"threads":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
            )
        }

        runBlocking { client(engine, log).getThreads("sex") }

        assertTrue(log.entries.value.isEmpty(), "успешная загрузка не пишет в журнал ошибок")
    }
}
