package com.github.tor1ant.govnach.ui.search

import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview

/**
 * Привести текст к виду, по которому идёт сравнение: снять регистр, заменить «ё» на «е»
 * и свести типографику телефонов к клавиатурным символам.
 *
 * Без этого «ежик» не находил бы «ёжик», а «посмотри» — «посмотри». Длина строки не меняется:
 * замена символ-в-символ, поэтому подсветку по этому тексту можно резать на исходном.
 */
fun normalizeForSearch(text: String): String {
    if (text.isEmpty()) return text

    val out = StringBuilder(text.length)

    for (char in text.lowercase()) {
        out.append(
            when (char) {
                'ё' -> 'е'
                '–', '—', '−' -> '-'
                '‘', '’', '`' -> '\''
                '«', '»', '“', '”' -> '"'
                '\u00a0', '\u2009', '\u202f' -> ' '
                else -> char
            }
        )
    }

    return out.toString()
}

/**
 * Слова запроса. Ищем по словам, а не по целой строке: «анон кот» должен находить текст,
 * где между словами стоит разметка или другие слова, — так пользователь этого и ждёт.
 */
fun searchTerms(query: String): List<String> =
    normalizeForSearch(query)
        .split(WHITESPACE_REGEX)
        .filter { it.isNotEmpty() }

/**
 * Есть ли в [haystack] все слова [terms]. [haystack] должен быть уже нормализован
 * через [normalizeForSearch]: нормализация не бесплатная, по одному тексту её делают раз.
 */
fun matchesAllTerms(haystack: String, terms: List<String>): Boolean =
    terms.isNotEmpty() && terms.all { haystack.contains(it) }

/**
 * Оценка совпадения для сортировки.
 *
 * [titleScore] — по теме треда, [textScore] — по тексту ОП-поста. Совпадение в теме выше:
 * тред «Кошки» по запросу «кошки» нужнее треда, где слово мелькнуло в конце поста.
 * За совпадение в начале слова даётся бонус: набирая «кот», ждут «кототред» выше «вот и конец».
 */
data class MatchRank(val titleScore: Int, val textScore: Int)

fun rankMatch(title: String, text: String, terms: List<String>): MatchRank =
    MatchRank(titleScore = score(title, terms), textScore = score(text, terms))

private fun score(haystack: String, terms: List<String>): Int =
    terms.sumOf { term ->
        when (wordStartIndex(haystack, term)) {
            NONE -> 0
            0 -> WORD_START_BONUS + FIRST_WORD_BONUS
            else -> WORD_START_BONUS
        }
    }

/** Первое вхождение [term] в начале слова; [NONE] — такого нет. */
private fun wordStartIndex(haystack: String, term: String): Int {
    var from = 0

    while (from <= haystack.length - term.length) {
        val at = haystack.indexOf(term, from)
        if (at < 0) return NONE

        if (at == 0 || !haystack[at - 1].isLetterOrDigit()) return at
        from = at + 1
    }

    return NONE
}

/** Нормализованные поля треда, по которым идёт поиск. */
data class ThreadHaystacks(val title: String, val text: String)

/** Тема и текст треда, готовые к сравнению. */
fun threadHaystacks(thread: ThreadPreview): ThreadHaystacks =
    ThreadHaystacks(
        title = normalizeForSearch(thread.subject.orEmpty()),
        text = normalizeForSearch(thread.comment.orEmpty().htmlToPlainText())
    )

/**
 * Строка доски для поиска: id, название и категория — всё, что видно на карточке.
 * Регистр снят: по этой строке и фильтруют, и режут подсветку — границы совпадают
 * с исходными названиями, потому что нормализация меняет символы, а не длину.
 */
fun boardHaystack(board: Board): String =
    normalizeForSearch("/${board.id}/ ${board.name} ${board.category}")

private val WHITESPACE_REGEX = Regex("\\s+")
private val NONE = Int.MIN_VALUE
private const val WORD_START_BONUS = 1
private const val FIRST_WORD_BONUS = 1
