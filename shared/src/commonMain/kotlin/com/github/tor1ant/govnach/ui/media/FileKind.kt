package com.github.tor1ant.govnach.ui.media

import org.openapitools.client.models.FileModel

/**
 * Ссылки на медиа двача: path/thumbnail из апи — относительные пути, к ним добавляется host.
 * Base совпадает с ApiClient.BASE_URL из генерируемого клиента.
 */
object MediaPaths {
    const val BASE_URL = "https://2ch.hk"

    fun src(file: FileModel): String = BASE_URL + file.path
    fun thumbnail(file: FileModel): String = (file.thumbnail.ifEmpty { null } ?: file.path).let { BASE_URL + it }
}

enum class FileKind {
    Image,
    Video,
    Audio,
    Sticker,
    Other
}

fun FileModel.kind(): FileKind = when (type) {
    FILE_TYPE_JPG, FILE_TYPE_PNG, FILE_TYPE_APNG, FILE_TYPE_GIF, FILE_TYPE_BMP, FILE_TYPE_WEBP -> FileKind.Image
    FILE_TYPE_WEBM, FILE_TYPE_MP4 -> FileKind.Video
    FILE_TYPE_MP3, FILE_TYPE_OGG -> FileKind.Audio
    FILE_TYPE_STICKER -> FileKind.Sticker
    else -> kindFromExtension()
}

fun FileModel.isImage(): Boolean = kind() == FileKind.Image

fun FileModel.isVideo(): Boolean = kind() == FileKind.Video

/** Двач добавляет новые type без обновления схемы — на случай этого определяем по расширению. */
private fun FileModel.kindFromExtension(): FileKind =
    when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "apng", "gif", "bmp", "webp" -> FileKind.Image
        "webm", "mp4" -> FileKind.Video
        "mp3", "ogg" -> FileKind.Audio
        else -> FileKind.Other
    }

private const val FILE_TYPE_JPG = 1
private const val FILE_TYPE_PNG = 2
private const val FILE_TYPE_APNG = 3
private const val FILE_TYPE_GIF = 4
private const val FILE_TYPE_BMP = 5
private const val FILE_TYPE_WEBM = 6
private const val FILE_TYPE_MP3 = 7
private const val FILE_TYPE_OGG = 8
private const val FILE_TYPE_WEBP = 9
private const val FILE_TYPE_MP4 = 10
private const val FILE_TYPE_STICKER = 100
