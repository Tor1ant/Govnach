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
import com.github.tor1ant.govnach.ui.ThreadScreen
import com.github.tor1ant.govnach.ui.model.AdultAccess
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
 * Разрешение на взрослые доски хранит [HomeViewModel] (singleton): его хватает и главной,
 * и экрану доски — открытие доски с тредами и постингом от него же зависит.
 */
@Composable
fun App(
    navigator: AppNavigator = rememberAppNavigator(),
    themeState: ThemeState = rememberThemeState()
) {
    val homeViewModel: HomeViewModel = koinInject()
    val homeState by homeViewModel.uiState.collectAsState()
    val adultAllowed = homeState.adultAccess == AdultAccess.ALLOWED

    GovnachTheme(themeState) {
        // Тёмный фон — сразу под корнем: пока экран грузится, окно не мигает белым.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val route = navigator.currentRoute) {
                AppRoute.Home -> HomeScreen(
                    onBoardClick = { board -> navigator.openBoard(board) }
                )

                is AppRoute.Board -> BoardScreen(
                    board = route.board,
                    adultAllowed = adultAllowed,
                    onAdultConfirmed = { homeViewModel.allowAdult() },
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
                    adultAllowed = adultAllowed,
                    onAdultConfirmed = { homeViewModel.allowAdult() },
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
                    adultAllowed = adultAllowed,
                    onAdultConfirmed = { homeViewModel.allowAdult() },
                    onBack = { navigator.back() }
                )
            }
        }
    }
}
