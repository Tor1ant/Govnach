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
 * Совпадение засчитывается, когда присутствуют **все** слова запроса: слово из темы и слово
 * из поста вместе тоже считаются совпадением, искать в одном поле два разных слова незачем.
 */
fun searchThreads(
    threads: List<ThreadPreview>,
    terms: List<String>
): List<ThreadHit> {
    if (terms.isEmpty()) return emptyList()

    return threads
        .mapNotNull { thread ->
            val haystacks = threadHaystacks(thread)
            if (!matchesAllFields(haystacks, terms)) return@mapNotNull null

            ThreadHit(
                board = boardOf(thread),
                thread = thread,
                rank = rankMatch(haystacks.title, haystacks.text, terms)
            )
        }
        .sortedWith(ThreadOrder)
}

/** Id доски из полей самого треда — для выдачи, где доска не передана явно. */
private fun boardOf(thread: ThreadPreview): Board = Board(
    id = thread.board,
    name = thread.board,
    category = "",
    info = "",
    infoOuter = "",
    threadsPerPage = 0,
    bumpLimit = 0,
    maxPages = 0,
    defaultName = "",
    enableNames = false,
    enableTrips = false,
    enableSubject = false,
    enableSage = false,
    enableIcons = false,
    enableFlags = false,
    enableDices = false,
    enableShield = false,
    enableThreadTags = false,
    enablePosting = false,
    enableLikes = false,
    enableOekaki = false,
    fileTypes = emptyList(),
    maxComment = 0,
    maxFilesSize = 0
)

/**
 * Треды с доской, на которой они лежат.
 *
 * [threadsByBoard] — треды, уже прочитанные с сервера; в сеть фильтр не ходит.
 * Вызывающая сторона не передаёт доски, по которым искать не надо, — фильтра по ним здесь нет.
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

/** Доски, подходящие под запрос: совпадение в id, названии или категории. */
fun searchBoards(boards: List<Board>, terms: List<String>): List<BoardHit> {
    if (terms.isEmpty()) return emptyList()

    return boards
        .asSequence()
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
