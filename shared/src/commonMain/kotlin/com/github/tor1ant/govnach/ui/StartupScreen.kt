package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Ширина знака на заставке: на широком окне логотип не растягивается во всю ширину. */
private val StartupLogoWidth = 220.dp

/**
 * Заставка запуска: пока список досок не приехал, экран не пустует — по центру
 * логотип приложения с индикатором под ним. Фон берётся из [MaterialTheme], поэтому
 * переход к главной не мигает и не меняет палитру.
 *
 * Логотип векторный и без подложки, поэтому одинаково читается на светлой и тёмной теме.
 */
@Composable
fun StartupScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(modifier = Modifier.width(StartupLogoWidth))
            Spacer(modifier = Modifier.size(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }
    }
}
