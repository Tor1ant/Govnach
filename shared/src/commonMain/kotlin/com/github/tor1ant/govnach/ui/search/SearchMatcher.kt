package com.github.tor1ant.govnach.ui.search

import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview

/** Тред вместе с доской, на которой он лежит: без доски результат некуда открыть. */
data class ThreadHit(
    val board: Board,
    val thread: ThreadPreview,
    val rank: MatchRank
)

/** Доска с нормализованной строкой поиска — по ней же рисуется подсветка. */
data class BoardHit(
    val board: Board,
    val haystack: String,
    val score: MatchRank
)

/**
 * Порядок выдачи: совпадение в теме важнее совпадения в тексте, затем выше свежий тред.
 * При равном номере порядок по id доски, чтобы результаты не прыгали между перерисовками.
 */
object ThreadOrder : Comparator<ThreadHit> {
    override fun compare(a: ThreadHit, b: ThreadHit): Int {
        if (a.rank.titleScore != b.rank.titleScore) return b.rank.titleScore.compareTo(a.rank.titleScore)
        if (a.rank.textScore != b.rank.textScore) return b.rank.textScore.compareTo(a.rank.textScore)

        val byThread = b.thread.num.compareTo(a.thread.num)
        return if (byThread != 0) byThread else a.board.id.compareTo(b.board.id)
    }
}

/** Доски по релевантности: совпадение в началах слов выше, при равенстве — алфавит по id. */
object BoardOrder : Comparator<BoardHit> {
    override fun compare(a: BoardHit, b: BoardHit): Int {
        if (a.score.titleScore != b.score.titleScore) return b.score.titleScore.compareTo(a.score.titleScore)
        return a.board.id.compareTo(b.board.id)
    }
}

/**
 * Треды, подходящие под запрос.
 *
 * [threadsByBoard] — треды, уже прочитанные с сервера ([SearchViewModel] держит их в памяти,
 * чтобы не ходить в сеть на каждый символ). Взрослые доски отсекает вызывающая сторона —
 * те же правила, что и у списка на главной.
 *
 * Совпадение засчитывается, когда присутствуют **все** слова запроса: слово из темы и слово
 * из поста вместе тоже считаются совпадением, искать в одном поле два разных слова незачем.
 */
fun searchThreads(
    threadsByBoard: Map<Board, List<ThreadPreview>>,
    terms: List<String>
): List<ThreadHit> {
    if (terms.isEmpty()) return emptyList()

    val hits = mutableListOf<ThreadHit>()

    for ((board, threads) in threadsByBoard) {
        for (thread in threads) {
            val haystacks = threadHaystacks(thread)
            if (!matchesAllFields(haystacks, terms)) continue

            hits += ThreadHit(
                board = board,
                thread = thread,
                rank = rankMatch(haystacks.title, haystacks.text, terms)
            )
        }
    }

    return hits.sortedWith(ThreadOrder)
}

/** Доски, подходящие под запрос. Взрослые доски — только после подтверждения возраста. */
fun searchBoards(
    boards: List<Board>,
    terms: List<String>,
    adultAllowed: Boolean
): List<BoardHit> {
    if (terms.isEmpty()) return emptyList()

    return boards
        .asSequence()
        .filter { visibleForSearch(it, adultAllowed) }
        .map { board ->
            val haystack = boardHaystack(board)
            BoardHit(board, haystack, rankMatch(haystack, "", terms))
        }
        .filter { matchesAllTerms(it.haystack, terms) }
        .sortedWith(BoardOrder)
        .toList()
}

/** Все слова запроса присутствуют хотя бы в одном из полей треда. */
private fun matchesAllFields(haystacks: ThreadHaystacks, terms: List<String>): Boolean =
    terms.all { term -> haystacks.title.contains(term) || haystacks.text.contains(term) }
