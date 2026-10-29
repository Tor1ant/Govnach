package com.github.tor1ant.govnach.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.openapitools.client.models.FileModel

/**
 * Диалог воспроизведения видео: на android inline через media3, на desktop — кнопка
 * «открыть в системе» (см. actual VideoPlayer).
 */
@Composable
fun LocalPlayerDialog(
    file: FileModel,
    onDismiss: () -> Unit
) {
    val url = MediaPaths.src(file)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = file.displayname.ifEmpty { file.name },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                    TextButton(onClick = onDismiss) { Text("✕") }
                }

                VideoPlayer(url = url, modifier = Modifier.weight(1f))
            }
        }
    }
}
