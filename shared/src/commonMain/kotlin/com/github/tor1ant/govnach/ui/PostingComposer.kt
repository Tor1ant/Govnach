package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.PostingDraft
import com.github.tor1ant.govnach.client.localFile
import com.github.tor1ant.govnach.client.postingProblemsMessage
import com.github.tor1ant.govnach.client.postingRules
import com.github.tor1ant.govnach.client.validatePosting
import org.openapitools.client.models.Board

/**
 * Композер постинга: комментарий, плюс имя/тема когда доска их разрешает, плюс файл по пути.
 * Один блок работает и для ответа в тред ([threadNum]), и для нового треда (null).
 *
 * Поле файла — именно путь: компонента выбора файлов в Compose нет, а двачу нужен байтовый аплоад.
 */
@Composable
fun PostingComposer(
    board: Board,
    threadNum: Int?,
    comment: String,
    onCommentChange: (String) -> Unit,
    commentFocus: FocusRequester,
    enabled: Boolean,
    hint: String?,
    isPosting: Boolean,
    sendLabel: String = "→",
    onSend: (PostingDraft) -> Unit
) {
    var name by remember(board.id, threadNum) { mutableStateOf("") }
    var subject by remember(board.id, threadNum) { mutableStateOf("") }
    var filePath by remember(board.id, threadNum) { mutableStateOf("") }

    val rules = postingRules(board)
    val draft = PostingDraft(
        boardId = board.id,
        threadNum = threadNum,
        comment = comment.trim(),
        name = if (rules.allowName) name.takeIf { it.isNotBlank() } else null,
        subject = if (rules.allowSubject) subject.takeIf { it.isNotBlank() } else null,
        files = listOf(filePath).filter { it.isNotBlank() }
    )

    val problems = validatePosting(rules, draft)
    val canSend = enabled && !isPosting && problems.isEmpty()
    val attached = filePath.ifBlank { null }?.let(::localFile)

    fun sendDraft() {
        if (canSend) onSend(draft)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val location = if (threadNum == null) "/${board.id}/ · новый тред" else "/${board.id}/$threadNum"
            Text(
                text = buildString {
                    append(hint ?: "$location · ${comment.length}/${rules.maxComment}")
                    attached?.let {
                        append(" · 📎 ")
                        append(it.name)
                        append(" (")
                        append(formatBytes(it.sizeBytes))
                        append(")")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (hint == null && problems.isNotEmpty()) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (enabled) {
                if (rules.allowName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Имя") },
                        placeholder = { Text(board.defaultName) },
                        singleLine = true,
                        enabled = !isPosting
                    )
                }

                if (rules.allowSubject) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (threadNum == null) "Тема треда" else "Тема") },
                        singleLine = true,
                        enabled = !isPosting
                    )
                }

                OutlinedTextField(
                    value = filePath,
                    onValueChange = { path -> filePath = path.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Файл (путь)") },
                    placeholder = { Text(if (threadNum == null) "обязателен для нового треда" else "не обязателен") },
                    supportingText = {
                        if (problems.isNotEmpty()) {
                            Text(postingProblemsMessage(problems, rules), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    enabled = !isPosting
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = onCommentChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(commentFocus),
                    placeholder = { Text(if (threadNum == null) "Первый пост треда" else "Написать ответ") },
                    minLines = 1,
                    maxLines = 5,
                    enabled = enabled && !isPosting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendDraft() })
                )

                Button(onClick = { sendDraft() }, enabled = canSend) {
                    Text(if (isPosting) "…" else sendLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f МБ".format(bytes / 1_048_576.0)
    else -> "${bytes / 1024} КБ"
}
