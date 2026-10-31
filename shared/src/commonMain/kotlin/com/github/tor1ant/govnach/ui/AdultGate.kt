package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Подтверждение возраста перед показом досок «для взрослых».
 *
 * Двач не отдаёт признак «доска 18+» отдельным полем, поэтому решает клиент:
 * до подтверждения взрослые доски не показываются в списке, треды по ним не открываются,
 * а композер ответа не включается. После подтверждения доступ обычный: чтение и постинг.
 */
@Composable
fun AdultGateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Контент 18+",
    body: String = "Доски категории «Взрослым» содержат материалы для взрослых. " +
        "Подтвердите, что вам исполнилось 18 лет."
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Выбор действует до перезапуска приложения. Список всегда можно скрыть обратно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Мне есть 18")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * Плашка под фильтром категорий: сколько взрослых досок спрятано и кнопка, открывающая гейт.
 * Показывается только пока возраст не подтверждён — иначе места на главной не занимает.
 */
@Composable
fun AdultBoardsNotice(
    hiddenBoards: Int,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hiddenBoards <= 0) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Скрыто досок 18+: $hiddenBoards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onEnable) {
            Text("Показать")
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

/**
 * Заглушка вместо списка тредов: доска взрослая, а подтверждения ещё не было.
 * Прячет контент до решения, но не блокирует навигацию молча.
 */
@Composable
fun AdultLockedState(
    boardName: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "/$boardName/ · 18+",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Доска категории «Взрослым» скрыта, пока вы не подтвердите возраст.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onUnlock) {
            Text("Мне есть 18")
        }
    }
}
