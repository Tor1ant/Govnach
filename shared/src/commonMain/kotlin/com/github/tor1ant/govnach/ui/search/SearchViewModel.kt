package com.github.tor1ant.govnach.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tor1ant.govnach.client.Client
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview
import kotlin.time.Duration

/**
 * Состояние поиска.
 *
 * [query] и [phase] меняются на каждый введённый символ — по ним подписывается строка ввода.
 * [boards] и [threads] — результат фильтра: он пересчитывается после паузы дебаунсера,
 * на приезд новых данных и по Enter.
 */
data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.IDLE,
    val boards: List<BoardHit> = emptyList(),
    val threads: List<ThreadHit> = emptyList(),
    /** Данные ещё едут: список досок или тексты тредов не готовы. */
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Сколько тредов прочитано: по счётчику видно охват выдачи. */
    val scannedThreads: Int = 0,
    /** В запросе он один и это id доски: отдельного экрана «все треды доски» здесь нет. */
    val sectionOnly: Boolean = false
) {
    /** Текст в поле есть, а фильтр ещё ждёт паузы: списки ниже — от прошлого запроса. */
    val isPending: Boolean get() = phase == SearchPhase.PENDING

    val hasQuery: Boolean get() = query.isNotEmpty()

    /** Запрос отработан и не нашлось ничего. */
    val isEmpty: Boolean get() = phase == SearchPhase.READY && boards.isEmpty() && threads.isEmpty()
}

/**
 * Поиск досок и тредов из строки поиска.
 *
 * Серверного поиска у двача нет — в `openapi/api.yml` нет ни одного метода с `search`.
 * Поэтому ищем по данным, которые приложение уже умеет читать:
 *
 * - доски — полный список `/api/mobile/v2/boards`, он же кормит главную;
 * - треды — `/board/{id}.json` каждой видимой доски.
 *
 * Тексты тредов кэшируются по доске, поэтому ввод символов в сеть не ходит: сеть нужна
 * только на пустом кэше (первый поиск после запуска) и по кнопке «повторить».
 *
 * Пока пользователь печатает, фильтра нет вовсе, а список остаётся от прошлого запроса
 * с пометкой [SearchUiState.isPending] — см. [SearchQuery]. Нормализация текста и
 * пересечение слов идут на [dispatcher]: обход десятков досок на UI-потоке ронял бы кадры.
 *
 * Взрослые доски подчиняются тому же гейту, что и главная: до подтверждения возраста
 * они не показываются ни среди досок, ни среди тредов и не читаются из сети.
 */
