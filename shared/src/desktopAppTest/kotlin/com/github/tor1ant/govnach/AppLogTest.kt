package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.AppLog
import com.github.tor1ant.govnach.client.LogEntry
import com.github.tor1ant.govnach.client.LogLevel
import com.github.tor1ant.govnach.client.describe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Журнал консоли: порядок записей, ограничение размера и потокобезопасность.
 *
 * Последнее — не формальность: записи кладутся из сетевых колбэков, которые приходят
 * не в главном потоке, а список при этом пересобирается целиком.
 */
class AppLogTest {

    @Test
    fun entriesKeepOrderAndLevels() {
        val log = AppLog()

        log.info("HTTP", "первая")
        log.warn("HTTP", "вторая")
        log.error("HTTP", "третья")

        assertEquals(
            listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            log.entries.value.map { it.level }
        )
        assertEquals(listOf("первая", "вторая", "третья"), log.entries.value.map { it.message })
    }

    @Test
    fun oldEntriesFallOutOfCapacity() {
        val log = AppLog(capacity = 3)

        repeat(5) { log.info("HTTP", "запись $it") }

        assertEquals(3, log.entries.value.size, "журнал не должен расти бесконечно")
        assertEquals(listOf("запись 2", "запись 3", "запись 4"), log.entries.value.map { it.message })
    }

    @Test
    fun clearEmptiesJournal() {
        val log = AppLog()
        log.info("HTTP", "что-то")

        log.clear()

        assertTrue(log.entries.value.isEmpty())
        assertEquals("", log.dump())
    }

    @Test
    fun failureKeepsTypeMessageAndStack() {
        val log = AppLog()
        val cause = IllegalStateException("доска недоступна")

        log.failure("Доска /h/", "не удалось загрузить треды", cause)

        val entry = log.entries.value.single()
        assertEquals(LogLevel.ERROR, entry.level)
        val detail = entry.detail.orEmpty()
        assertTrue(detail.contains("IllegalStateException"), "тип исключения должен быть в подробностях")
        assertTrue(detail.contains("доска недоступна"), "сообщение исключения должно быть в подробностях")
        assertTrue(detail.contains("AppLogTest"), "стек должен быть в подробностях")
    }

    @Test
    fun dumpContainsEveryEntryWithDetails() {
        val log = AppLog()
        log.info("HTTP", "Список досок")
        log.error("HTTP", "Треды /h/: сервер вернул не то", detail = "статус: 500")

        val dump = log.dump()

        assertTrue(dump.contains("Список досок"))
        assertTrue(dump.contains("Треды /h/: сервер вернул не то"))
        assertTrue(dump.contains("статус: 500"), "подробности должны попадать в копию целиком")
        assertTrue(dump.indexOf("Список досок") < dump.indexOf("Треды /h/"), "порядок должен сохраняться")
    }

    @Test
    fun entryTextHasTimeLevelAndTag() {
        val entry = LogEntry(LogLevel.WARN, "HTTP", "проверка")

        val text = entry.toText()

        assertTrue(text.contains("WARN"), "уровень виден в тексте")
        assertTrue(text.contains("[HTTP]"), "источник виден в тексте")
        assertTrue(text.contains("проверка"))
        assertTrue(Regex("""^\d{2}:\d{2}:\d{2} """).containsMatchIn(text), "время идёт первым: $text")
    }

    @Test
    fun detailIsIndentedUnderMessage() {
        val entry = LogEntry(LogLevel.ERROR, "HTTP", "сообщение", detail = "строка один\nстрока два")

        val text = entry.toText()

        assertTrue(text.contains("    строка один"), "подробности с отступом: $text")
        assertTrue(text.contains("    строка два"), "каждая строка подробностей с отступом: $text")
    }

    /**
     * Записи из нескольких потоков не должны теряться и не должны ломать список:
     * проверяем, что пришло ровно столько строк, сколько положили.
     */
    @Test
    fun concurrentWritesAreNotLost() = runBlocking {
        val log = AppLog(capacity = 1000)
        val writers = 8
        val perWriter = 50

        (0 until writers).map { writer ->
            async(Dispatchers.Default) {
                repeat(perWriter) { i -> log.info("HTTP", "поток $writer запись $i") }
            }
        }.awaitAll()

        assertEquals(writers * perWriter, log.entries.value.size, "ни одна запись не должна потеряться")
        assertFalse(log.entries.value.isEmpty())
    }

    @Test
    fun describeWithoutMessageStillNamesType() {
        val text = object : RuntimeException() {}.describe()

        assertTrue(text.isNotBlank())
        assertTrue(!text.contains("null"), "без сообщения не должно появляться слово null: $text")
    }
}