package com.github.tor1ant.govnach.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.isAdult
import com.github.tor1ant.govnach.ui.AppCard
import com.github.tor1ant.govnach.ui.AppTopBar
import com.github.tor1ant.govnach.ui.BackButton
import com.github.tor1ant.govnach.ui.EmptyState
import com.github.tor1ant.govnach.ui.ErrorState
import com.github.tor1ant.govnach.ui.util.htmlToPlainText
import org.koin.compose.koinInject
import org.openapitools.client.models.Board

/**
 * Экран поиска тредов и досок.
 *
 * Один экран на весь поиск: шапка с полем ввода и лента результатов. Колбэки открывают
 * доску ([onBoardClick]) или тред на ней ([onThreadClick]) — маршруты строит App.
 *
 * [adultAllowed] — подтвердил ли пользователь возраст: поиск подчиняется тому же гейту,
 * что и главная. Подтверждают возраст на главной или на карточке доски — этот экран
 * просто не показывает взрослое, пока разрешения нет.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onBoardClick: (Board) -> Unit,
    onThreadClick: (Board, Int, String) -> Unit,
    adultAllowed: Boolean,
    viewModel: SearchViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()

    // На Android системный «назад» возвращает из поиска туда, откуда его открыли.
    // BackHandler тот же, что и на экране треда: замена на NavigationEventHandler
    // потребовала бы отдельной зависимости, а поведение здесь то же самое.
    @Suppress("DEPRECATION")
    BackHandler(enabled = true, onBack = onBack)

    // Данные грузим сразу при открытии: к первому символу кэш уже греется,
    // и фильтр по нажатию клавиши работает без сети.
    LaunchedEffect(adultAllowed) {
        if (adultAllowed) viewModel.allowAdult()
        viewModel.preload()
    }

    val terms = searchTerms(uiState.query)

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = { Text("Поиск", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { BackButton(onClick = onBack) }
        )

        AppSearchBar(
            query = uiState.query,
            onQueryChange = viewModel::updateQuery,
            isSearching = uiState.isPending || uiState.isLoading,
            onSubmit = viewModel::submit,
            onClear = viewModel::clear,
            autoFocus = true
        )

        // Пока данные едут, тонкая полоса честно говорит, что выдача неполная.
        // Ожидание паузы после ввода показывает спиннер в самом поле ([isSearching]),
        // а список остаётся от прошлой строки и не мигает.
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.error != null && uiState.boards.isEmpty() && uiState.threads.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: "Неизвестная ошибка",
                        onRetry = { viewModel.refresh() }
                    )
                }

                !uiState.hasQuery -> {
                    SearchHint()
                }

                uiState.isEmpty && !uiState.isPending && !uiState.isLoading -> {
                    EmptyState(text = "Ничего не найдено по запросу «${uiState.query}»")
                }

                else -> {
                    SearchResults(
                        state = uiState,
                        terms = terms,
                        onBoardClick = onBoardClick,
                        onThreadClick = onThreadClick
                    )
                }
            }
        }
    }
}

/** Подсказка на пустом экране: что и как ищется. */
@Composable
private fun SearchHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Треды и доски",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Начните печатать — поиск найдёт доски по названию, " +
                "а треды по теме и тексту ОП-поста.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    terms: List<String>,
    onBoardClick: (Board) -> Unit,
    onThreadClick: (Board, Int, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.boards.isNotEmpty()) {
            item(key = "boards-header") {
                SectionHeader(title = "Доски", count = state.boards.size)
            }

            items(state.boards, key = { "board-${it.board.id}" }) { hit ->
                BoardHitCard(hit = hit, terms = terms, onClick = { onBoardClick(hit.board) })
            }
        }

        if (state.sectionOnly && state.threads.isEmpty()) {
            item(key = "boards-hint") {
                Text(
                    text = "Это доска целиком: откройте её карточку выше, чтобы увидеть все треды.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.threads.isNotEmpty()) {
            item(key = "threads-header") {
                SectionHeader(
                    title = "Треды",
                    count = state.threads.size,
                    caption = if (state.scannedThreads > 0) {
                        "просмотрено тредов: ${state.scannedThreads}"
                    } else {
                        null
                    }
                )
            }

            items(state.threads, key = { "thread-${it.board.id}-${it.thread.num}" }) { hit ->
                ThreadHitCard(
                    hit = hit,
                    terms = terms,
                    onClick = {
                        onThreadClick(
                            hit.board,
                            hit.thread.num,
                            hit.thread.subject.orEmpty()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, caption: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$title · $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/** Доска в выдаче: id, название и категория с подсветкой совпадения. */
@Composable
private fun BoardHitCard(
    hit: BoardHit,
    terms: List<String>,
    onClick: () -> Unit
) {
    val board = hit.board

    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 10.dp)
            ) {
                HighlightedText(
                    text = "/${board.id}/",
                    terms = terms,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = board.name,
                    terms = terms,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (board.isAdult()) "🔞 ${board.category}" else board.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (board.isAdult()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Тред в выдаче. Заголовок — тема треда (или первая строка поста, если темы нет),
 * ниже фрагмент поста вокруг совпадения — так видно, почему тред попал в выдачу.
 */
@Composable
private fun ThreadHitCard(
    hit: ThreadHit,
    terms: List<String>,
    onClick: () -> Unit
) {
    val thread = hit.thread
    val comment = thread.comment.orEmpty().htmlToPlainText()
    val hasSubject = thread.subject?.isNotBlank() == true
    val title = thread.subject?.takeIf { it.isNotBlank() } ?: comment.lineSequence().firstOrNull().orEmpty()
    val snippet = snippetAround(comment, terms)

    // Если темы нет, заголовок уже показывает начало поста — второй раз тот же текст не нужен,
    // но фрагмент вокруг совпадения из глубины поста всё равно полезен.
    val showSnippet = snippet.isNotEmpty() &&
        (hasSubject || normalizeForSearch(title).let { it.isEmpty() || !it.contains(snippet.trim('…')) })

    AppCard(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            HighlightedText(
                text = title.ifBlank { "Тред ${thread.num}" },
                terms = terms,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (showSnippet) {
                Spacer(modifier = Modifier.height(6.dp))
                HighlightedText(
                    text = snippet,
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
                    text = "/${hit.board.id}/ · № ${thread.num}",
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
 * Фрагмент текста вокруг первого совпадения: показывать весь ОП-пост в выдаче незачем.
 * Окно в [SNIPPET_RADIUS] символов с многоточием на обрезанном краю.
 */
internal fun snippetAround(text: String, terms: List<String>, radius: Int = SNIPPET_RADIUS): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.isEmpty()) return ""

    val haystack = normalizeForSearch(flat)
    val first = terms
        .map { haystack.indexOf(it) }
        .filter { it >= 0 }
        .minOrNull()
        ?: return flat.take(radius * 2).let { if (flat.length <= it.length) it else "$it…" }

    val start = (first - radius).coerceAtLeast(0)
    val end = (first + radius).coerceAtMost(flat.length)

    val head = if (start > 0) "…" else ""
    val tail = if (end < flat.length) "…" else ""
    return head + flat.substring(start, end).trim() + tail
}

private const val SNIPPET_RADIUS = 90
