package com.github.tor1ant.govnach.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tor1ant.govnach.client.boardsCategories
import com.github.tor1ant.govnach.client.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.models.Board

data class HomeUiState(
    val boards: List<Board> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String? = null
)

/**
 * Список досок для главной.
 *
 * Все доски равны: категория «Взрослым» — такая же строка в фильтре и такие же карточки
 * в списке, никаких подтверждений возраста и скрытых групп здесь нет.
 */
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
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки: ${e.message}",
                    isLoading = false
                )
                client.log().failure("Главная", "не удалось загрузить список досок", e)
            }
        }
    }

    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }
}

/**
 * Доски для списка на главной: все или только выбранной категории.
 *
 * Считается из [HomeUiState], а не из вьюмодели: экран держит состояние через
 * `collectAsState()` и читает его в теле composable. Функция-геттер на вьюмодели читала бы
 * `_uiState.value` в обход отслеживаемого снимка — Compose не узнал бы, что зависимость
 * поменялась, и список не перерисовался бы на нажатие чипа.
 */
fun HomeUiState.filteredBoards(): List<Board> =
    if (selectedCategory == null) {
        boards
    } else {
        boards.filter { it.category == selectedCategory }
    }

/** Категории фильтра — все категории из ответа сервера, без скрытия чего-либо. */
fun HomeUiState.filterCategories(): List<String> = boardsCategories(boards)
