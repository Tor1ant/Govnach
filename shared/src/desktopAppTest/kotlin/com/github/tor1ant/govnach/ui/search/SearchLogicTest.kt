package com.github.tor1ant.govnach.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview

/**
 * Поиск по доскам и тредам — чистая логика без сети и Compose.
 *
 * Проверяем то, что пользователь видит в выдаче: находит ли по части слова, не находить
 * ли лишнего, что стоит выше и какие интервалы подсвечиваются.
 *
 * Поиск контекстный: на главной ищут доски ([searchBoards]), внутри доски — её треды
 * ([searchThreads] по списку). Доски категории «Взрослым» участвуют наравне с остальными.
 */
class SearchLogicTest {

    private fun board(
        id: String,
        name: String = id,
        category: String = "Разное"
    ) = Board(
        id = id,
        name = name,
        category = category,
        info = "",
        infoOuter = "",
        threadsPerPage = 10,
        bumpLimit = 500,
        maxPages = 10,
        defaultName = "Аноним",
        enableNames = true,
        enableTrips = true,
        enableSubject = false,
        enableSage = true,
        enableIcons = false,
        enableFlags = false,
        enableDices = false,
        enableShield = false,
        enableThreadTags = false,
        enablePosting = true,
        enableLikes = false,
        enableOekaki = false,
        fileTypes = listOf("jpg"),
        maxComment = 15_000,
        maxFilesSize = 40_960
    )

    private fun thread(
        num: Int,
        subject: String?,
        comment: String,
        boardId: String = "b"
    ) = ThreadPreview(
        num = num,
        parent = num,
        board = boardId,
        timestamp = 1_700_000_000,
        date = "01/01/26 Чт 00:00:00",
        comment = comment,
        subject = subject,
        postsCount = 10,
        filesCount = 1
    )

    // --- нормализация и слова ----------------------------------------------------------

    @Test
    fun normalizingFoldsCaseAndYo() {
        assertEquals("ежик в тумане", normalizeForSearch("Ёжик в Тумане"))
        assertEquals("ежик", normalizeForSearch("ёжик"), "«ё» ищется как «е»")
    }

    @Test
    fun normalizingKeepsLengthSoHighlightIndicesMatch() {
        val source = "Ёжик — в тумане «раз»"
        assertEquals(
            source.length,
            normalizeForSearch(source).length,
            "замена символ-в-символ: длина не меняется, индексы подсветки остаются валидными"
        )
    }

    @Test
    fun querySplitsIntoWordsIgnoringExtraSpaces() {
        assertEquals(listOf("кот", "рыба"), searchTerms("  Кот   РЫБА "))
        assertEquals(emptyList(), searchTerms("   "))
    }

    // --- доски ------------------------------------------------------------------------

    @Test
    fun boardFoundByPrefixOfItsName() {
        val boards = listOf(board("b", "Разное"), board("tv", "Телевидение"), board("vg", "Видеоигры"))

        val hits = searchBoards(boards, searchTerms("теле"))

        assertEquals(listOf("tv"), hits.map { it.board.id }, "поиск идёт уже на первых символах")
    }

    @Test
    fun boardFoundByItsId() {
        val boards = listOf(board("b", "Разное"), board("mu", "Музыка"))

        assertEquals(listOf("mu"), searchBoards(boards, searchTerms("mu")).map { it.board.id })
    }

    @Test
    fun boardFoundByItsCategory() {
        val boards = listOf(board("b", "Разное"), board("mu", "Музыка", "Творчество"))

        assertEquals(listOf("mu"), searchBoards(boards, searchTerms("творч")).map { it.board.id })
    }

    @Test
    fun adultBoardsAreSearchedLikeAnyOther() {
        // Гейта 18+ больше нет: категория «Взрослым» — такая же строка в поиске, как остальные.
        val boards = listOf(board("b", "Разное"), board("sex", "Секс", "Взрослым"))

        assertEquals(
            listOf("sex"),
            searchBoards(boards, searchTerms("взрослым")).map { it.board.id },
            "доска категории «Взрослым» находится по категории без подтверждений"
        )
        assertEquals(
            listOf("sex"),
            searchBoards(boards, searchTerms("sex")).map { it.board.id }
        )
    }

