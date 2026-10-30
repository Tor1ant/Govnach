package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.clickable
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
import com.github.tor1ant.govnach.ui.model.BoardViewModel
import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import org.koin.compose.koinInject
import org.openapitools.client.models.Board
import org.openapitools.client.models.ThreadPreview

/**
 * Доска: список тредов. Клик по треду открывает сам тред,
 * кнопка в шапке — создание нового треда (когда доска разрешает постинг).
 */
@Composable
fun BoardScreen(
    board: Board,
    onThreadClick: (ThreadPreview) -> Unit,
    onNewThreadClick: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val client: Client = koinInject()
    val viewModel = remember(board.id) { BoardViewModel(client, board) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("/${board.id}/", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (onNewThreadClick != null && board.enablePosting) {
                        TextButton(onClick = onNewThreadClick) {
                            Text("+ новый тред")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.threads, key = { it.num }) { thread ->
                            ThreadCard(
                                thread = thread,
                                onClick = { onThreadClick(thread) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreadCard(
    thread: ThreadPreview,
    onClick: () -> Unit
) {
    val comment = thread.comment.htmlToPlainText()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = thread.subject?.ifEmpty { null } ?: comment.ifEmpty { "Тред ${thread.num}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                ThreadBadges(thread)
            }

            if (comment.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = comment.snippet(160),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "/${thread.board}/${thread.num}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Постов: ${thread.postsCount ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Файлов: ${thread.filesCount ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadBadges(thread: ThreadPreview) {
    val sticky = (thread.sticky ?: 0) > 0
    val closed = (thread.closed ?: 0) > 0

    if (!sticky && !closed) return

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (sticky) {
            Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                Text("📌", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (closed) {
            Badge(containerColor = MaterialTheme.colorScheme.error) {
                Text("🔒", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Кнопка возврата на экран вверх. Иконки не тянем — dependency на material-icons не добавляем.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("←", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

private fun String.snippet(limit: Int): String {
    val flattened = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (flattened.length <= limit) flattened else flattened.take(limit).trimEnd() + "…"
}
