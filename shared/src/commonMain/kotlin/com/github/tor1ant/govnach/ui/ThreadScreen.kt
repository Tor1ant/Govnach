package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.ui.media.FileKind
import com.github.tor1ant.govnach.ui.media.FileMetaLine
import com.github.tor1ant.govnach.ui.media.FileThumb
import com.github.tor1ant.govnach.ui.media.MediaPaths
import com.github.tor1ant.govnach.ui.media.MediaViewer
import com.github.tor1ant.govnach.ui.media.MediaViewerRequest
import com.github.tor1ant.govnach.ui.media.PlatformMediaOpener
import com.github.tor1ant.govnach.ui.media.formatSizeKb
import com.github.tor1ant.govnach.ui.media.kind
import com.github.tor1ant.govnach.ui.media.mediaPlatformContext
import com.github.tor1ant.govnach.ui.model.ThreadViewModel
import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import com.github.tor1ant.govnach.client.Client
import org.koin.compose.koinInject
import org.openapitools.client.models.Board
import org.openapitools.client.models.FileModel
import org.openapitools.client.models.Post

/**
 * Тред: посты от ОП-поста до последнего плюс композер ответа.
 * Открыть его можно с экрана доски.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThreadScreen(
    board: Board,
    threadNum: Int,
    threadSubject: String = "",
    onBack: () -> Unit
) {
    val boardId = board.id
    val mediaOpener: PlatformMediaOpener = koinInject()
    val mediaContext = mediaPlatformContext
    val client: Client = koinInject()
    val viewModel = remember(boardId, threadNum) { ThreadViewModel(client, boardId = boardId, threadNum = threadNum) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var comment by remember(boardId, threadNum) { mutableStateOf("") }
    val commentFocus = remember { FocusRequester() }

    // Просмотр медиа живёт поверх треда в собственном окне (на desktop — окне ОС),
    // а лента при этом остаётся на месте.
    var viewingFile: FileModel? by remember { mutableStateOf(null) }
    var playingFile: FileModel? by remember { mutableStateOf(null) }
    val mediaOverlayOpen = viewingFile != null || playingFile != null

    // На Android системный «назад» сначала закрывает просмотр медиа, а не выходит из треда.
    BackHandler(enabled = mediaOverlayOpen) {
        viewingFile = null
        playingFile = null
    }

    val threadClosed = uiState.posts.firstOrNull()?.closed?.let { it > 0 } ?: false
    val canReply = board.enablePosting && !threadClosed && !uiState.isLoading && uiState.posts.isNotEmpty()
    val composerHint = when {
        !board.enablePosting -> "/$boardId/: постинг запрещён"
        threadClosed -> "Тред закрыт: отвечать нельзя"
        else -> null
    }

    LaunchedEffect(uiState.postedNum, uiState.postError) {
        when {
            uiState.postedNum != null -> {
                snackbarHostState.showSnackbar("Ответ опубликован: № ${uiState.postedNum}")
                comment = ""
                viewModel.consumePostResult()
            }

            uiState.postError != null -> {
                snackbarHostState.showSnackbar(uiState.postError ?: "Не удалось отправить пост")
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
                        Text("/$boardId/$threadNum", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = threadSubject.ifEmpty { board.name },
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
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when {
                            uiState.isLoading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }

                            uiState.error != null -> {
                                ErrorState(
                                    message = uiState.error ?: "Неизвестная ошибка",
                                    onRetry = { viewModel.loadPosts() }
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.posts, key = { it.num }) { post ->
                                        PostCard(
                                            post = post,
                                            onImageClick = { viewingFile = it },
                                            onVideoClick = { playingFile = it },
                                            onExternalClick = { file ->
                                                mediaOpener.openFile(
                                                    context = mediaContext,
                                                    file = file,
                                                    url = MediaPaths.src(file),
                                                    pageUrl = threadPageUrl(boardId, threadNum)
                                                )
                                            },
                                            onReplyTo = if (canReply) {
                                                {
                                                    comment = appendCrosspost(comment, post.num)
                                                    commentFocus.requestFocus()
                                                }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PostingComposer(
                        board = board,
                        threadNum = threadNum,
                        comment = comment,
                        onCommentChange = { comment = it },
                        commentFocus = commentFocus,
                        enabled = canReply,
                        hint = composerHint,
                        isPosting = uiState.isPosting,
                        onSend = { draft -> viewModel.sendDraft(draft) }
                    )
                }

                // Просмотр медиа: на desktop открывается отдельное окно ОС, на Android —
                // панель поверх приложения. Контент в окне привязан к его краям.
                val viewing = viewingFile
                val playing = playingFile
                val openMedia = viewing ?: playing
                MediaViewer(
                    request = openMedia?.let { MediaViewerRequest(it, pageUrl = threadPageUrl(boardId, threadNum)) },
                    onDismiss = {
                        viewingFile = null
                        playingFile = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
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
}

private fun appendCrosspost(comment: String, postNum: Int): String {
    val reference = ">>$postNum "
    return if (comment.contains(reference.removeSuffix(" "))) comment else comment + reference
}

/** Страница треда на дваче: на неё уходит «открыть в браузере» из окна просмотра. */
private fun threadPageUrl(boardId: String, threadNum: Int): String =
    "https://2ch.hk/$boardId/res/$threadNum.html"

