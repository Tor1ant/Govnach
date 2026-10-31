package com.github.tor1ant.govnach.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tor1ant.govnach.client.boardsCategories
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.isAdult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.models.Board

/**
 * Показывать ли доски «для взрослых».
 *
 * Живёт в singleton вьюмодели, поэтому выбор один на все экраны: разрешили на главной впервые,
 * подтверждение на карточке доски и на экране постинга уже не спрашиваем.
 * Подтверждение разовое на запуск приложения — состояние не сохраняется между запусками.
 */
enum class AdultAccess {
    /** Подтверждения ещё не было: взрослые доски скрыты из списка. */
    HIDDEN,

    /** Пользователь подтвердил возраст: доски видны, треды открываются, постинг доступен. */
    ALLOWED
}

data class HomeUiState(
    val boards: List<Board> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String? = null,
    val adultAccess: AdultAccess = AdultAccess.HIDDEN,
    /** Сколько взрослых досок спрятано: по числу рисуется подсказка «показать доски 18+». */
    val hiddenAdultBoards: Int = 0
)

class HomeViewModel(
    private val client: Client
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadBoards()
    }

    fun loadBoards() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val boards = client.getBoards()
                _uiState.value = _uiState.value.copy(
                    boards = boards,
                    isLoading = false,
                    hiddenAdultBoards = boards.count { it.isAdult() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    /** Пользователь подтвердил, что ему 18+: взрослые доски появляются и в фильтре, и в списке. */
    fun allowAdult() {
        _uiState.value = _uiState.value.copy(adultAccess = AdultAccess.ALLOWED)
    }

    /** Вернуть запрет: взрослые доски снова скрыты. Открытые экраны это не закрывает. */
    fun hideAdult() {
        _uiState.value = _uiState.value.copy(
            adultAccess = AdultAccess.HIDDEN,
            selectedCategory = _uiState.value.selectedCategory.takeUnless { it == ADULT_FILTER }
        )
    }

    private val adultAllowed: Boolean get() = _uiState.value.adultAccess == AdultAccess.ALLOWED

    /** Доски для списка на главной: без взрослых, пока возраст не подтверждён. */
    fun getFilteredBoards(): List<Board> {
        val state = _uiState.value
        val visible = if (adultAllowed) state.boards else state.boards.filterNot { it.isAdult() }

        return if (state.selectedCategory == null) {
            visible
        } else {
            visible.filter { it.category == state.selectedCategory }
        }
    }

    /** Категории фильтра: категория «Взрослым» появляется вместе с досками. */
    fun getCategories(): List<String> {
        val categories = boardsCategories(_uiState.value.boards)
        return if (adultAllowed) categories else categories.filterNot { it == ADULT_FILTER }
    }

    private companion object {
        const val ADULT_FILTER = "Взрослым"
    }
}
