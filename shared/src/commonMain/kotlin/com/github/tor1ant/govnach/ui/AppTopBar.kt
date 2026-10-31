package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.tor1ant.govnach.ui.theme.ThemeToggleButton

/**
 * Шапка экрана. Одна на всё приложение, чтобы цвета и кнопки не расходились:
 * ровная поверхность [MaterialTheme.colorScheme.surface], тонкая линия снизу и
 * маленький переключатель темы справа от действий экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            actions()
            ThemeToggleButton()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        scrollBehavior = scrollBehavior
    )
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
