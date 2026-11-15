package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.AppLog
import com.github.tor1ant.govnach.client.LogEntry
import com.github.tor1ant.govnach.client.LogLevel
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

/**
 * Консоль приложения: журнал сетевых запросов и ошибок.
 *
 * Нужна, чтобы разбираться в проблемах без отладчика: у доски, которая не открылась,
 * здесь видно статус ответа, `content-type` и первые символы тела. Любую запись можно
 * скопировать целиком — вместе со стеком — и вставить в переписку.
 *
 * Кнопка «копировать всё» отдаёт журнал одним текстом; «очистить» освобождает экран,
 * чтобы новая попытка была видна отдельно от прошлой.
 */
@Composable
fun ConsoleScreen(
    onBack: () -> Unit,
    log: AppLog = koinInject()
) {
    val entries by log.entries.collectAsState()
    // LocalClipboardManager помечен устаревшим в пользу LocalClipboard, но тот требует
    // собирать ClipEntry своим для каждой платформы (на desktop — AWT Transferable,
    // на Android — ClipData). Здесь нужен только текст, а этот API в commonMain один.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    // Фильтр по уровню: «всё» по умолчанию, дальше — только предупреждения или только ошибки.
    var filter by remember { mutableStateOf<LogLevel?>(null) }
    // Номер записи, которую развернули: подробности показываются у одной карточки за раз.
    var expanded by remember { mutableStateOf(-1) }
    // Подтверждение копирования/очистки: без него нажатие выглядит как «ничего не произошло».
    var notice by remember { mutableStateOf<String?>(null) }

    val shown = remember(entries, filter) {
        val level = filter
        if (level == null) entries else entries.filter { it.level == level }
    }
    val listState = rememberLazyListState()

    // Новая запись — прокручиваем вниз: консоль читают с конца.
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty()) listState.animateScrollToItem(shown.lastIndex)
    }

    fun copy(text: String, what: String) {
        clipboard.setText(AnnotatedString(text))
        notice = "Скопировано: $what"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text("Консоль", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${entries.size} записей" +
                                if (filter == null) "" else " · показано ${shown.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    TextButton(
                        onClick = { copy(log.dump(), "весь журнал") },
                        enabled = entries.isNotEmpty()
                    ) {
                        Text("копировать всё", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { log.clear(); expanded = -1; notice = "Журнал очищен" }) {
                        Text("очистить", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LevelFilterRow(selected = filter, onSelect = { filter = it })

            notice?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (shown.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (entries.isEmpty()) {
                            "Журнал пуст. Откройте доску — записи появятся здесь."
                        } else {
                            "Записей этого уровня нет"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(shown.size) { index ->
                        val entry = shown[index]
                        val isExpanded = expanded == index
                        LogEntryCard(
                            entry = entry,
                            expanded = isExpanded,
                            onClick = {
                                expanded = if (isExpanded) -1 else index
                                copy(entry.toText(), "запись")
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Чипы уровня: «всё», «предупреждения», «ошибки». */
@Composable
private fun LevelFilterRow(selected: LogLevel?, onSelect: (LogLevel?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("всё") }
        )
        FilterChip(
            selected = selected == LogLevel.WARN,
            onClick = { onSelect(LogLevel.WARN) },
            label = { Text("предупр.") }
        )
        FilterChip(
            selected = selected == LogLevel.ERROR,
            onClick = { onSelect(LogLevel.ERROR) },
            label = { Text("ошибки") }
        )
    }
}

/**
 * Одна запись: время, уровень, источник и текст. Клик разворачивает подробности
 * и заодно копирует запись — так проще всего унести ошибку в багрепорт.
 */
@Composable
private fun LogEntryCard(
    entry: LogEntry,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val accent = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.time,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = entry.message, style = MaterialTheme.typography.bodySmall)

            val detail = entry.detail
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (expanded) detail else "подробности ▾",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = if (expanded) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}