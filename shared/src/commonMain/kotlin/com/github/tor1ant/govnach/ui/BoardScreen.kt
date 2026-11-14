package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.isAdult
import com.github.tor1ant.govnach.ui.model.BoardViewModel
import com.github.tor1ant.govnach.ui.media.FileKind
import com.github.tor1ant.govnach.ui.media.FileThumb
import com.github.tor1ant.govnach.ui.search.AppSearchBar
import com.github.tor1ant.govnach.ui.search.HighlightedText
import com.github.tor1ant.govnach.ui.search.SearchActionButton
import com.github.tor1ant.govnach.ui.search.searchTerms
import com.github.tor1ant.govnach.ui.search.searchThreads
import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import org.koin.compose.koinInject
import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview
import com.github.tor1ant.govnach.ui.media.kind

/**
 * Доска: список тредов. Клик по треду открывает сам тред,
 * кнопка в шапке — создание нового треда (когда доска разрешает постинг).
 *
 * Лупа в шапке ищет **треды этой доски**: на главной ищут доски, а внутри доски —
 * её содержимое. Поиск идёт по теме и тексту ОП-поста, подходящее подсвечивается
 * прямо в карточке треда.
 *
 * Доски категории «Взрослым» ничем не отличаются: треды грузятся сразу, постинг доступен
 * по общему правилу доски.
 */
@Composable
fun BoardScreen(
    board: Board,
    onThreadClick: (ThreadPreview) -> Unit,
    onNewThreadClick: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val client: Client = koinInject()
    val adultBoard = board.isAdult()
    val viewModel = remember(board.id) { BoardViewModel(client, board) }
    val uiState by viewModel.uiState.collectAsState()

    // Поиск по тредам доски: лупа в шапке раскрывается в строку ввода.
    var searching by remember(board.id) { mutableStateOf(false) }
    var query by remember(board.id) { mutableStateOf("") }

    val terms = searchTerms(query)
    // Треды уже загружены вьюмоделью: фильтр идёт по памяти и в сеть не ходит.
    val threads = if (terms.isEmpty()) {
        uiState.threads
    } else {
        searchThreads(mapOf(board to uiState.threads), terms).map { it.thread }
    }

    fun closeSearch() {
        searching = false
        query = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (searching) {
                AppSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hint = "Поиск тредов на /${board.id}/",
                    isSearching = false,
                    onSubmit = {},
                    onClear = { query = "" },
                    autoFocus = true,
                    onClose = ::closeSearch
                )
            } else {
                AppTopBar(
                    title = {
                        Column {
                            Text(
                                text = if (adultBoard) "🔞 /${board.id}/" else "/${board.id}/",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = board.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = { BackButton(onClick = onBack) },
                    actions = {
                        SearchActionButton(onClick = { searching = true })

                        if (onNewThreadClick != null && board.enablePosting) {
                            TextButton(onClick = onNewThreadClick) {
                                Text("+ тред", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "Неизвестная ошибка",
                        onRetry = { viewModel.loadThreads() }
                    )
                }

                threads.isEmpty() -> {
                    EmptyState(
                        text = if (terms.isNotEmpty() && uiState.threads.isNotEmpty()) {
                            "На /${board.id}/ ничего не найдено по запросу «$query»"
                        } else {
                            "На доске пока нет тредов"
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(threads, key = { it.num }) { thread ->
                            ThreadCard(
                                thread = thread,
                                terms = terms,
                                onClick = {
                                    closeSearch()
                                    onThreadClick(thread)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка треда. [terms] — слова запроса из строки поиска доски: совпадения
 * подсвечиваются в теме и в тексте ОП-поста, чтобы было видно, почему тред в выдаче.
 */
@Composable
fun ThreadCard(
    thread: ThreadPreview,
    onClick: () -> Unit,
    terms: List<String> = emptyList()
) {
    val comment = thread.comment.htmlToPlainText()

    AppCard(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                val title = thread.subject?.ifEmpty { null } ?: comment.ifEmpty { "Тред ${thread.num}" }

                if (terms.isEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    HighlightedText(
                        text = title,
                        terms = terms,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ThreadBadges(thread)
            }

            ThreadOpThumbs(thread = thread, onClick = onClick)

            if (comment.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                HighlightedText(
                    text = comment.snippet(160),
                    terms = terms,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "/${thread.board}/${thread.num}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "постов: ${thread.postsCount ?: 0} · файлов: ${thread.filesCount ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Превью медиаконтента ОП-поста треда: картинки и видео рисует превью.
 */
@Composable
private fun ThreadOpThumbs(thread: ThreadPreview, onClick: () -> Unit) {
    val thumbs = thread.files.orEmpty().filter {
        val kind = it.kind()
        kind == FileKind.Image || kind == FileKind.Video
    }

    if (thumbs.isEmpty()) return

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        thumbs.take(3).forEach { file ->
            FileThumb(
                file = file,
                onClick = onClick,
                maxThumbWidth = 140f,
                maxThumbHeight = 100f
            )
        }
    }
}

@Composable
private fun ThreadBadges(thread: ThreadPreview) {
    val sticky = (thread.sticky ?: 0) > 0
    val closed = (thread.closed ?: 0) > 0

    if (!sticky && !closed) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        if (sticky) {
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text("📌", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (closed) {
            Badge(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text("🔒", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Пустой список: подсказка по центру вместо пустого экрана. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun String.snippet(limit: Int): String {
    val flattened = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (flattened.length <= limit) flattened else flattened.take(limit).trimEnd() + "…"
}
