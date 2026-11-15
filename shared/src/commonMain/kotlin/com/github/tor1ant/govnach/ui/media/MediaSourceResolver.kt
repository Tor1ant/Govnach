package com.github.tor1ant.govnach.ui.media

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Откуда брать видео для встроенного плеера.
 *
 * Плеер открывает адрес своим нативным сетевым слоем и заголовков не принимает — ключ
 * доступа (`usercode_auth`) он с собой не принесёт. Поэтому файлы закрытых разделов
 * (`/h/`, `/ho/`, `/e/`, `/nf/`, `/fet/`) он получает как 500 «Not found».
 *
 * Здесь решается, как поступить с конкретным файлом:
 * - файл отдаётся и без ключа — играем прямо по адресу, ничего не качая (обычный случай);
 * - файл требует ключ — скачиваем его клиентом приложения ([http] знает ключ) и отдаём
 *   плееру локальный путь.
 *
 * Проверка «отдаётся ли без ключа» делается один раз на доску и запоминается: у двача
 * доступ закрыт целой доской, а не отдельными файлами, поэтому лишних запросов нет.
 */
class MediaSourceResolver(
    private val http: HttpClient,
    /** Клиент без подтверждений: им и проверяем, нужен ли ключ. */
    private val anonymous: HttpClient
) {
    private val mutex = Mutex()

    /** Готовые загрузки: повторный просмотр того же видео играет с диска. */
    private val downloaded = HashMap<String, File>()

    /** Доски, про которые уже известно, нужен ли им ключ: true — файлы без ключа не отдаются. */
    private val boardNeedsKey = HashMap<String, Boolean>()

    private val directory: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "govnach-media").apply { mkdirs() }
    }

    /**
     * Адрес для плеера: исходный, если он доступен без ключа, иначе путь к скачанной копии.
     * Если скачать не удалось, возвращается исходный адрес — плеер покажет свою ошибку.
     */
    suspend fun playable(url: String): String {
        if (url.isEmpty()) return url
        if (!requiresKey(url)) return url

        return mutex.withLock {
            downloaded[url]?.takeIf { it.isFile }?.absolutePath
                ?: download(url)?.also { downloaded[url] = it }?.absolutePath
                ?: url
        }
    }

    /** Закрыта ли доска файла: проверяется один запрос на доску, дальше берётся из памяти. */
    private suspend fun requiresKey(url: String): Boolean {
        val board = boardOf(url) ?: return false

        mutex.withLock { boardNeedsKey[board] }?.let { return it }

        val locked = try {
            // Нужен только статус: тело не читаем и сразу закрываем, иначе проверка
            // сама скачала бы весь ролик — ровно то, чего мы избегаем для обычных досок.
            anonymous.prepareGet(url).execute { response -> response.status.value == 500 }
        } catch (e: Exception) {
            // Сеть подвела — считаем, что ключ не нужен: плеер попробует сам,
            // а если не выйдет, покажет свою ошибку вместо пустого экрана.
            return false
        }

        mutex.withLock { boardNeedsKey[board] = locked }
        return locked
    }

    private suspend fun download(url: String): File? = try {
        val response = http.get(url)
        if (response.status.value !in 200..299) {
            null
        } else {
            val bytes = response.bodyAsChannel().readRemaining().readBytes()
            if (bytes.isEmpty()) {
                null
            } else {
                val file = File(directory, fileName(url))
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                file
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Доска из адреса файла: `/h/src/372438/name.jpg` → `h`. Это первый сегмент пути
     * у двача, поэтому других правил не нужно.
     */
    private fun boardOf(url: String): String? =
        url.substringAfter("://", url)
            .substringAfter('/', "")
            .substringBefore('/')
            .takeIf { it.isNotEmpty() && it != "api" && it != "user" && it != "static" }

    /**
     * Имя файла: расширение важно плееру — по нему он выбирает демультиплексор.
     * К нему добавляется хеш адреса, иначе два разных файла с одинаковым именем
     * в конце пути перетирали бы друг друга.
     */
    private fun fileName(url: String): String {
        val base = url.substringBefore('?').substringAfterLast('/').ifBlank { "media" }
        return "${url.hashCode().toUInt().toString(16)}-$base"
    }
}