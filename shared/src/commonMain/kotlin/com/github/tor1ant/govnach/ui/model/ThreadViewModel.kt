package com.github.tor1ant.govnach.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tor1ant.govnach.client.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.models.Post

data class ThreadUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Постовый список одного треда. Создаётся на весь экран треда.
 */
class ThreadViewModel(
    private val client: Client,
    private val boardId: String,
    private val threadNum: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    init {
        loadPosts()
    }

    fun loadPosts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val posts = client.getThreadPosts(boardId, threadNum)
                _uiState.value = _uiState.value.copy(
                    posts = posts,
                    isLoading = false,
                    error = if (posts.isEmpty()) "Тред пуст или был удалён" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки треда: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
}
