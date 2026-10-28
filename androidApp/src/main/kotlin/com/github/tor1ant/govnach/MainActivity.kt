package com.github.tor1ant.govnach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.github.tor1ant.govnach.ui.navigation.AppNavigator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
