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
import com.github.tor1ant.govnach.ui.model.AdultAccess
import com.github.tor1ant.govnach.ui.model.HomeViewModel
import org.koin.compose.koinInject
import org.openapitools.client.models.Board

@Composable
fun HomeScreen(
    onBoardClick: (Board) -> Unit = {}

) {
    val viewModel: HomeViewModel = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    var askAdult by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = { HomeTopBarTitle() })
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
                        // Фильтр по категориям
                        CategoryFilter(
                            categories = viewModel.getCategories(),
                            selectedCategory = uiState.selectedCategory,
                            onCategorySelected = { viewModel.selectCategory(it) }
                        )

                        // Доски 18+ спрятаны до подтверждения возраста: подсказка вместо списка
                        if (uiState.adultAccess == AdultAccess.HIDDEN) {
                            AdultBoardsNotice(
                                hiddenBoards = uiState.hiddenAdultBoards,
                                onEnable = { askAdult = true }
                            )
                        } else {
                            AdultBoardsHideRow(onHide = { viewModel.hideAdult() })
                        }

                        // Список досок
                        BoardsList(
                            boards = viewModel.getFilteredBoards(),
                            onBoardClick = onBoardClick
                        )
                    }
                }
            }
        }
    }

    if (askAdult) {
        AdultGateDialog(
            onConfirm = {
                askAdult = false
                viewModel.allowAdult()
            },
            onDismiss = { askAdult = false }
        )
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

/**
 * Пока взрослые доски разрешены — маленькая строка, чтобы их можно было спрятать обратно.
 */
@Composable
private fun AdultBoardsHideRow(onHide: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Доски 18+ показаны",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onHide) {
            Text("Скрыть")
        }
    }
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
