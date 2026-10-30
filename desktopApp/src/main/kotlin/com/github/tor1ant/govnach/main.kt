package com.github.tor1ant.govnach

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.di.appModule
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.koinApplication
import org.koin.plugin.module.dsl.startKoin

fun main() = application {

    startKoin {
        modules(appModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "265ch",
    ) {
        App()
    }
}