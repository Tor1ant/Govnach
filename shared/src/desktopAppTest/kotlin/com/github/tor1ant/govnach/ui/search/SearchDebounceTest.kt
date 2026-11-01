package com.github.tor1ant.govnach.ui.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Дебаунс строки поиска: проверяем именно задержку, а не логику фильтра.
 *
 * Полсекунды — требование к поиску, поэтому тест смотрит на виртуальное время:
 * пока пользователь печатает, проходов нет; после паузы — ровно один, по последней строке.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchDebounceTest {

    @Test
    fun debounceDelayIsHalfSecond() {
        assertEquals(500L, SEARCH_DEBOUNCE.inWholeMilliseconds)
    }

    @Test
    fun typingFastProducesSingleFilterRun() = runTest {
        val query = SearchQuery()
        val collected = mutableListOf<String>()

        val job = launch { query.ready().collect { collected += it } }
        // Даём подписке встать на поток.
        advanceTimeBy(1.milliseconds)

        // Набор «кот» с паузами 100 мс между символами — быстрее задержки.
        query.update("к")
        advanceTimeBy(100.milliseconds)
        query.update("ко")
        advanceTimeBy(100.milliseconds)
        query.update("кот")
        advanceTimeBy(100.milliseconds)

        assertEquals(emptyList(), collected, "пока пользователь печатает, фильтр не запускается")

        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)

        assertEquals(listOf("кот"), collected, "после паузы — один проход по последней строке")

        job.cancel()
    }

    @Test
    fun pauseBetweenWordsRunsSearchTwice() = runTest {
        val query = SearchQuery()
        val collected = mutableListOf<String>()

        val job = launch { query.ready().collect { collected += it } }
        advanceTimeBy(1.milliseconds)

        query.update("кот")
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)
        query.update("рыба")
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)

        assertEquals(listOf("кот", "рыба"), collected, "каждая пауза — свой запрос")

        job.cancel()
    }

    @Test
    fun repeatedIdenticalQueryIsNotRerun() = runTest {
        val query = SearchQuery()
        val collected = mutableListOf<String>()

        val job = launch { query.ready().collect { collected += it } }
        advanceTimeBy(1.milliseconds)

        query.update("кот")
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)
        // Пробелы по краям и другой регистр — тот же запрос: фильтру достаточно одного прохода.
        query.update("  кот ")
        advanceTimeBy(SEARCH_DEBOUNCE + 1.milliseconds)

        assertEquals(listOf("кот"), collected)

        job.cancel()
    }

    @Test
    fun shortDebounceStillWaitsForThePause() = runTest {
        // Та же проверка с короткой задержкой: она идёт через реальные таймауты тестового
        // диспетчера, а не через виртуальное время, — debounce работает независимо от 500 мс.
        val query = SearchQuery()
        val collected = mutableListOf<String>()

        val job = launch {
            query.ready(debounce = 80.milliseconds).collect { collected += it }
        }

        advanceTimeBy(10.milliseconds)
        query.update("а")
        advanceTimeBy(10.milliseconds)
        query.update("аб")

        assertEquals(emptyList(), collected, "внутри задержки прохода нет")

        advanceTimeBy(100.milliseconds)

        assertEquals(listOf("аб"), collected)

        job.cancel()
    }
}
