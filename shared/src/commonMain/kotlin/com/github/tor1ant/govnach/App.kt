package com.github.tor1ant.govnach

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.github.tor1ant.govnach.ui.BoardScreen
import com.github.tor1ant.govnach.ui.HomeScreen
import com.github.tor1ant.govnach.ui.ThreadScreen
import com.github.tor1ant.govnach.ui.navigation.AppNavigator
import com.github.tor1ant.govnach.ui.navigation.AppRoute
import com.github.tor1ant.govnach.ui.navigation.rememberAppNavigator

@Composable
fun App(
    navigator: AppNavigator = rememberAppNavigator()
) {
    MaterialTheme() {
        val route = navigator.currentRoute

        when (route) {
            AppRoute.Home -> HomeScreen(
                onBoardClick = { board -> navigator.openBoard(board) }
            )

            is AppRoute.Board -> BoardScreen(
                board = route.board,
                onThreadClick = { thread ->
                    navigator.openThread(
                        boardId = thread.board,
                        boardName = route.board.name,
                        threadNum = thread.num,
                        threadSubject = thread.subject.orEmpty()
                    )
                },
                onBack = { navigator.back() }
            )

            is AppRoute.Thread -> ThreadScreen(
                boardId = route.boardId,
                boardName = route.boardName,
                threadNum = route.threadNum,
                threadSubject = route.threadSubject,
                onBack = { navigator.back() }
            )
        }
    }
}
