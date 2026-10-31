package com.github.tor1ant.govnach

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.di.appModule
import org.koin.core.context.GlobalContext.startKoin
import java.awt.image.AbstractMultiResolutionImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** Имя приложения: заголовок окна и подпись в панели задач. */
private const val APP_NAME = "Govnach"

/** Кадры иконки, которые лежат в `desktopApp/icons/window-<N>.png`, от крупного к мелкому. */
private val ICON_SIZES = listOf(64, 48, 40, 32, 24, 20, 16)

fun main() = application {

    startKoin {
        modules(appModule)
    }

    // Иконка окна, заголовка и панели задач. В установленном приложении
    // jpackage задаёт иконку ярлыку и exe (desktopApp/icons/govnach.ico), а при
    // запуске из IDE или через `gradlew run` ничего этого нет — поэтому те же
    // кадры читаются вручную.
    val windowIcon = remember { loadWindowIcon() }

    Window(
        onCloseRequest = ::exitApplication,
        title = APP_NAME,
        icon = windowIcon,
    ) {
        App()
    }
}

/**
 * Иконка окна — набор кадров под размеры, которые спрашивает ОС.
 *
 * Одна картинка здесь не годится. Заголовок и трей Windows просят иконку
 * размером `SM_CXSMICON` — 16px при масштабе 100%, 24 или 32 при 150–200%.
 * Если отдать единственный крупный PNG, размер подбирает
 * `sun.awt.SunToolkit.getScaledIconImage`: он округляет масштаб до 1/N и
 * рисует билинейно без сохранения пропорций. В итоге в заголовке вместо
 * стрелки оказывается мутное пятно — это и было видно.
 *
 * Поэтому `tools/gen_desktop_icons.py` рисует каждый размер отдельно, а здесь
 * они собираются в [MultiResolutionImage]: AWT сам возьмёт кадр под запрос и
 * масштабировать ничего не станет.
 *
 * PNG, а не `.ico`: `ImageIO` не умеет читать `.ico` без сторонних кодеков.
 * Сам `.ico` (desktopApp/icons/govnach-window.ico) нужен только Windows — его
 * подхватывает оболочка для ярлыка и переключателя окон.
 */
private fun loadWindowIcon(): Painter? {
    val dir = iconDir()
    val frames = ICON_SIZES
        .map { dir.resolve("window-$it.png") }
        .filter(File::isFile)
        .mapNotNull { file -> runCatching { ImageIO.read(file) }.getOrNull() }
        .sortedByDescending { it.width }

    if (frames.isEmpty()) return null
    // Базовый кадр: Compose держит одну картинку, а размер под окно и заголовок
    // AWT берёт из MultiResolutionImage напрямую.
    val icon = WindowIconImage(frames)
    return BitmapPainter(icon.getResolutionVariant(256.0, 256.0).toComposeImageBitmap())
}

/** Папка с иконками: рабочая директория из IDE — корень проекта. */
private fun iconDir(): File {
    System.getProperty("govnach.iconDir")?.let(::File)?.let { if (it.isDirectory) return it }
    return listOf("desktopApp/icons", "icons").map(::File).firstOrNull(File::isDirectory)
        ?: File("desktopApp/icons")
}

/**
 * Кадры иконки для AWT. Базовым считается самый крупный: его AWT рисует,
 * если ОС запросила размер, которого нет в списке.
 */
private class WindowIconImage(frames: List<BufferedImage>) :
    AbstractMultiResolutionImage() {

    private val frames = frames.sortedByDescending { it.width }
    private val base = this.frames.first()

    override fun getResolutionVariant(destWidth: Double, destHeight: Double): BufferedImage =
        frames.minBy { abs(it.width - destWidth) }

    override fun getResolutionVariants(): List<BufferedImage> = frames

    override fun getBaseImage(): BufferedImage = base
}
