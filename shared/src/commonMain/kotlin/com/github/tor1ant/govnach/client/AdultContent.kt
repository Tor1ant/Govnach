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

/**
 * Доска категории «Взрослым».
 *
 * Это только метка для оформления — значок и цвет категории в списках. Доступ она больше
 * не ограничивает: взрослые доски лежат в общем списке, открываются, читаются и принимают посты
 * наравне с остальными.
 */
fun Board.isAdult(): Boolean = category.trim().startsWith(ADULT_CATEGORY, ignoreCase = true)

/** Категории досок без повторов и пустых значений, в алфавитном порядке — для фильтра на главной. */
fun boardsCategories(boards: List<Board>): List<String> =
    boards.map { it.category.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
