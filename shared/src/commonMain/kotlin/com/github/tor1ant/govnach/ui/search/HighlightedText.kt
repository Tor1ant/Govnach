package com.github.tor1ant.govnach.ui.search

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * Текст с подсветкой слов запроса.
 *
 * Нормализация ([normalizeForSearch]) меняет регистр и «ё», но не длину строки, поэтому
 * индексы вхождений в нормализованном тексте — это те же индексы в исходном. Совпадение
 * выделяем акцентом и полужирным: цвет работает и в светлой, и в тёмной теме.
 */
@Composable
fun HighlightedText(
    text: String,
    terms: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = highlightMatches(text, terms),
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow
    )
}

/** Разметка с подсветкой — отдельно от [HighlightedText], чтобы годилось и в превью, и в тестах. */
@Composable
fun highlightMatches(
    text: String,
    terms: List<String>,
    color: Color = MaterialTheme.colorScheme.primary
): AnnotatedString {
    if (terms.isEmpty() || text.isEmpty()) return AnnotatedString(text)

    val ranges = matchRanges(normalizeForSearch(text), terms)
    if (ranges.isEmpty()) return AnnotatedString(text)

    val accent = SpanStyle(color = color, fontWeight = FontWeight.SemiBold)

    return buildAnnotatedString {
        var cursor = 0

        for (range in ranges) {
            if (range.start > cursor) append(text.substring(cursor, range.start))
            withStyle(accent) { append(text.substring(range.start, range.end)) }
            cursor = range.end
        }

        if (cursor < text.length) append(text.substring(cursor))
    }
}

/** Один интервал совпадения в нормализованной строке. */
data class MatchRange(val start: Int, val end: Int)

/**
 * Все вхождения слов [terms] в [haystack] (уже нормализованный текст), слитые в интервалы.
 *
 * Соседние вхождения склеиваются — иначе «кот» в «коты» и «ты» рядом дали бы рваную
 * подсветку на каждый символ.
 */
fun matchRanges(haystack: String, terms: List<String>): List<MatchRange> {
    if (haystack.isEmpty() || terms.isEmpty()) return emptyList()

    val sorted = mutableListOf<MatchRange>()

    for (term in terms) {
        var from = 0
        while (from <= haystack.length - term.length) {
            val at = haystack.indexOf(term, from)
            if (at < 0) break

            sorted += MatchRange(at, at + term.length)
            // Сдвигаемся на символ, а не за конец совпадения: так «ааа» находится и со второго «а».
            from = at + 1
        }
    }

    if (sorted.isEmpty()) return emptyList()
    sorted.sortBy { it.start }

    val merged = mutableListOf<MatchRange>()
    var current = sorted.first()

    for (range in sorted.drop(1)) {
        current = if (range.start <= current.end) {
            MatchRange(current.start, maxOf(current.end, range.end))
        } else {
            merged += current
            range
        }
    }
    merged += current

    return merged
}

/**
 * Фрагмент текста вокруг первого совпадения: показывать весь ОП-пост в карточке треда незачем.
 * Окно в [radius] символов с многоточием на обрезанном краю.
 *
 * Если в тексте нет ни одного слова запроса, режется начало — карточка всё равно
 * должна показать хоть что-то осмысленное, а не пустую строку.
 */
fun snippetAround(text: String, terms: List<String>, radius: Int = SNIPPET_RADIUS): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.isEmpty()) return ""

    val haystack = normalizeForSearch(flat)
    val first = terms
        .map { haystack.indexOf(it) }
        .filter { it >= 0 }
        .minOrNull()

    if (first == null) {
        return flat.take(radius * 2).let { if (flat.length <= it.length) it else "$it…" }
    }

    val start = (first - radius).coerceAtLeast(0)
    val end = (first + radius).coerceAtMost(flat.length)

    val head = if (start > 0) "…" else ""
    val tail = if (end < flat.length) "…" else ""
    return head + flat.substring(start, end).trim() + tail
}

internal const val SNIPPET_RADIUS = 90
