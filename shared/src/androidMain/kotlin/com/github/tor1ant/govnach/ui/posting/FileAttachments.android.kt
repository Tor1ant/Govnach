package com.github.tor1ant.govnach.ui.posting

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android: файл выбирают в системном пикере документов (SAF), он же «Проводник»/«Файлы».
 *
 * Пикер отдаёт `content://`-адреса, а отправка поста читает байты через `java.io.File`,
 * поэтому выбранное сначала копируется во временную папку приложения, а наружу уходят
 * уже обычные пути. Копирование идёт в IO-диспетчере: файл может быть на порядок больше
 * памяти экрана, и композер не должен на это время замирать.
 */
@Composable
actual fun rememberFilePicker(enabled: Boolean, onPicked: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val paths = copyUrisToCache(context, uris)
            if (paths.isNotEmpty()) currentOnPicked(paths)
        }
    }

    return remember(launcher, enabled) {
        {
            if (enabled) {
                // "*/*" — доска сама скажет, какой тип ей не нравится; фильтр по типам
                // доски отрезал бы файлы, которые двач всё-таки принимает.
                launcher.launch("*/*")
            }
        }
    }
}

/**
 * Android: файлы приходят в `DragEvent.clipData` — это URI, полученные из проводника
 * или другого приложения. Их, как и выбранные в пикере, сначала копируем во временную папку.
 *
 * Чтение `clipData` допустимо только внутри обработки `ACTION_DROP`, что и делает [onDrop].
 */
@Composable
actual fun rememberFileDropTarget(
    enabled: Boolean,
    onDragActiveChange: (Boolean) -> Unit,
    onFilesDropped: (List<String>) -> Unit
): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnActive by rememberUpdatedState(onDragActiveChange)
    val currentOnDropped by rememberUpdatedState(onFilesDropped)
    val currentEnabled by rememberUpdatedState(enabled)

    val target = remember {
        object : DragAndDropTarget {
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

                val uris = event.droppedUris()
                if (uris.isEmpty()) return false

                scope.launch {
                    val paths = copyUrisToCache(context, uris)
                    if (paths.isNotEmpty()) currentOnDropped(paths)
                }
                return true
            }
        }
    }

    return Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            currentEnabled && event.hasDroppableUris()
        },
        target = target
    )
}

/** В перетаскивании есть URI: файл из проводника или из другого приложения. */
private fun DragAndDropEvent.hasDroppableUris(): Boolean = droppedUris().isNotEmpty()

/** URI из `clipData`. Пустой список, если перетащили текст или картинку без файла. */
private fun DragAndDropEvent.droppedUris(): List<Uri> {
    val clip = runCatching { toAndroidDragEvent().clipData }.getOrNull() ?: return emptyList()
    return buildList {
        clip.itemCount.let { count ->
            for (index in 0 until count) {
                clip.getItemAt(index)?.uri?.let(::add)
            }
        }
    }
}

/**
 * Копирует содержимое URI во временную папку и отдаёт пути к копиям.
 *
 * Имена сохраняем исходные — по ним композер подписывает файл, а доска проверяет расширение.
 * Совпадение имён не страшно: к имени добавляется номер, чтобы не затереть предыдущий файл
 * того же черновика. Файлы старше суток удаляются: временная папка не должна расти вечно.
 */
private suspend fun copyUrisToCache(context: Context, uris: List<Uri>): List<String> =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, ATTACHMENTS_DIR).apply { mkdirs() }
        pruneOld(dir)

        uris.mapNotNull { uri ->
            runCatching {
                val name = displayName(context, uri)
                val target = uniqueFile(dir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: return@runCatching null
                target.absolutePath
            }.getOrNull()
        }
    }

/** Имя файла из метаданных провайдера: у `content://` своего имени нет. */
private fun displayName(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    val raw = fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
    // Провайдеры иногда отдают имя без расширения, а иногда с разделителями пути внутри.
    return fileNameOf(raw).ifBlank { ATTACHMENT_FALLBACK_NAME }
}

/** Свободное имя в папке: `cat.jpg`, `cat (1).jpg`, … */
private fun uniqueFile(dir: File, name: String): File {
    val base = name.substringBeforeLast('.', name)
    val extension = name.substringAfterLast('.', "")
    val suffix = if (extension.isEmpty()) "" else ".$extension"

    var candidate = File(dir, name)
    var counter = 1
    while (candidate.exists()) {
        candidate = File(dir, "$base ($counter)$suffix")
        counter++
    }
    return candidate
}

/** Удаляет копии, оставшиеся от прошлых черновиков: сутки — с запасом на любой диалог. */
private fun pruneOld(dir: File) {
    val deadline = System.currentTimeMillis() - MAX_ATTACHMENT_AGE_MS
    dir.listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < deadline) file.delete()
    }
}

private const val ATTACHMENTS_DIR = "post-attachments"
private const val ATTACHMENT_FALLBACK_NAME = "file"
private const val MAX_ATTACHMENT_AGE_MS = 24L * 60 * 60 * 1000