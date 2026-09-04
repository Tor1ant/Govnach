package com.github.tor1ant.govnach

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "265ch",
    ) {
        App()
    }
}