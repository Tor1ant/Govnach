package com.github.tor1ant.govnach.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import govnach.shared.generated.resources.Res
import govnach.shared.generated.resources.twoch_emblem
import govnach.shared.generated.resources.twoch_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Логотип двача — вектор из `composeResources/drawable/twoch_logo.xml`: оранжевая
 * стрелка и словесный знак «2CH». Фона у картинки нет, поэтому он одинаково
 * читается и на светлой, и на тёмной палитре приложения.
 *
 * Файл ведёт `tools/gen_logo.py` из официального SVG со страницы 2ch.hk
 * (`tools/2ch-logo-transparent.svg`), так что иконка лаунчера, шапка и экран
 * запуска рисуются одним и тем же источником.
 *
 * Размеры по умолчанию — под шапку: высота 20dp, пропорции знака сохраняются.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.twoch_logo),
        contentDescription = "Двач",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

/** Высота знака в шапке: строка заголовка у TopAppBar — 64dp, знак встаёт в неё по центру. */
val AppLogoHeight = 20.dp

/** Отношение сторон знака «2CH»: viewBox 509.9 x 188.5 в `twoch_logo.xml`. */
const val AppLogoAspectRatio = 509.9f / 188.5f

/**
 * Только стрелка без букв — для тесных мест (иконка лаунчера, ожидание загрузки).
 */
@Composable
fun AppEmblem(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(Res.drawable.twoch_emblem),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