    @Test
    fun emptyQueryReturnsNothing() {
        val boards = listOf(board("b", "Разное"))

        assertTrue(searchBoards(boards, searchTerms("   ")).isEmpty())
    }

    // --- треды ------------------------------------------------------------------------

    @Test
    fun threadFoundBySubject() {
        val threads = listOf(
            thread(1, subject = "Кошки", comment = "<p>просто тред</p>"),
            thread(2, subject = "Собаки", comment = "<p>просто тред</p>")
        )

        val hits = searchThreads(threads, searchTerms("кош"))

        assertEquals(listOf(1), hits.map { it.thread.num })
    }

    @Test
    fun threadFoundByCommentEvenWhenSubjectLacksIt() {
        val threads = listOf(thread(7, subject = "Вечерний", comment = "<p>Сегодня про <b>рыбалку</b></p>"))

        val hits = searchThreads(threads, searchTerms("рыбалк"))

        assertEquals(listOf(7), hits.map { it.thread.num })
    }

    @Test
    fun htmlMarkupDoesNotHideWords() {
        val threads = listOf(
            thread(3, subject = null, comment = "<p>Ссылка на <a href=\"/res/1.html\">архив</a></p>")
        )

        assertEquals(listOf(3), searchThreads(threads, searchTerms("архив")).map { it.thread.num })
    }

    @Test
    fun allWordsMustBePresent() {
        val threads = listOf(
            thread(1, subject = "Кот", comment = "<p>просто пост</p>"),
            thread(2, subject = "Кот и рыба", comment = "<p>просто пост</p>")
        )

        val hits = searchThreads(threads, searchTerms("кот рыба"))

        assertEquals(listOf(2), hits.map { it.thread.num }, "ищутся оба слова, а не любое")
    }

    @Test
    fun wordsMayBeSplitBetweenSubjectAndComment() {
        val threads = listOf(thread(5, subject = "Кот", comment = "<p>и рыба</p>"))

        assertEquals(listOf(5), searchThreads(threads, searchTerms("кот рыба")).map { it.thread.num })
    }

    @Test
    fun threadMatchesAreCaseAndYoInsensitive() {
        val threads = listOf(thread(9, subject = "Ёжики", comment = "<p>колючие</p>"))

        assertEquals(listOf(9), searchThreads(threads, searchTerms("ежик")).map { it.thread.num })
    }

    @Test
    fun threadInSubjectOutranksThreadInComment() {
        val threads = listOf(
            thread(100, subject = null, comment = "<p>в конце поста упомянут кот</p>"),
            thread(1, subject = "Кототред", comment = "<p>картинки</p>")
        )

        val hits = searchThreads(threads, searchTerms("кот"))

        assertEquals(
            listOf(1, 100),
            hits.map { it.thread.num },
            "совпадение в теме важнее совпадения в тексте, даже если тред старше"
        )
    }

    @Test
    fun freshThreadFirstWhenBothMatchInSubject() {
        val threads = listOf(
            thread(10, subject = "Кототред", comment = "<p>a</p>"),
            thread(50, subject = "Кот", comment = "<p>b</p>")
        )

        assertEquals(
            listOf(50, 10),
            searchThreads(threads, searchTerms("кот")).map { it.thread.num },
            "при равной релевантности свежий тред выше"
        )
    }

    @Test
    fun wordStartMatchRanksAboveMatchInsideWord() {
        val threads = listOf(
            // «закотово» — «кот» внутри слова, в теме.
            thread(1, subject = "Всё закотово", comment = "<p>a</p>"),
            thread(2, subject = "Кот", comment = "<p>b</p>")
        )

        val hits = searchThreads(threads, searchTerms("кот"))

        assertEquals(
            listOf(2, 1),
            hits.map { it.thread.num },
            "совпадение с начала слова выше совпадения внутри слова"
        )
    }

