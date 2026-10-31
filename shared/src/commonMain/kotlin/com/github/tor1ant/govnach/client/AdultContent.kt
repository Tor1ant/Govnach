package com.github.tor1ant.govnach.client

import org.openapitools.client.models.Board

/**
 * Доски «для взрослых».
 *
 * В апи две такие доски не отличаются: у `/sex/` те же `enable_posting=true` и `max_comment`, что у `/b/`.
 * Признак один — категория доски в `/api/mobile/v2/boards`: «Взрослым». Сравниваем по началу строки
 * без учёта регистра, чтобы не потерять доску, если у категории поменяют падеж или добавят уточнение.
 */
const val ADULT_CATEGORY = "Взрослым"

/** Доска, которая требует подтверждения возраста и разрешения на контент 18+. */
fun Board.isAdult(): Boolean = category.trim().startsWith(ADULT_CATEGORY, ignoreCase = true)

/** Категории досок без повторов и пустых значений, в алфавитном порядке — для фильтра на главной. */
fun boardsCategories(boards: List<Board>): List<String> =
    boards.map { it.category.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

/** Нужно ли спрашивать подтверждение «мне есть 18» при открытии доски. */
fun needsAdultConfirmation(board: Board, adultConfirmed: Boolean): Boolean =
    board.isAdult() && !adultConfirmed

/**
 * Взрослый ли контент показан в файле.
 *
 * У файлов есть поле `nsfw`, но двач заполняет его не на всех досках. На доске «для взрослых»
 * поля может не быть вовсе, поэтому в треде такой доски превью считаем взрослым до явного
 * действия пользователя: размытие снимается тапом по превью.
 */
fun isAdultContent(fileNsfw: Int?, adultBoard: Boolean = false): Boolean =
    when {
        fileNsfw != null -> fileNsfw >= 0
        else -> adultBoard
    }
