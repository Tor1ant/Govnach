package com.github.tor1ant.govnach.ui.posting

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Выбор файлов для поста через системный проводник.
 *
 * Возвращает действие, которое открывает выбор файла: на desktop — диалог файлов ОС,
 * на Android — системный пикер документов (SAF). [onPicked] получает **локальные пути**,
 * а не `content://`-адреса: `PostingDraft.files` и [com.github.tor1ant.govnach.client.localFile]
 * работают именно с путями, по которым можно открыть `java.io.File`. Android поэтому сначала
 * копирует выбранное во временную папку приложения.
 *
 * [enabled] == false — действие ничего не делает: на досках с запрещённым постингом
 * и во время отправки дёргать проводник незачем.
 */
@Composable
expect fun rememberFilePicker(enabled: Boolean, onPicked: (List<String>) -> Unit): () -> Unit

/**
 * Приём файлов, перетащенных из проводника ОС (drag and drop).
 *
 * Возвращает модификатор для той области композера, куда можно бросить файл.
 * [onDragActiveChange] сообщает, что указатель сейчас над областью, — по нему область
 * подсвечивается; [onFilesDropped] отдаёт локальные пути, такие же как у [rememberFilePicker].
 *
 * Бросить файл можно только когда [enabled]: иначе модификатор не заявляет интерес
 * к перетаскиванию и курсор показывает «нельзя».
 */
@Composable
expect fun rememberFileDropTarget(
    enabled: Boolean,
    onDragActiveChange: (Boolean) -> Unit,
    onFilesDropped: (List<String>) -> Unit
): Modifier

/**
 * Короткое имя файла для подписи в композере.
 *
 * Полный путь занимает всю строку и вытесняет остальное, а человеку нужно узнать файл,
 * а не его расположение. Разделители обоих видов: Android отдаёт `/`, Windows — `\`.
 */
fun fileNameOf(path: String): String {
    val name = path.substringAfterLast('/').substringAfterLast('\\')
    return name.ifBlank { path }
}