@Composable
fun PostCard(
    post: Post,
    onImageClick: (FileModel) -> Unit = {},
    onVideoClick: (FileModel) -> Unit = {},
    onExternalClick: (FileModel) -> Unit = {},
    onReplyTo: (() -> Unit)? = null
) {
    val isOp = post.parent == 0
    val comment = post.comment.htmlToPlainText()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isOp) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isOp) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "№ ${post.num}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = onReplyTo
                            ?.let { handler ->
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = handler)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            }
                            ?: Modifier
                    )

                    val name = buildString {
                        append(post.name?.ifEmpty { null } ?: "Аноним")
                        post.trip?.ifEmpty { null }?.let { append(" $it") }
                    }

                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (post.sticky > 0 && isOp) {
                        Text(
                            text = " 📌",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (post.closed > 0 && isOp) {
                        Text(
                            text = " 🔒",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Text(
                    text = post.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val subject = post.subject?.ifEmpty { null }
            if (subject != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (comment.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val files = post.files.orEmpty()
            if (files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PostMedia(
                    files = files,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onExternalClick = onExternalClick
                )
            }

            val likes = post.likes ?: 0
            val dislikes = post.dislikes ?: 0
            if (likes != 0 || dislikes != 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "👍 $likes  👎 $dislikes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PostMedia(
    files: List<FileModel>,
    onImageClick: (FileModel) -> Unit,
    onVideoClick: (FileModel) -> Unit,
    onExternalClick: (FileModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { file ->
            when (file.kind()) {
                FileKind.Image -> {
                    FileThumb(file = file, onClick = { onImageClick(file) })
                    FileMetaLine(file = file, extra = file.duration.orEmpty().ifEmpty { null })
                }

                FileKind.Video -> {
                    FileThumb(file = file, onClick = { onVideoClick(file) })
                    FileMetaLine(
                        file = file,
                        extra = buildString {
                            append("▶ video")
                            file.duration?.ifEmpty { null }?.let { append(" · $it") }
                        }
                    )
                }

                FileKind.Sticker -> {
                    FileThumb(file = file, onClick = { onImageClick(file) })
                    FileMetaLine(file = file, extra = "🃏 стикер")
                }

                FileKind.Audio, FileKind.Other -> {
                    TextButton(onClick = { onExternalClick(file) }) {
                        Text(
                            text = buildString {
                                append(if (file.kind() == FileKind.Audio) "🎵 " else "📎 ")
                                append(file.displayname.ifEmpty { file.name })
                                append(" · ")
                                append(formatSizeKb(file.propertySize))
                                file.duration?.ifEmpty { null }?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