    @Test
    fun threadHitCarriesBoardIdFromTheThreadItself() {
        // Поиск внутри доски не передаёт доску явно: ударение на том, что id доски
        // для перехода восстанавливается из самого треда (поле board).
        val threads = listOf(thread(4, subject = "Коты", comment = "<p>x</p>", boardId = "sex"))

        val hits = searchThreads(threads, searchTerms("кот"))

        assertEquals("sex", hits.single().board.id, "тред на доске «Взрослым» ищется как любой другой")
    }

    @Test
    fun emptyThreadListReturnsNothing() {
        assertTrue(searchThreads(emptyList(), searchTerms("кот")).isEmpty())
    }

    // --- подсветка ---------------------------------------------------------------------

    @Test
    fun highlightRangesPointAtWordsInSourceText() {
        val text = "Кот и котёнок"
        val ranges = matchRanges(normalizeForSearch(text), searchTerms("кот"))

        assertEquals(2, ranges.size, "оба вхождения «кот» выделяются")
        assertEquals("кот", text.substring(ranges[0].start, ranges[0].end).lowercase())
        assertEquals("кот", text.substring(ranges[1].start, ranges[1].end).lowercase())
    }

    @Test
    fun adjacentMatchesMergeIntoOneRange() {
        // «кот» в «коты» и «ты» — два совпадения встык, подсветка не должна рваться.
        val ranges = matchRanges("коты", listOf("кот", "ты"))

        assertEquals(listOf(MatchRange(0, 4)), ranges)
    }

    @Test
    fun unmatchedTextHasNoRanges() {
        assertTrue(matchRanges("кошки", searchTerms("собака")).isEmpty())
        assertTrue(matchRanges("кошки", emptyList()).isEmpty())
    }

    // --- адаптер фазы поиска -----------------------------------------------------------

    @Test
    fun pendingQueryKeepsPreviousResultsVisible() {
        val query = SearchQuery()
        query.update("кот")
        query.accept("кот")
        query.markReady("кот")
        assertEquals(SearchPhase.READY, query.state.value.phase)

        query.update("кото")
        assertEquals(SearchPhase.PENDING, query.state.value.phase, "пока не истекла пауза — ждём")
        assertEquals("кот", query.state.value.debouncedQuery, "старая выдача ещё показывается")

        query.update("")
        assertEquals(SearchPhase.IDLE, query.state.value.phase)
        assertEquals("", query.state.value.debouncedQuery, "очистка сбрасывает выдачу")
    }

    @Test
    fun flushHandsQueryToFilterWithoutWaiting() {
        val query = SearchQuery()
        query.update("рыба")
        assertEquals("", query.state.value.debouncedQuery)

        query.flush()

        assertEquals("рыба", query.state.value.debouncedQuery, "Enter сразу отдаёт строку фильтру")
        assertEquals(SearchPhase.PENDING, query.state.value.phase, "результатов ещё нет — ждём их")
    }

    @Test
    fun acceptKeepsPendingUntilResultsArrive() {
        val query = SearchQuery()
        query.update("кот")
        query.accept("кот")

        assertEquals("кот", query.state.value.debouncedQuery, "фильтр запущен")
        assertEquals(
            SearchPhase.PENDING,
            query.state.value.phase,
            "READY только после того, как результаты на экране: иначе «не найдено» мелькнёт раньше"
        )

        query.markReady("кот")
        assertEquals(SearchPhase.READY, query.state.value.phase)
    }

    @Test
    fun acceptIgnoresStaleQuery() {
        val query = SearchQuery()
        query.update("кот")
        query.update("кошка")
        query.accept("кот")

        assertEquals("", query.state.value.debouncedQuery, "устаревшая строка не запускает фильтр")
    }

    @Test
    fun markReadyIgnoresStaleQuery() {
        val query = SearchQuery()
        query.update("кот")
        query.update("кошка")
        query.markReady("кот")

        assertFalse(query.state.value.phase == SearchPhase.READY, "устаревшая строка не должна применяться")
    }
}
