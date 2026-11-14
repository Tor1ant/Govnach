package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.ADULT_CATEGORY
import com.github.tor1ant.govnach.client.boardsCategories
import com.github.tor1ant.govnach.client.isAdult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openapitools.client.models.Board

/**
 * Взрослые доски определяются по категории из апи, а не по id: список досок в апи меняется,
 * а категория «Взрослым» — единственный признак, который даёт сервер.
 *
 * Признак нужен только чтобы пометить карточку значком 🔞. Доступ она не ограничивает:
 * гейта «мне есть 18» и размытия файлов в приложении больше нет.
 */
class AdultBoardsTest {

    /** Все поля Board обязательные, поэтому имя и категория — единственное, что здесь важно. */
    private fun board(id: String, category: String) = Board(
        id = id,
        name = id,
        category = category,
        info = "",
        infoOuter = "",
        threadsPerPage = 15,
        bumpLimit = 500,
        maxPages = 12,
        defaultName = "Аноним",
        enableNames = true,
        enableTrips = true,
        enableSubject = true,
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

    private val sex = board("sex", "Взрослым")
    private val fag = board("fag", "Взрослым")
    private val b = board("b", "Разное")

    @Test
    fun adultsAreDetectedByCategory() {
        assertTrue(sex.isAdult(), "«Взрослым» — взрослая доска")
        assertTrue(fag.isAdult())
        assertFalse(b.isAdult(), "обычная доска не должна помечаться как взрослая")
    }

    @Test
    fun categoryCheckDoesNotDependOnSpacingOrCase() {
        assertTrue(board("x", " взрослым ").isAdult(), "пробелы не должны ломать признак")
        assertTrue(board("x", "ВЗРОСЛЫМ").isAdult(), "регистр не должен ломать признак")
        assertTrue(board("x", "Взрослым, NFSW").isAdult(), "уточнение в категории не должно ломать признак")
        assertFalse(board("x", "Взрослеющим").isAdult(), "начало слова — не совпадение категории")
        assertFalse(board("x", "").isAdult())
    }

    @Test
    fun adultCategoryAppearsInCategoriesWithBoards() {
        // Категория «Взрослым» — обычная строка фильтра, рядом с остальными и без гейта.
        val categories = boardsCategories(listOf(sex, fag, b, board("d", "Разное"), board("e", "")))

        assertEquals(listOf("Взрослым", "Разное"), categories, "без повторов и пустых, по алфавиту")
        assertEquals(ADULT_CATEGORY, categories.first(), "константа совпадает с категорией апи")
    }
}
