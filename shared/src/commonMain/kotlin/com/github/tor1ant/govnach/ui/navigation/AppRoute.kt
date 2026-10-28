package com.github.tor1ant.govnach.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import org.openapitools.client.models.Board as ApiBoard

/**
 * Экраны приложения: доска -> тред.
 */
sealed interface AppRoute {
    data object Home : AppRoute

    data class Board(val board: ApiBoard) : AppRoute

    data class Thread(
        val boardId: String,
        val boardName: String,
        val threadNum: Int,
        val threadSubject: String = ""
    ) : AppRoute
}

/**
 * Простой back stack без навигационной библиотеки: достаточно трёх экранов,
 * а зависимость от navigation-compose для KMP пока не добавляем.
 */
class AppNavigator(startRoute: AppRoute = AppRoute.Home) {

    private val stack = mutableListOf<AppRoute>(startRoute).toMutableStateList()

    val currentRoute: AppRoute
        get() = stack.last()

    val canGoBack: Boolean
        get() = stack.size > 1

    fun openBoard(board: ApiBoard) {
        push(AppRoute.Board(board))
    }

    fun openThread(boardId: String, boardName: String, threadNum: Int, threadSubject: String = "") {
        push(AppRoute.Thread(boardId, boardName, threadNum, threadSubject))
    }

    fun back() {
        if (canGoBack) {
            stack.removeAt(stack.lastIndex)
        }
    }

    private fun push(route: AppRoute) {
        if (stack.lastOrNull() == route) return
        stack.add(route)
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator = remember { AppNavigator() }
