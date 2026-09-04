package com.github.tor1ant.govnach

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.github.tor1ant.govnach.ui.HomeScreen
import okio.blackholeSink

@Composable
fun App() {
    MaterialTheme() {
        HomeScreen(
            onBoardClick = { board ->
                // TODO: Навигация к доске
                println("Clicked on board: /${board.id}/ - ${board.name}")
            }
        )
    }
}
