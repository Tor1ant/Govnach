package com.github.tor1ant.govnach.ui.search

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Задержка поиска после последнего нажатия клавиши.
 *
 * Пока пользователь печатает, фильтр не работает: он запускается один раз через
 * [SEARCH_DEBOUNCE] после последнего символа. Пятьсот миллисекунд — компромисс: быстрый
 * набор идёт ~150–250 мс на символ, поэтому «догоняющих» проходов нет, а после паузы
 * результат не заставляет ждать.
 */
val SEARCH_DEBOUNCE: Duration = 500.milliseconds

/**
 * Фаза запроса.
 *
 * [PENDING] держится всё время, пока введённый текст ещё не дошёл до фильтра:
 * опечатки в слове не должны дёргать список результатов на каждой букве.
 * В этой фазе [SearchQueryState.debouncedQuery] отстаёт от [SearchQueryState.query].
 */
enum class SearchPhase {
    IDLE,
    PENDING,
    READY
}

/** Что введено сейчас и что уже отработал дебаунсер. */
data class SearchQueryState(
    val query: String = "",
    val debouncedQuery: String = "",
    val phase: SearchPhase = SearchPhase.IDLE
) {
    val isPending: Boolean get() = phase == SearchPhase.PENDING
}

/**
 * Строка поиска и её дебаунс.
 *
 * Поле вызывает [update] на каждый символ — это дёшево и попадает в UI сразу. Дорогой
 * фильтр подписывается на [ready] и получает строку только после паузы. Разделение
 * важно: если бы фильтр висел на вводе, он отрабатывал бы на каждую нажатую букву.
 *
 * [flush] нужен для Enter: пользователь нажал «поиск» — ждать полсекунды незачем.
 */
class SearchQuery {
    private val input = MutableStateFlow("")

    /** Состояние для индикатора «ищу…» и кнопки очистки. */
    val state: MutableStateFlow<SearchQueryState> = MutableStateFlow(SearchQueryState())

    /** Ввод из поля. Пробелы по краям срезаем — «кот» и «  кот  » это один запрос. */
    fun update(query: String) {
        val trimmed = query.trim()
        input.value = trimmed

        state.value = if (trimmed.isEmpty()) {
            SearchQueryState()
        } else {
            // Старый debouncedQuery оставляем как есть: до паузы список показывает его выдачу,
            // помеченную как PENDING, и не мигает пустотой на каждой букве.
            state.value.copy(query = trimmed, phase = SearchPhase.PENDING)
        }
    }

    /**
     * Применить введённое немедленно: Enter.
     *
     * Отмечаем строку готовой к фильтру без ожидания паузы; фазу [SearchPhase.READY]
     * выставит фильтр, когда результаты окажутся на экране ([markReady]).
     */
    fun flush() {
        val query = input.value
        state.value = if (query.isEmpty()) {
            SearchQueryState()
        } else {
            state.value.copy(query = query, debouncedQuery = query, phase = SearchPhase.PENDING)
        }
    }

    /**
     * Строки, готовые к фильтрации: каждая выходит через [debounce] после последнего ввода.
     * `distinctUntilChanged` нужен, чтобы набранный символ и Enter по той же строке
     * не гоняли фильтр дважды.
     */
    @OptIn(FlowPreview::class)
    fun ready(debounce: Duration = SEARCH_DEBOUNCE): Flow<String> =
        input.debounce(debounce).distinctUntilChanged()

    /**
     * Отметить строку отработанной фильтром — только если она всё ещё в поле.
     * После этого экран перестаёт показывать [SearchPhase.PENDING].
     *
     * Пустой строки это не касается: очистка поля сразу возвращает [SearchPhase.IDLE],
     * и отстающий проход фильтра не должен вернуть «готово» уже пустому поиску.
     */
    fun markReady(query: String) {
        if (query.isEmpty()) return

        state.update { current ->
            if (current.query == query) {
                current.copy(debouncedQuery = query, phase = SearchPhase.READY)
            } else {
                current
            }
        }
    }

    /**
     * Дебаунсер сработал: строка готова к фильтру. `debouncedQuery` запускает проход фильтра
     * на экране, а фаза остаётся [SearchPhase.PENDING] до [markReady] — чтобы «ничего
     * не найдено» не мелькало на кадр раньше результата.
     */
    fun accept(query: String) {
        state.update { current ->
            if (current.query == query) current.copy(debouncedQuery = query) else current
        }
    }

    /** Крестик в поле. */
    fun clear() = update("")
}

/** Диспетчер, на котором считается фильтр: по умолчанию — пул фоновых потоков. */
internal val searchDispatcher: CoroutineDispatcher get() = Dispatchers.Default
