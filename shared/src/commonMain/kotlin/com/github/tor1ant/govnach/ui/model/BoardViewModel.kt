package com.github.tor1ant.govnach.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tor1ant.govnach.client.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview

data class BoardUiState(
    val threads: List<ThreadPreview> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Список тредов конкретной доски. Создаётся на весь экран доски.
 */
class BoardViewModel(
    private val client: Client,
    private val board: Board
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        loadThreads()
    }

    fun loadThreads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val threads = client.getThreads(board.id)
                _uiState.value = _uiState.value.copy(
                    threads = threads,
                    isLoading = false,
                    error = if (threads.isEmpty()) "На доске нет тредов" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки тредов: ${e.message}",
                    isLoading = false
                )
                client.log().failure("Доска /${board.id}/", "не удалось загрузить треды", e)
            }
        }
    }

    /**
     * Подтвердить возраст и сразу перезагрузить доску.
     *
     * Так приложение повторяет то, что делает браузер: окно «18+» записывает `ageallow`,
     * и дальше запросы идут уже с ним. Подтверждение запоминается между запусками.
     */
    fun allowAdultContent() {
        client.allowAdultContent()
        loadThreads()
    }

    /**
     * Ввести ключ доступа к закрытым разделам и перезагрузить доску.
     *
     * Ключ — это cookie `usercode_auth`, которую двач выдаёт после первого поста.
     * Браузер получает её сам, приложению её нужно один раз вставить руками.
     */
    fun applyAccessToken(token: String) {
        if (token.isBlank()) return
        client.setAccessToken(token)
        loadThreads()
    }

    /** Есть ли уже ключ: от этого зависит, показывать ли поле ввода. */
    fun hasAccessToken(): Boolean = client.hasAccessToken()
}
