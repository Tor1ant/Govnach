package com.github.tor1ant.govnach.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Уровень записи журнала: обычный ход работы, предупреждение и ошибка. */
enum class LogLevel { INFO, WARN, ERROR }

/**
 * Одна запись журнала.
 *
 * [detail] — то, что не помещается в строку списка: тело ответа, стек, параметры запроса.
 * Показывается в развёрнутой карточке и уходит в буфер обмена вместе с остальным текстом.
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val detail: String? = null,
    val timestamp: Instant = now()
) {
    /** Время в виде `12:34:56` — часовой пояс устройства. */
    val time: String get() = timestamp.toString().substringAfter('T').take(8)

    /** Строка для буфера обмена: время, уровень, источник и текст. */
    fun toText(): String = buildString {
        append(time)
        append(' ')
        append(level.name.padEnd(5))
        append(" [")
        append(tag)
        append("] ")
        append(message)
        detail?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(it.prependIndent("    "))
        }
    }
}

/** Время сейчас; вынесено, чтобы не тащить clock в каждую запись. */
@OptIn(ExperimentalTime::class)
private fun now(): Instant = Clock.System.now()

/**
 * Журнал приложения для встроенной консоли.
 *
 * Держит последние [capacity] записей: консоль нужна, чтобы посмотреть, что произошло
 * только что, а не как архив. Записи кладутся из корутин и из любого потока
 * (OkHttp зовёт колбэки не в главном), поэтому список обновляется неизменяемо и под мьютексом:
 * [update] на [MutableStateFlow] потокобезопасен сам по себе, а вот «прочитать-изменить-записать»
 * по нему — нет.
 */
class AppLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Записи от старых к новым: консоль показывает их в том же порядке, что и время. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun info(tag: String, message: String, detail: String? = null) =
        add(LogEntry(LogLevel.INFO, tag, message, detail))

    fun warn(tag: String, message: String, detail: String? = null) =
        add(LogEntry(LogLevel.WARN, tag, message, detail))

    fun error(tag: String, message: String, detail: String? = null) =
        add(LogEntry(LogLevel.ERROR, tag, message, detail))

    /**
     * Запись об исключении: сообщение исключения в [message], его тип и стек — в [detail].
     * Так в консоли видно и краткую причину, и полный стек для багрепорта.
     */
    fun failure(tag: String, message: String, throwable: Throwable) {
        add(LogEntry(LogLevel.ERROR, tag, message, throwable.describe()))
    }

    fun add(entry: LogEntry) {
        synchronized(lock) {
            _entries.update { current ->
                val next = current + entry
                if (next.size <= capacity) next else next.takeLast(capacity)
            }
        }
    }

    fun clear() {
        synchronized(lock) { _entries.value = emptyList() }
    }

    /** Всё содержимое журнала одним текстом — для «скопировать всё». */
    fun dump(): String = _entries.value.joinToString("\n\n") { it.toText() }

    private companion object {
        /** Хватает, чтобы пролистать последнюю сессию, и не растёт с памятью бесконечно. */
        const val DEFAULT_CAPACITY = 500
    }
}

/** Тип исключения, сообщение и стек — в одном тексте. */
fun Throwable.describe(): String = buildString {
    append(this@describe::class.simpleName ?: this@describe::class.toString())
    message?.let {
        append(": ")
        append(it)
    }
    val stack = stackTraceToString()
    if (stack.isNotBlank()) {
        append('\n')
        append(stack.trim())
    }
}