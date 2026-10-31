package com.github.tor1ant.govnach.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.EmojiCaptcha
import com.github.tor1ant.govnach.client.PostingDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.models.Post

data class ThreadUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPosting: Boolean = false,
    val postError: String? = null,
    val postedNum: Int? = null,
    /** Открытая emoji капча: пост уйдёт только после её решения. */
    val captcha: EmojiCaptcha? = null,
    val isCaptchaBusy: Boolean = false,
    val captchaError: String? = null,
    /** Сколько emoji уже выбрано в текущей капче. */
    val captchaPicks: Int = 0
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

    /** Черновик, который ждёт решения капчи. */
    private var pendingDraft: PostingDraft? = null

    // Загрузку запускает экран: на взрослой доске без подтверждения возраста тред
    // не запрашивается вовсе (см. ThreadScreen).

    /** [silent] — перезагрузка без спиннера: список остаётся на экране. */
    fun loadPosts(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

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

    /** Отправка ответа в этот же тред. Композер проверяет черновик до вызова. */
    fun sendDraft(draft: PostingDraft) {
        pendingDraft = draft.copy(threadNum = threadNum)
        _uiState.value = _uiState.value.copy(captcha = null, captchaError = null)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCaptchaBusy = true, postError = null, postedNum = null)

            try {
                val captcha = client.requestEmojiCaptcha(boardId, threadNum)
                _uiState.value = _uiState.value.copy(isCaptchaBusy = false, captcha = captcha)
            } catch (e: Exception) {
                pendingDraft = null
                _uiState.value = _uiState.value.copy(
                    isCaptchaBusy = false,
                    postError = e.message ?: "Не удалось получить капчу"
                )
            }
        }
    }

    /** Клик по варианту emoji: на сервер уходит номер, обратно — новая клавиатура или решение. */
    fun clickCaptcha(emojiNumber: Int) {
        val captcha = _uiState.value.captcha ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCaptchaBusy = true, captchaError = null)

            try {
                val next = client.clickEmojiCaptcha(captcha.id, emojiNumber)

                if (next.isSolved) {
                    _uiState.value = _uiState.value.copy(isCaptchaBusy = false, captcha = null, captchaPicks = 0)
                    publish(next.solved)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCaptchaBusy = false,
                        captcha = next,
                        captchaPicks = _uiState.value.captchaPicks + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCaptchaBusy = false,
                    captchaError = e.message ?: "Капча не принята, попробуйте ещё раз"
                )
            }
        }
    }

    /** Взять другую капчу: прежняя уже «испорчена» неверным выбором. */
    fun refreshCaptcha() {
        val draft = pendingDraft ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCaptchaBusy = true, captchaError = null, captchaPicks = 0)

            try {
                val captcha = client.requestEmojiCaptcha(draft.boardId, draft.threadNum ?: threadNum)
                _uiState.value = _uiState.value.copy(isCaptchaBusy = false, captcha = captcha)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCaptchaBusy = false,
                    captchaError = e.message ?: "Не удалось получить другую капчу"
                )
            }
        }
    }

    /** Капча отклонена: пост не отправляем, черновик забываем. */
    fun cancelCaptcha() {
        pendingDraft = null
        _uiState.value = _uiState.value.copy(captcha = null, captchaError = null, captchaPicks = 0)
    }

    private fun publish(captchaId: String?) {
        val draft = pendingDraft ?: return
        pendingDraft = null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true, postError = null, postedNum = null)

            try {
                val result = client.post(draft, emojiCaptchaId = captchaId)
                _uiState.value = _uiState.value.copy(isPosting = false, postedNum = result.num ?: threadNum)
                loadPosts(silent = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPosting = false,
                    postError = e.message ?: "Не удалось отправить пост"
                )
            }
        }
    }

    fun consumePostResult() {
        _uiState.value = _uiState.value.copy(postError = null, postedNum = null)
    }
}
