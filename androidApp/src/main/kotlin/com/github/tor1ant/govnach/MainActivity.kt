package com.github.tor1ant.govnach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.github.tor1ant.govnach.di.appModule
import com.github.tor1ant.govnach.ui.navigation.AppNavigator
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.error.KoinApplicationAlreadyStartedException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            startKoin {
                androidContext(this@MainActivity.applicationContext)
                modules(appModule)
            }
        } catch (e: KoinApplicationAlreadyStartedException) {
            // Process-wide Koin уже собран — переинициализация не нужна.
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Back stack живёт здесь, чтобы системный «назад» возвращал с доски/треда, а не закрывал приложение.
        val navigator = AppNavigator()

        setContent {
            BackHandler(enabled = navigator.canGoBack) { navigator.back() }
            App(navigator = navigator)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
