package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Объяснение и поле для ключа доступа к закрытым разделам.
 *
 * Двач открывает `/h/`, `/ho/`, `/e/`, `/nf/`, `/fet/` и ещё несколько досок только тем,
 * кто уже что-то постил: после первого поста сервер выдаёт cookie `usercode_auth`,
 * и без неё каталог отвечает HTTP 500. Браузер хранит её сам, у приложения своего
 * хранилища не было — поэтому ключ можно вставить здесь, и он запомнится.
 */
@Composable
internal fun AccessTokenHelp(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var token by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Раздел открыт только тем, кто уже писал на дваче.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!expanded) {
            TextButton(onClick = { expanded = true }) {
                Text("У меня есть ключ доступа")
            }
            return@Column
        }

        Text(
            text = "В браузере: F12 → Application → Cookies → 2ch.org → значение " +
                "usercode_auth. Оно появляется после первого поста.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("usercode_auth") },
            singleLine = true,
            // Ключ — это учётные данные: на экране не показываем.
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onSubmit(token.trim()) },
            enabled = token.isNotBlank()
        ) {
            Text("Открыть раздел")
        }

        Spacer(modifier = Modifier.height(0.dp))
    }
}