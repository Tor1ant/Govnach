package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.ADULT_CATEGORY
import com.github.tor1ant.govnach.client.boardsCategories
import com.github.tor1ant.govnach.client.isAdult
import com.github.tor1ant.govnach.client.isAdultContent
import com.github.tor1ant.govnach.client.needsAdultConfirmation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openapitools.client.models.Board

/**
 * Взрослые доски определяются по категории из апи, а не по id: список досок в апи меняется,
 * а категория «Взрослым» единственный признак, который даёт сервер.
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
        assertFalse(b.isAdult(), "обычная доска не должна попадать в гейт")
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
    fun confirmationRequiredUntilAllowed() {
        assertTrue(needsAdultConfirmation(sex, adultConfirmed = false))
        assertFalse(needsAdultConfirmation(sex, adultConfirmed = true))
        assertFalse(needsAdultConfirmation(b, adultConfirmed = false), "обычные доски без гейта")
    }

    @Test
    fun adultCategoryAppearsInCategoriesWithBoards() {
        val categories = boardsCategories(listOf(sex, fag, b, board("d", "Разное"), board("e", "")))

        assertEquals(listOf("Взрослым", "Разное"), categories, "без повторов и пустых, по алфавиту")
        assertEquals(ADULT_CATEGORY, categories.first(), "константа совпадает с категорией апи")
    }

    @Test
    fun fileNsfwFlagWinsOverBoardKind() {
        // Флаг от двача главнее: 0 и положительные — взрослый файл, отрицательный — явно без флага.
        assertTrue(isAdultContent(fileNsfw = 0))
        assertTrue(isAdultContent(fileNsfw = 1))
        assertFalse(isAdultContent(fileNsfw = -1))
        assertFalse(isAdultContent(fileNsfw = -1, adultBoard = true), "явная метка «не nsfw» важнее доски")
    }

    @Test
    fun emptyNsfwOnAdultBoardIsTreatedAsAdult() {
        assertTrue(isAdultContent(fileNsfw = null, adultBoard = true), "на /sex/ поле nsfw часто пустое")
        assertFalse(isAdultContent(fileNsfw = null, adultBoard = false), "на обычной доске без флага не размываем")
    }
}
