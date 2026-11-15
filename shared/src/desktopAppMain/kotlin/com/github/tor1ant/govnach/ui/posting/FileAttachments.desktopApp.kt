package com.github.tor1ant.govnach.ui.posting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.SwingUtilities

/**
 * Desktop: файл выбирают в диалоге ОС. `AWT FileDialog` рисует родное окно Windows
 * (в отличие от Swing `JFileChooser`), поэтому выглядит как проводник и умеет
 * закреплённые папки, «недавние» и поиск — то, что человек и ждёт от выбора файла.
 *
 * Диалог модальный: `isVisible` не возвращает управление, пока его не закроют,
 * поэтому вызов уходит в очередь EDT через [SwingUtilities.invokeLater] — сам кадр
 * композера успевает отрисоваться, и диалог не появляется поверх старой картинки.
 */
@Composable
actual fun rememberFilePicker(enabled: Boolean, onPicked: (List<String>) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val picker = remember(enabled) {
        {
            if (enabled) {
                // Открываем последним в очереди EDT, чтобы диалог появился после текущего кадра.
                SwingUtilities.invokeLater {
                    val files = openFileDialog()
                    if (files.isNotEmpty()) currentOnPicked(files)
                }
            }
        }
    }
    return picker
}

/** Диалог выбора файлов. `null` в родителе — системное окно без привязки к окну приложения. */
private fun openFileDialog(): List<String> =
    try {
        val dialog = FileDialog(null as Frame?, "Файл для поста", FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.isVisible = true
        dialog.files.orEmpty().map(File::getAbsolutePath)
    } catch (e: Exception) {
        // На headless-машине диалога нет, но приложение всё равно должно работать:
        // тогда файл остаётся перетащить в композер.
        emptyList()
    }

/**
 * Desktop: файлы из проводника приходят как AWT `DataFlavor.javaFileListFlavor`.
 * Это единственный формат, в котором система отдаёт настоящие файлы; картинка,
 * перетащенная из браузера, приходит как `imageFlavor` и файлом поста не является.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberFileDropTarget(
    enabled: Boolean,
    onDragActiveChange: (Boolean) -> Unit,
    onFilesDropped: (List<String>) -> Unit
): Modifier {
    val currentOnActive by rememberUpdatedState(onDragActiveChange)
    val currentOnDropped by rememberUpdatedState(onFilesDropped)
    val currentEnabled by rememberUpdatedState(enabled)

    val target = remember {
        object : DragAndDropTarget {
            // Интерес к перетаскиванию решает сам модификатор (shouldStartDragAndDrop),
            // здесь только состояние подсветки.
            override fun onEntered(event: DragAndDropEvent) {
                if (currentEnabled) currentOnActive(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnActive(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnActive(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnActive(false)
                if (!currentEnabled) return false
                val paths = event.localFiles()
                if (paths.isEmpty()) return false
                currentOnDropped(paths)
                return true
            }
        }
    }

    return Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            currentEnabled && event.hasLocalFiles()
        },
        target = target
    )
}

/** Есть ли в перетаскивании файлы: картинку из браузера бросить нельзя, это не файл поста. */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.hasLocalFiles(): Boolean =
    runCatching { awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }
        .getOrDefault(false)

/**
 * Пути перетащенных файлов. `javaFileListFlavor` отдаёт готовые [File], а не строки,
 * поэтому переводим их в абсолютные пути — в таком виде их ждёт `PostingDraft.files`.
 * Каталоги отбрасываем: двач их не принимает, а бросить папку в композер человек может.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.localFiles(): List<String> = runCatching {
    val transferable = awtTransferable
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()

    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
        .orEmpty()
        .filterIsInstance<File>()
        .filter(File::isFile)
        .map(File::getAbsolutePath)
}.getOrDefault(emptyList())