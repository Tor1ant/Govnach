package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.needsAdultConfirmation
import com.github.tor1ant.govnach.ui.model.NewThreadViewModel
import org.koin.compose.koinInject
import org.openapitools.client.models.Board

/**
 * Создание нового треда на доске: композер с обязательным файлом в ОП-посте.
 * Открывается кнопкой «+ новый тред» на экране доски.
 *
 * На взрослой доске без подтверждения возраста композер неактивен — ровно так же,
 * как на доске с запрещённым постингом: гейт один [AdultAccess] на всё приложение.
 */
@Composable
fun NewThreadScreen(
    board: Board,
    onCreated: (threadNum: Int, subject: String?) -> Unit,
    onBack: () -> Unit,
    adultAllowed: Boolean = true,
    onAdultConfirmed: () -> Unit = {}
) {
    val client: Client = koinInject()
    val viewModel = remember(board.id) { NewThreadViewModel(client) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var askAdult by remember(board.id) { mutableStateOf(false) }

    var comment by remember(board.id) { mutableStateOf("") }
    val commentFocus = remember { FocusRequester() }

    val adultLocked = needsAdultConfirmation(board, adultAllowed)
    val canPost = board.enablePosting && !adultLocked
    val composerHint = when {
        adultLocked -> "/${board.id}/ · 18+: подтвердите возраст, чтобы писать"
        !board.enablePosting -> "/${board.id}/: постинг запрещён"
        else -> null
    }

    // Открыли форму на взрослой доске без подтверждения возраста — сразу спрашиваем, а не ждём клика.
    LaunchedEffect(board.id) {
        if (adultLocked) askAdult = true
    }

    LaunchedEffect(uiState.createdThread, uiState.postError) {
        when {
            uiState.createdThread != null -> {
                val threadNum = uiState.createdThread
                val subject = uiState.createdSubject

                snackbarHostState.showSnackbar("Тред создан: № $threadNum")
                comment = ""
                viewModel.consumePostResult()

                if (threadNum != null) onCreated(threadNum, subject)
            }

            uiState.postError != null -> {
                snackbarHostState.showSnackbar(uiState.postError ?: "Не удалось создать тред")
                viewModel.consumePostResult()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text("/${board.id}/ · новый тред", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "Двач не создаёт тред без файла: укажите путь к картинке или видео в композере ниже.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            PostingComposer(
                board = board,
                threadNum = null,
                comment = comment,
                onCommentChange = { comment = it },
                commentFocus = commentFocus,
                enabled = canPost,
                hint = composerHint,
                isPosting = uiState.isPosting,
                sendLabel = "Создать тред",
                onSend = { draft -> viewModel.sendDraft(draft) }
            )
        }

        uiState.captcha?.let { captcha ->
            EmojiCaptchaDialog(
                captcha = captcha,
                picks = uiState.captchaPicks,
                isBusy = uiState.isCaptchaBusy,
                error = uiState.captchaError,
                onEmojiClick = { index -> viewModel.clickCaptcha(index) },
                onRefresh = { viewModel.refreshCaptcha() },
                onCancel = { viewModel.cancelCaptcha() }
            )
        }
    }

    if (askAdult && adultLocked) {
        AdultGateDialog(
            title = "Доска «Взрослым»",
            body = "Постинг на доске /${board.id}/ «${board.name}» доступен только для пользователей 18+. " +
                "Подтвердите возраст, чтобы создать тред.",
            onConfirm = {
                askAdult = false
                onAdultConfirmed()
            },
            onDismiss = {
                askAdult = false
                onBack()
            }
        )
    }
}
