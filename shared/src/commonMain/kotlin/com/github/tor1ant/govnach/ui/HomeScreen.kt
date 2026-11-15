package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tor1ant.govnach.client.isAdult
import com.github.tor1ant.govnach.ui.model.HomeViewModel
import com.github.tor1ant.govnach.ui.model.filterCategories
import com.github.tor1ant.govnach.ui.model.filteredBoards
import com.github.tor1ant.govnach.ui.search.AppSearchBar
import com.github.tor1ant.govnach.ui.search.ConsoleActionButton
import com.github.tor1ant.govnach.ui.search.SearchActionButton
import com.github.tor1ant.govnach.ui.search.searchBoards
import com.github.tor1ant.govnach.ui.search.searchTerms
import org.koin.compose.koinInject
import org.openapitools.client.models.Board

/**
 * Главная: список всех досок и фильтр по категориям.
 *
 * Поиск здесь ищет **только доски** — доска открывается внутрь, и поиск тредов живёт уже
 * на экране доски ([BoardScreen]). Строку поиска открывает лупа в шапке; пока она набрана,
 * фильтр категорий уступает ей место, чтобы не искать по двум ограничениям сразу.
 */
@Composable
fun HomeScreen(
    onBoardClick: (Board) -> Unit = {},
    onConsoleClick: () -> Unit = {}
) {
    val viewModel: HomeViewModel = koinInject()
    val uiState by viewModel.uiState.collectAsState()

    // Поиск досок раскрыт: лупа в шапке превратилась в строку ввода.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val terms = searchTerms(query)
    // Обе величины выводятся из uiState — единственного источника, который экран наблюдает.
    val allBoards = uiState.filteredBoards()
    val foundBoards = if (terms.isEmpty()) {
        allBoards
    } else {
        searchBoards(allBoards, terms).map { it.board }
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
                    hint = "Поиск досок",
                    isSearching = false,
                    onSubmit = {},
                    onClear = { query = "" },
                    autoFocus = true,
                    onClose = ::closeSearch
                )
            } else {
                AppTopBar(
                    title = { HomeTopBarTitle() },
                    actions = {
                        ConsoleActionButton(onClick = onConsoleClick)
                        SearchActionButton(onClick = { searching = true })
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
                // Первую загрузку закрывает заставка в App: пустой экран при запуске
                // не должен показывать второй спиннер поверх логотипа.
                uiState.isLoading && uiState.boards.isEmpty() -> Unit

                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "Неизвестная ошибка",
                        onRetry = { viewModel.loadBoards() }
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Фильтр по категориям уступает место результатам поиска:
                        // искать «в категории» и «по строке» одновременно — не то, чего ждут.
                        if (!searching) {
                            CategoryFilter(
                                categories = uiState.filterCategories(),
                                selectedCategory = uiState.selectedCategory,
                                onCategorySelected = { viewModel.selectCategory(it) }
                            )
                        }

                        // Список досок
                        BoardsList(
                            boards = foundBoards,
                            onBoardClick = { board ->
                                closeSearch()
                                onBoardClick(board)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Заголовок главной: вместо текстового «Двач» — сам логотип. Высота фиксирована,
 * чтобы шапка не прыгала, пока знак подгружается.
 */
@Composable
private fun HomeTopBarTitle() {
    AppLogo(modifier = Modifier.height(AppLogoHeight))
}

@Composable
fun CategoryFilter(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Text(
            text = "Категории",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(
                    label = "Все",
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) }
                )
            }

            items(categories) { category ->
                CategoryChip(
                    label = category,
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

/** Чипы фильтра: выбранный — акцентом, остальные — на нейтральной подложке. */
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    )
}

@Composable
fun BoardsList(
    boards: List<Board>,
    onBoardClick: (Board) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(boards) { board ->
            BoardCard(
                board = board,
                onClick = { onBoardClick(board) }
            )
        }
    }
}

@Composable
fun BoardCard(
    board: Board,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Заголовок с ID и названием
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ID доски
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Text(
                        text = "/${board.id}/",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Название доски
                Text(
                    text = board.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (board.infoOuter.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = board.infoOuter,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Мета-информация
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Категория
                Text(
                    text = if (board.isAdult()) "🔞 ${board.category}" else board.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (board.isAdult()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Статистика
                Text(
                    text = "тредов: ${board.threadsPerPage} · бамп: ${board.bumpLimit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
