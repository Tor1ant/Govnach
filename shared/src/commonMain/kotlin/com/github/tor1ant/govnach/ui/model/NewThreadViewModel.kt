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

data class NewThreadUiState(
    val isPosting: Boolean = false,
    val postError: String? = null,
    /** Номер созданного треда и его тема — экран по ним открывает новый тред. */
    val createdThread: Int? = null,
    val createdSubject: String? = null,
    /** Открытая emoji капча: тред создаётся только после её решения. */
    val captcha: EmojiCaptcha? = null,
    val isCaptchaBusy: Boolean = false,
    val captchaError: String? = null,
    /** Сколько emoji уже выбрано в текущей капче. */
    val captchaPicks: Int = 0
)

/** Создание нового треда на доске; ОП-пост без файла двач не принимает. */
class NewThreadViewModel(
    private val client: Client
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewThreadUiState())
    val uiState: StateFlow<NewThreadUiState> = _uiState.asStateFlow()

    private var pendingDraft: PostingDraft? = null

    /** [draft] приходит из композера и уже прошёл локальную проверку. */
    fun sendDraft(draft: PostingDraft) {
        pendingDraft = draft.copy(threadNum = null)
        _uiState.value = _uiState.value.copy(captcha = null, captchaError = null)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCaptchaBusy = true, postError = null, createdThread = null)

            try {
                val captcha = client.requestEmojiCaptcha(draft.boardId, threadNum = null)
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
                val captcha = client.requestEmojiCaptcha(draft.boardId, threadNum = null)
                _uiState.value = _uiState.value.copy(isCaptchaBusy = false, captcha = captcha)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCaptchaBusy = false,
                    captchaError = e.message ?: "Не удалось получить другую капчу"
                )
            }
        }
    }

    fun cancelCaptcha() {
        pendingDraft = null
        _uiState.value = _uiState.value.copy(captcha = null, captchaError = null, captchaPicks = 0)
    }

    fun consumePostResult() {
        _uiState.value = _uiState.value.copy(postError = null, createdThread = null)
    }

    private fun publish(captchaId: String?) {
        val draft = pendingDraft ?: return
        pendingDraft = null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true, postError = null, createdThread = null)

            try {
                val result = client.post(draft, emojiCaptchaId = captchaId)
                _uiState.value = _uiState.value.copy(
                    isPosting = false,
                    createdThread = result.thread ?: result.num,
                    createdSubject = draft.subject?.ifEmpty { null }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPosting = false,
                    postError = e.message ?: "Не удалось создать тред"
                )
            }
        }
    }
}
