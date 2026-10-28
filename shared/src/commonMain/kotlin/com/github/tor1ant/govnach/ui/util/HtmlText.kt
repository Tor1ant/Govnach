package com.github.tor1ant.govnach.ui.util

private val LINE_BREAK_REGEX = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>")
private val TAG_REGEX = Regex("<[^>]*>")

/**
 * Двач отдаёт комментарии с HTML разметкой, поэтому перед показом в Compose
 * разметку нужно вырезать и разобрать HTML сущности.
 */
fun String.htmlToPlainText(): String {
    val withLineBreaks = LINE_BREAK_REGEX.replace(this, "\n")
    val withoutTags = TAG_REGEX.replace(withLineBreaks, "")

    return withoutTags
        .replace("&nbsp;", " ")
        .replace("&#47;", "/")
        .replace("&#95;", "_")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .trim()
}
