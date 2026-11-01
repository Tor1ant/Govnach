package com.github.tor1ant.govnach.ui.search

import com.github.tor1ant.govnach.client.Client
import com.sun.net.httpserver.HttpServer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds

/**
 * Сквозной проход поиска: ввод строки → дебаунс → фильтр → состояние экрана.
 *
 * Вместо сети поднимается локальный HTTP-сервер, который отдаёт настоящие JSON-ответы
 * двача для `/api/mobile/v2/boards` и `/{board}/catalog.json`. Так проверяется вся цепочка
 * целиком — разбор ответов, кэш тредов, дебаунс и фильтр, — а не только чистые функции.
 *
 * Вьюмодель живёт в [androidx.lifecycle.viewModelScope], а не в скоупе теста, поэтому
 * результат ждём с таймаутом через `first { ... }`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private var server: HttpServer? = null

    /**
     * `viewModelScope` на desktop берёт `Dispatchers.Main`, а в обычном JVM-тесте его нет —
     * без подмены корутины вьюмодели просто не стартуют. Настоящий Swing-диспетчер здесь
     * не нужен: тесту хватает фонового пула, порядок вызовов от этого не зависит.
     */
    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @AfterTest
    fun stopServer() {
        server?.stop(0)
        Dispatchers.resetMain()
    }

    private fun startServer(boardsJson: String, catalogs: Map<String, String>): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        http.createContext("/api/mobile/v2/boards") { exchange ->
            val body = boardsJson.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        for ((boardId, json) in catalogs) {
            http.createContext("/$boardId/catalog.json") { exchange ->
                requestedCatalogs += boardId
                val body = json.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }

        // Доска без каталога: ответ 200 с пустым списком, как у двача без тредов.
        http.createContext("/") { exchange ->
            requestedCatalogs += "other:${exchange.requestURI}"
            val body = """{"threads":[]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}"
    }

    private fun boardJson(id: String, name: String, category: String = "Разное"): String = """
        {
          "id": "$id",
          "name": "$name",
          "category": "$category",
          "info": "",
          "info_outer": "",
          "threads_per_page": 10,
          "bump_limit": 500,
          "max_pages": 10,
          "default_name": "Аноним",
          "enable_names": true,
          "enable_trips": true,
          "enable_subject": true,
          "enable_sage": true,
          "enable_icons": false,
          "enable_flags": false,
          "enable_dices": false,
          "enable_shield": false,
          "enable_thread_tags": false,
          "enable_posting": true,
          "enable_likes": false,
          "enable_oekaki": false,
          "file_types": ["jpg"],
          "max_comment": 15000,
          "max_files_size": 40960
        }
    """.trimIndent()

    private fun catalogJson(vararg threads: String): String =
        """{"threads": [${threads.joinToString(",")}], "filter": ""}"""

    private fun threadJson(num: Int, subject: String?, comment: String, boardId: String): String {
        val subjectField = subject?.let { """"subject": "${it.replace("\"", "\\\"")}",""" } ?: ""
        return """
            {
              "num": $num,
              "parent": $num,
              "board": "$boardId",
              "timestamp": 1700000000,
              "date": "01/01/26 Чт 00:00:00",
              "comment": "${comment.replace("\"", "\\\"")}",
              $subjectField
              "posts_count": 10,
              "files_count": 1
            }
        """.trimIndent()
    }

    /**
     * Клиент как в приложении: [AppModule] ставит ContentNegotiation, иначе тела ответов
     * не разбираются в модели. BaseUrl переведён на локальный сервер.
     */
    private fun viewModel(baseUrl: String) = SearchViewModel(
        client = Client(
            org.openapitools.client.apis.DefaultApi(
                baseUrl = baseUrl,
                httpClientConfig = { config ->
                    config.install(ContentNegotiation) {
                        json(Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                            encodeDefaults = true
                        })
                    }
                }
            )
        ),
        dispatcher = Dispatchers.Default,
        debounce = 50.milliseconds
    )

    /** Треды, которые фиктивный сервер реально отдал: видно, дошёл ли запрос по доске. */
    private val requestedCatalogs = java.util.Collections.synchronizedList(mutableListOf<String>())

    private val boardsJson = """
        [${boardJson("b", "Разное")}, ${boardJson("mu", "Музыка")}, ${boardJson("sex", "Взрослым", "Взрослым")}]
    """.trimIndent()

    private val catalogs = mapOf(
        "b" to catalogJson(
            threadJson(1, "Кототред", "<p>картинки</p>", "b"),
            threadJson(2, "Собаки", "<p>просто про рыбалку</p>", "b")
        ),
        "mu" to catalogJson(threadJson(3, "Музыка", "<p>кот поёт</p>", "mu")),
        "sex" to catalogJson(threadJson(4, "Коты", "<p>18+</p>", "sex"))
    )

    private suspend fun SearchViewModel.awaitResults(
        predicate: (SearchUiState) -> Boolean
    ): SearchUiState = withContext(Dispatchers.Default) {
        withTimeout(10_000) { uiState.first(predicate) }
    }

    @Test
    fun serverFixtureIsParsedByClient() {
        kotlinx.coroutines.runBlocking {
            val baseUrl = startServer(boardsJson, catalogs)
            val api = org.openapitools.client.apis.DefaultApi(
                baseUrl = baseUrl,
                httpClientConfig = { config ->
                    config.install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; isLenient = true })
                    }
                }
            )

            val boards = api.apiMobileV2BoardsGet().body()
            val catalog = api.boardCatalogJsonGet("mu").body()

            // Фейковые ответы — настоящий контракт апи: поля разбираются в модели.
            assertEquals(listOf("b", "mu", "sex"), boards.map { it.id })
            assertEquals(listOf(3), catalog.threads.orEmpty().map { it.num })
        }
    }

    @Test
    fun boardIsFoundByPrefixAfterDebounce() {
        kotlinx.coroutines.runBlocking {
            val vm = viewModel(startServer(boardsJson, catalogs))
            vm.preload()

            vm.updateQuery("музы")

            val state = vm.awaitResults { it.query == "музы" && it.boards.isNotEmpty() }

            assertEquals(
                listOf("mu"),
                state.boards.map { it.board.id },
                "доска находится по началу названия. Запрошенные каталоги: $requestedCatalogs"
            )
        }
    }

    @Test
    fun threadsAreFoundAcrossBoardsExceptAdult() {
        kotlinx.coroutines.runBlocking {
            val vm = viewModel(startServer(boardsJson, catalogs))
            vm.preload()

            vm.updateQuery("кот")

            val state = vm.awaitResults { it.query == "кот" && it.threads.isNotEmpty() }

            assertEquals(
                listOf("b", "mu"),
                state.threads.map { it.board.id },
                "тред найден на двух досках; взрослая /sex/ не вошла без подтверждения возраста. " +
                    "Запрошенные каталоги: $requestedCatalogs"
            )
        }
    }

    @Test
    fun ageConfirmationRevealsAdultThreads() {
        kotlinx.coroutines.runBlocking {
            val vm = viewModel(startServer(boardsJson, catalogs))
            vm.preload()
            vm.updateQuery("кот")
            vm.awaitResults { it.query == "кот" && it.threads.isNotEmpty() }

            vm.allowAdult()

            val state = vm.awaitResults { it.query == "кот" && it.threads.any { hit -> hit.board.id == "sex" } }

            assertTrue(state.threads.any { it.board.id == "sex" }, "после подтверждения взрослый тред в выдаче")
        }
    }

    @Test
    fun clearingTheFieldEmptiesResults() {
        kotlinx.coroutines.runBlocking {
            val vm = viewModel(startServer(boardsJson, catalogs))
            vm.preload()
            vm.updateQuery("кот")
            vm.awaitResults { it.query == "кот" && it.threads.isNotEmpty() }

            vm.clear()

            val state = vm.awaitResults { it.query.isEmpty() }

            assertTrue(state.boards.isEmpty() && state.threads.isEmpty(), "очистка убирает выдачу")
            assertEquals(SearchPhase.IDLE, state.phase)
        }
    }

    @Test
    fun typingLetterByLetterYieldsOnlyFinalQueryResults() {
        kotlinx.coroutines.runBlocking {
            val vm = viewModel(startServer(boardsJson, catalogs))
            vm.preload()

            vm.updateQuery("к")
            vm.updateQuery("ко")
            vm.updateQuery("кот")

            val state = vm.awaitResults { it.query == "кот" && it.threads.isNotEmpty() }

            assertTrue(state.boards.isEmpty(), "по «кот» среди досок совпадений нет")
            assertEquals(setOf("b", "mu"), state.threads.map { it.board.id }.toSet())
        }
    }

    @Test
    fun enterSearchesWithoutWaitingForDebounce() {
        kotlinx.coroutines.runBlocking {
            val vm = viewModel(startServer(boardsJson, catalogs))
            vm.preload()
            vm.awaitResults { it.query.isEmpty() }

            vm.updateQuery("музыка")
            vm.submit()

            val state = vm.awaitResults { it.query == "музыка" && it.boards.isNotEmpty() }

            assertEquals(listOf("mu"), state.boards.map { it.board.id })
        }
    }
}
