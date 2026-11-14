package com.github.tor1ant.govnach

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.tor1ant.govnach.ui.BoardScreen
import com.github.tor1ant.govnach.ui.HomeScreen
import com.github.tor1ant.govnach.ui.NewThreadScreen
import com.github.tor1ant.govnach.ui.StartupScreen
import com.github.tor1ant.govnach.ui.ThreadScreen
import com.github.tor1ant.govnach.ui.model.HomeViewModel
import com.github.tor1ant.govnach.ui.navigation.AppNavigator
import com.github.tor1ant.govnach.ui.navigation.AppRoute
import com.github.tor1ant.govnach.ui.navigation.rememberAppNavigator
import com.github.tor1ant.govnach.ui.theme.GovnachTheme
import com.github.tor1ant.govnach.ui.theme.ThemeState
import com.github.tor1ant.govnach.ui.theme.rememberThemeState
import org.koin.compose.koinInject

/**
 * Корень приложения: тема (светлая/тёмная) и текущий экран. Переключатель темы живёт
 * в шапке каждого экрана и берёт состояние из [GovnachTheme] через LocalThemeState.
 *
 * Поиск не отдельный экран: лупа в шапке раскрывает строку ввода прямо на месте.
 * На главной она ищет доски, на экране доски — треды этой доски, поэтому поиск
 * не меняет место в навигации.
 *
 * Пока главная загружает список досок впервые, открывается заставка с логотипом:
 * пустой экран со спиннером при запуске заменяем на тот же знак, что и в иконке.
 * Дальше заставка не показывается — повторные загрузки (кнопка «повторить») идут
 * внутри главной.
 */
@Composable
fun App(
    navigator: AppNavigator = rememberAppNavigator(),
    themeState: ThemeState = rememberThemeState()
) {
    val homeViewModel: HomeViewModel = koinInject()
    val homeState by homeViewModel.uiState.collectAsState()

    GovnachTheme(themeState) {
        // Тёмный фон — сразу под корнем: пока экран грузится, окно не мигает белым.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Логотип на запуске, пока первый ответ списка досок ещё в пути. Ответ с ошибкой
            // заставку уже снимает: дальше главная сама покажет состояние «повторить».
            val startingUp = homeState.isLoading && homeState.boards.isEmpty() && homeState.error == null

            if (startingUp) {
                StartupScreen()
            } else {
                when (val route = navigator.currentRoute) {
                    AppRoute.Home -> HomeScreen(
                        onBoardClick = { board -> navigator.openBoard(board) }
                    )

                    is AppRoute.Board -> BoardScreen(
                        board = route.board,
                        onThreadClick = { thread ->
                            navigator.openThread(
                                board = route.board,
                                threadNum = thread.num,
                                threadSubject = thread.subject.orEmpty()
                            )
                        },
                        onNewThreadClick = { navigator.openNewThread(route.board) },
                        onBack = { navigator.back() }
                    )

                    is AppRoute.NewThread -> NewThreadScreen(
                        board = route.board,
                        onCreated = { threadNum, subject ->
                            navigator.replaceWithThread(
                                board = route.board,
                                threadNum = threadNum,
                                threadSubject = subject.orEmpty()
                            )
                        },
                        onBack = { navigator.back() }
                    )

                    is AppRoute.Thread -> ThreadScreen(
                        board = route.board,
                        threadNum = route.threadNum,
                        threadSubject = route.threadSubject,
                        onBack = { navigator.back() }
                    )
                }
            }
        }
    }
}
