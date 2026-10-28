package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.ui.model.ThreadViewModel
import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import org.koin.compose.koinInject
import org.openapitools.client.models.FileModel
import org.openapitools.client.models.Post

/**
 * Тред: посты от ОП-поста до последнего. Открыть его можно с экрана доски.
 */
@Composable
fun ThreadScreen(
    boardId: String,
    boardName: String,
    threadNum: Int,
    threadSubject: String = "",
    onBack: () -> Unit
) {
    val client: Client = koinInject()
    val viewModel = remember(boardId, threadNum) { ThreadViewModel(client, boardId, threadNum) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("/$boardId/$threadNum", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = threadSubject.ifEmpty { boardName },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
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
                            PostCard(post = post)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: Post) {
    val isOp = post.parent == 0
    val comment = post.comment.htmlToPlainText()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isOp) {
                    it.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    it
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOp) 3.dp else 1.dp)
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
                        color = MaterialTheme.colorScheme.primary
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
                FilesList(files)
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
private fun FilesList(files: List<FileModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        files.forEach { file ->
            Text(
                text = buildString {
                    append("📎 ")
                    append(file.displayname.ifEmpty { file.name })
                    append(" · ")
                    append(file.propertySize)
                    append(" КБ · ")
                    append(file.width)
                    append("×")
                    append(file.height)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
