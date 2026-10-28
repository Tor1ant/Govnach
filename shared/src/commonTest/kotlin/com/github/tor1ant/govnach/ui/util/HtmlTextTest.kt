package com.github.tor1ant.govnach.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {

    @Test
    fun `вырезает теги`() {
        val comment = "<p>Привет, <b>анон</b>!</p>"

        assertEquals("Привет, анон!", comment.htmlToPlainText())
    }

    @Test
    fun `переносы br и p становятся переводами строк`() {
        val comment = "первая<br>вторая</p>третья"

        assertEquals("первая\nвторая\nтретья", comment.htmlToPlainText())
    }

    @Test
    fun `разбирает html сущности двача`() {
        val comment = "&gt;&gt;337787119 &#47;b&#47; &quot;цитата&quot; &amp; friends &#39;квотка&#39;"

        assertEquals(">>337787119 /b/ \"цитата\" & friends 'квотка'", comment.htmlToPlainText())
    }

    @Test
    fun `не ломается на пустом комментарии`() {
        assertEquals("", "".htmlToPlainText())
    }
}
