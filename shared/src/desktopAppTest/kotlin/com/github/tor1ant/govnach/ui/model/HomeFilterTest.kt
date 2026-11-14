package com.github.tor1ant.govnach.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openapitools.client.models.Board

/**
 * Фильтр категорий на главной.
 *
 * Проверяем именно чистые функции над [HomeUiState]: экран рисует список из них,
 * и регрессия здесь — это либо «чип не фильтрует», либо «фильтр выкидывает не то».
 */
class HomeFilterTest {

    private fun board(id: String, category: String) = Board(
        id = id,
        name = id,
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

    private val boards = listOf(
        board("b", "Разное"),
        board("tv", "Тематика"),
        board("mu", "Тематика"),
        board("sex", "Взрослым")
    )

    @Test
    fun noCategorySelectedShowsEveryBoard() {
        val state = HomeUiState(boards = boards)

        assertEquals(boards, state.filteredBoards(), "без выбора видно всё")
    }

    @Test
    fun selectedCategoryKeepsOnlyItsBoards() {
        val state = HomeUiState(boards = boards, selectedCategory = "Тематика")

        assertEquals(
            listOf("tv", "mu"),
            state.filteredBoards().map { it.id },
            "чип категории фильтрует список по этой категории"
        )
    }

    @Test
    fun adultCategoryFiltersLikeAnyOther() {
        // Гейта 18+ нет: категория «Взрослым» — обычная строка фильтра.
        val state = HomeUiState(boards = boards, selectedCategory = "Взрослым")

        assertEquals(listOf("sex"), state.filteredBoards().map { it.id })
    }

    @Test
    fun selectingCategoryAgainResetToAll() {
        val filtered = HomeUiState(boards = boards, selectedCategory = "Тематика")
        assertEquals(2, filtered.filteredBoards().size)

        val reset = filtered.copy(selectedCategory = null)
        assertEquals(boards.size, reset.filteredBoards().size, "«Все» возвращает полный список")
    }

    @Test
    fun categoriesAreDistinctAndSorted() {
        val categories = HomeUiState(boards = boards).filterCategories()

        assertEquals(listOf("Взрослым", "Разное", "Тематика"), categories, "без повторов, по алфавиту")
    }

    @Test
    fun everyListedCategoryMatchesAtLeastOneBoard() {
        val state = HomeUiState(boards = boards)

        // Чип, который не находит ни одной доски, — это «фильтр не работает» с точки зрения пользователя.
        for (category in state.filterCategories()) {
            val found = state.copy(selectedCategory = category).filteredBoards()
            assertEquals(
                true,
                found.isNotEmpty(),
                "категория «$category» из фильтра должна находить свои доски"
            )
        }
    }
}
