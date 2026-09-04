package com.github.tor1ant.govnach.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class HomeViewModel(
    private val client: Client
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadBoards()
    }

    private fun loadBoards() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // TODO: Загрузить данные с сервера
                // Пока что используем мок-данные
                val mockBoards = getBoards()
                _uiState.value = _uiState.value.copy(
                    boards = mockBoards,
                    isLoading = false
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

    fun getFilteredBoards(): List<Board> {
        val state = _uiState.value
        return if (state.selectedCategory == null) {
            state.boards
        } else {
            state.boards.filter { it.category == state.selectedCategory }
        }
    }

    fun getCategories(): List<String> {
        return _uiState.value.boards
            .map { it.category }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    private suspend fun getBoards(): List<Board> {
        return client.getBoards();
    }
}

