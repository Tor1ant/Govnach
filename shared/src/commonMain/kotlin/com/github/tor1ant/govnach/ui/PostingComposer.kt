package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.PostingDraft
import com.github.tor1ant.govnach.client.localFile
import com.github.tor1ant.govnach.client.postingProblemsMessage
import com.github.tor1ant.govnach.client.postingRules
import com.github.tor1ant.govnach.client.validatePosting
import com.github.tor1ant.govnach.ui.posting.fileNameOf
import com.github.tor1ant.govnach.ui.posting.rememberFileDropTarget
import com.github.tor1ant.govnach.ui.posting.rememberFilePicker
import org.openapitools.client.models.Board

/**
 * Композер постинга: комментарий, плюс имя/тема когда доска их разрешает, плюс прикреплённые файлы.
 * Один блок работает и для ответа в тред ([threadNum]), и для нового треда (null).
 *
 * Файл добавляют так, как это делают в любом приложении: перетаскиванием из проводника в область
 * вложений или выбором файла в системном диалоге. Путь руками больше не вводят — его никто не знает
 * наизусть, а ошибка в одном символе давала «файл не найден» уже после нажатия «отправить».
 * Платформенные части — [rememberFilePicker] и [rememberFileDropTarget].
 *
 * [onClose] != null — композер можно свернуть: на экране треда он выезжает по требованию
 * и не должен закрывать ленту, пока пользователь не пишет. На экране нового треда его нет.
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
    onClose: (() -> Unit)? = null,
    onSend: (PostingDraft) -> Unit
) {
    var name by remember(board.id, threadNum) { mutableStateOf("") }
    var subject by remember(board.id, threadNum) { mutableStateOf("") }
    // Прикреплённые файлы живут в композере: при сворачивании они не теряются вместе с текстом.
    var files by remember(board.id, threadNum) { mutableStateOf(emptyList<String>()) }
    var dragActive by remember(board.id, threadNum) { mutableStateOf(false) }

    val rules = postingRules(board)
    val draft = PostingDraft(
        boardId = board.id,
        threadNum = threadNum,
        comment = comment.trim(),
        name = if (rules.allowName) name.takeIf { it.isNotBlank() } else null,
        subject = if (rules.allowSubject) subject.takeIf { it.isNotBlank() } else null,
        files = files
    )

    val problems = validatePosting(rules, draft)
    val canSend = enabled && !isPosting && problems.isEmpty()

    fun sendDraft() {
        if (canSend) onSend(draft)
    }

    fun attach(paths: List<String>) {
        // Дубликаты не нужны: дважды брошенный файл дал бы два одинаковых вложения.
        files = (files + paths).distinct()
    }

    val openPicker = rememberFilePicker(enabled = enabled && !isPosting, onPicked = ::attach)

    // Бросить можно только существующий файл: у остального нет ни имени, ни размера,
    // и доска всё равно ответила бы «файл не найден». Молча отбрасываем — подсказка
    // о проблемах ниже объясняет, что именно доске не понравилось.
    val dropTarget = rememberFileDropTarget(
        enabled = enabled && !isPosting,
        onDragActiveChange = { dragActive = it },
        onFilesDropped = { paths -> attach(paths.filter { localFile(it) != null }) }
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Панель отделяется от ленты тонкой линией сверху: тени в тёмной теме почти не видно.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val location = if (threadNum == null) "/${board.id}/ · новый тред" else "/${board.id}/$threadNum"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hint ?: "$location · ${comment.length}/${rules.maxComment}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hint == null && problems.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Свернуть обратно к ленте. Не отменяет набранный текст: он ждёт следующего открытия.
                onClose?.let { close ->
                    TextButton(
                        onClick = close,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("✕", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

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

                Attachments(
                    files = files,
                    dragActive = dragActive,
                    enabled = !isPosting,
                    dropTarget = dropTarget,
                    onPick = openPicker,
                    onRemove = { path -> files = files.filterNot { it == path } }
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

            // Проблемы черновика объясняем под кнопкой: раньше подсказка висела на поле пути
            // и пропадала вместе с ним.
            if (enabled && problems.isNotEmpty()) {
                Text(
                    text = postingProblemsMessage(problems, rules),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Область вложений: кнопка выбора файла и список уже прикреплённых.
 *
 * Пока файлов нет, блок служит одновременно и подсказкой, и целью перетаскивания — бросить файл
 * можно в него целиком. Когда файлы есть, перетаскивание продолжает работать: добавить второй
 * файл так же удобно, как первый.
 */
@Composable
private fun Attachments(
    files: List<String>,
    dragActive: Boolean,
    enabled: Boolean,
    dropTarget: Modifier,
    onPick: () -> Unit,
    onRemove: (String) -> Unit
) {
    val borderColor = when {
        dragActive -> MaterialTheme.colorScheme.primary
        files.isEmpty() -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val background = if (dragActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(dropTarget)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        files.forEach { path ->
            AttachmentRow(path = path, enabled = enabled, onRemove = { onRemove(path) })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPick,
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("📎 Файл", style = MaterialTheme.typography.labelMedium)
            }

            Text(
                text = when {
                    dragActive -> "отпустите, чтобы прикрепить"
                    files.isEmpty() -> "или перетащите сюда из проводника"
                    else -> "можно перетащить ещё"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (dragActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Один прикреплённый файл: имя, размер и крестик. Полный путь не показываем — он не нужен. */
@Composable
private fun AttachmentRow(path: String, enabled: Boolean, onRemove: () -> Unit) {
    val file = remember(path) { localFile(path) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "📎 ${fileNameOf(path)}",
            style = MaterialTheme.typography.labelMedium,
            color = if (file == null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        file?.let {
            Text(
                text = formatBytes(it.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            onClick = onRemove,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            Text("✕", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f МБ".format(bytes / 1_048_576.0)
    else -> "${bytes / 1024} КБ"
}