class SearchViewModel(
    private val client: Client,
    private val dispatcher: CoroutineDispatcher = searchDispatcher,
    private val debounce: Duration = SEARCH_DEBOUNCE
) : ViewModel() {

    val query = SearchQuery()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val boards = MutableStateFlow<List<Board>>(emptyList())
    private val adultAllowed = MutableStateFlow(false)

    /** Треды по доскам: загружены один раз, дальше фильтр работает по кэшу. */
    private val threadsByBoard = MutableStateFlow<Map<Board, List<ThreadPreview>>>(emptyMap())

    /**
     * Пинок для Enter: значение меняется — фильтр гоняется по текущей строке ещё раз,
     * даже если дебаунсер по ней ещё не сработал. Счётчик, а не флаг: одинаковые нажатия
     * Enter тоже должны работать.
     */
    private val submitSignal = MutableStateFlow(0)

    private var loadJob: Job? = null

    init {
        // Строка ввода видна сразу, до всякого фильтра.
        viewModelScope.launch {
            query.state.collect { state ->
                _uiState.value = _uiState.value.copy(query = state.query, phase = state.phase)

                if (state.query.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        boards = emptyList(),
                        threads = emptyList(),
                        sectionOnly = false,
                        scannedThreads = threadsByBoard.value.values.sumOf { it.size }
                    )
                }
            }
        }

        // Дебаунсер: после паузы в наборе строка становится готовой к фильтру.
        viewModelScope.launch {
            query.ready(debounce).collect { ready -> query.accept(ready) }
        }

        // Один проход фильтра на паузу в наборе: набранная буква без паузы сюда не доходит.
        //
        // Источник прохода — `debouncedQuery`, а не сам ввод: дебаунсер пишет его через
        // [SearchQuery.accept], Enter — через [SearchQuery.flush]. Одно место правды.
        viewModelScope.launch {
            combine(
                query.state.map { it.debouncedQuery }.distinctUntilChanged(),
                boards,
                threadsByBoard,
                adultAllowed,
                submitSignal
            ) { ready, boardList, threads, adult, _ -> FilterInput(ready, boardList, threads, adult) }
                .map { input -> input to compute(input) }
                .flowOn(dispatcher)
                .collect { (input, result) ->
                    // Пока фильтр считался, строку могли поменять: такой результат устарел,
                    // а текущую строку посчитает следующий проход.
                    if (input.ready == query.state.value.query) apply(input, result)
                }
        }
    }

    /** Текст из поля поиска: строка обновляется сразу, фильтр — после паузы. */
    fun updateQuery(value: String) = query.update(value)

    /** Enter: искать сразу, не дожидаясь полусекунды. */
    fun submit() {
        query.flush()
        submitSignal.value += 1
    }

    fun clear() = query.clear()

    /** Возраст подтверждён: взрослые доски и их треды появляются в выдаче. */
    fun allowAdult() {
        if (adultAllowed.value) return
        adultAllowed.value = true
        loadData()
    }

    /** Открыли экран поиска: подтянуть доски и треды, чтобы первый ввод считался по кэшу. */
    fun preload() = loadData()

    /** Перечитать всё с сервера — кнопка «повторить» после ошибки. */
    fun refresh() {
        loadJob?.cancel()
        threadsByBoard.value = emptyMap()
        boards.value = emptyList()
        _uiState.value = _uiState.value.copy(error = null)
        loadData()
    }

    /**
     * Доски и треды в память. Идущую загрузку не дублирует: экран может просить [preload]
     * несколько раз подряд, а сеть от этого легче не станет.
     */
    private fun loadData() {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val loaded = try {
                client.getBoards()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Ошибка загрузки: ${e.message}"
                )
                return@launch
            }

            boards.value = loaded

            // Взрослые доски не читаем, пока возраст не подтверждён: контент не должен
            // оказаться в памяти раньше решения — так же поступает экран треда.
            for (board in loaded.filter { visibleForSearch(it, adultAllowed.value) }) {
                val threads = try {
                    client.getThreads(board.id)
                } catch (e: Exception) {
                    // Одна упавшая доска не должна съедать всю выдачу.
                    emptyList()
                }

                threadsByBoard.value = threadsByBoard.value + (board to threads)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /** Тяжёлая часть: нормализация и пересечение слов. На UI-потоке не вызывается. */
    private fun compute(input: FilterInput): SearchResult {
        val terms = searchTerms(input.ready)
        if (terms.isEmpty()) return SearchResult(emptyList(), emptyList(), false)

        // Запрос — это id доски («б» или «/б/»): страницы со всеми тредами доски у нас нет,
        // поэтому карточка ведёт на саму доску, а не на отдельный тред.
        val sectionOnly = terms.size == 1 && input.boards.any { it.id.equals(terms.single(), ignoreCase = true) }

        return SearchResult(
            boards = searchBoards(input.boards, terms, input.adultAllowed),
            threads = searchThreads(input.threads, terms),
            sectionOnly = sectionOnly
        )
    }

    private fun apply(input: FilterInput, result: SearchResult) {
        // Поле уже очистили: показывать выдачу по старой строке поверх пустого поиска нельзя.
        if (input.ready.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            boards = result.boards,
            threads = result.threads,
            isLoading = false,
            scannedThreads = input.threads.values.sumOf { it.size },
            sectionOnly = result.sectionOnly
        )

        // Результаты уже на экране — только теперь запрос считается отработанным.
        query.markReady(input.ready)
    }

    private data class FilterInput(
        val ready: String,
        val boards: List<Board>,
        val threads: Map<Board, List<ThreadPreview>>,
        val adultAllowed: Boolean
    )

    private data class SearchResult(
        val boards: List<BoardHit>,
        val threads: List<ThreadHit>,
        val sectionOnly: Boolean
    )
}
