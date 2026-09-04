package com.github.tor1ant.govnach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.tor1ant.govnach.client.Client
import org.jetbrains.compose.resources.painterResource

import govnach.shared.generated.resources.Res
import govnach.shared.generated.resources.compose_multiplatform
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.openapitools.client.models.Board

@Composable
@Preview
fun App() {
    MaterialTheme {
        var boards by remember { mutableStateOf<List<Board>?>(null) }
        val client: Client = koinInject()
        var showContent by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = {

                showContent = !showContent
            }) {
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        boards = client.getBoards()

                    }
                }
                Text("Click me!")
                Text(boards.toString())
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}

fun getBoards() {

}