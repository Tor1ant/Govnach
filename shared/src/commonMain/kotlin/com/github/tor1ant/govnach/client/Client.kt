package com.github.tor1ant.govnach.client

import io.ktor.client.request.forms.FormPart
import io.ktor.client.request.forms.InputProvider
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import org.openapitools.client.apis.DefaultApi
import org.openapitools.client.models.ApiCaptchaEmojiClickPostRequest
import org.openapitools.client.models.Board
import org.openapitools.client.models.CaptchaType
import org.openapitools.client.models.Error
import org.openapitools.client.models.Post
import org.openapitools.client.models.ThreadPreview
import java.io.File
import java.security.MessageDigest

/**
 * Ошибка, которую вернул двач в теле ответа (поле error при HTTP 200).
 * Если двач не прислал текст, подпись берём из [errorCodeMessage] — коды описаны в openapi/api.yml.
 */
class ApiException(
    val error: Error,
    message: String = error.message ?: errorCodeMessage(error.code) ?: "Ошибка запроса (код ${error.code ?: "нет кода"})"
) : Exception(message)

/** Человекочитаемая подпись кода ошибки; таблица совпадает с описанием ErrorCode в openapi/api.yml. */
fun errorCodeMessage(code: Int?): String? = when (code) {
    0 -> "Ошибки нет"
    403 -> "Отказано в доступе"
    666 -> "Внутренняя ошибка двача"
    667 -> "Запрос не существует"

    -2 -> "Доска не существует"
    -3 -> "Тред не существует"
    -4 -> "Нет доступа к контенту"
    -5 -> "Капча не валидна"
    -6 -> "Вы забанены"
    -7 -> "Тред закрыт: отвечать нельзя"
    -8 -> "Слишком быстро постите, либо на доске лимит создания тредов"
    -9 -> "Поле слишком большое"
    -10 -> "Такой файл уже загружен"
    -11 -> "Доска не поддерживает этот тип файла"
    -12 -> "Файл слишком большой для этой доски"
    -13 -> "Файлов больше, чем разрешает доска"
    -14 -> "Трипкод был забанен"
    -15 -> "В комментарии недопустимое выражение"
    -16 -> "В комментарии выражение из спам-листа"
    -19 -> "Тред нельзя создать без файла"
    -20 -> "Пост не может быть пустым: нужен комментарий или файл"
    -21 -> "Пасскод не существует"
    -22 -> "Лимит запросов достигнут, попробуйте позже"
    -23 -> "Слишком короткое сообщение"
    -31 -> "Пост не существует"

    -41 -> "Доска закрыта: постить нельзя"
    -42 -> "Доска доступна только с пасскода"

    -50 -> "Для жалобы слишком много постов"
    -51 -> "Жалоба без текста"
    -52 -> "Такая жалоба уже отправлена ранее"

    -300 -> "Приложение не существует или отключено"
    -301 -> "Некорректный app_response_id"
    -302 -> "app_response_id истёк, живёт 180 секунд"
    -303 -> "Неверная подпись поста ключом приложения: проверьте GOVNACH_APP_PRIVATE_KEY"

    else -> null
}

/** Черновик поста или треда. [threadNum] == null — двач создаёт новый тред, и тогда файл обязателен. */
data class PostingDraft(
    val boardId: String,
    val threadNum: Int? = null,
    val comment: String = "",
    val name: String? = null,
    val subject: String? = null,
    val files: List<String> = emptyList()
) {
    val isNewThread: Boolean get() = threadNum == null
}

/** Что вернул двач: номер нового поста ([num]) либо номер нового треда ([thread]). */
data class PostingResult(
    val num: Int? = null,
    val thread: Int? = null
)

/**
 * Состояние emoji капчи. Двач отдаёт либо новую картинку с клавиатурой, либо [solved] —
 * id, который потом уходит в пост как `emoji_captcha_id`.
 */
data class EmojiCaptcha(
    val id: String,
    val imageBase64: String? = null,
    val keyboardBase64: List<String> = emptyList(),
    val solved: String? = null
) {
    val isSolved: Boolean get() = solved != null
}

enum class PostingProblem {
    POSTING_DISABLED,
    EMPTY_DRAFT,
    COMMENT_TOO_LONG,
    FILE_MISSING,
    FILE_TYPE_FORBIDDEN,
    FILE_TOO_BIG,
    THREAD_NEEDS_FILE
}

/** Файл перед отправкой: имя и размер в байтах. */
data class LocalFile(
    val name: String,
    val sizeBytes: Long
) {
    val kilobytes: Long get() = sizeBytes / BYTES_PER_KB
}

/** Настройки доски, которые разрешают или мешают отправить пост. */
data class PostingRules(
    val postingEnabled: Boolean = true,
    val allowName: Boolean = false,
    val allowSubject: Boolean = false,
    val maxComment: Int = 0,
    val maxFilesKb: Long = 0L,
    val fileTypes: Set<String> = emptySet()
)

fun postingRules(board: Board): PostingRules = PostingRules(
    postingEnabled = board.enablePosting,
    allowName = board.enableNames,
    allowSubject = board.enableSubject,
    maxComment = board.maxComment,
    maxFilesKb = board.maxFilesSize.toLong(),
    fileTypes = board.fileTypes.orEmpty().map { it.lowercase() }.toSet()
)

/** Файл для отправки; null если пути нет или это не файл. */
fun localFile(path: String): LocalFile? {
    val file = File(path)
    if (!file.isFile) return null
    return LocalFile(name = file.name, sizeBytes = file.length())
}

/**
 * Локальная проверка черновика по настройкам доски. Двач проверит и сам, но лучше отклонить
 * пост до запроса в сеть и сказать пользователю что именно не так. [fileOf] подменяют в тестах.
 */
fun validatePosting(
    rules: PostingRules,
    draft: PostingDraft,
    fileOf: (String) -> LocalFile? = ::localFile
): List<PostingProblem> {
    if (!rules.postingEnabled) return listOf(PostingProblem.POSTING_DISABLED)

    val problems = mutableListOf<PostingProblem>()

    if (draft.comment.isBlank() && draft.files.isEmpty()) problems += PostingProblem.EMPTY_DRAFT
    if (rules.maxComment > 0 && draft.comment.length > rules.maxComment) {
        problems += PostingProblem.COMMENT_TOO_LONG
    }

    for (path in draft.files) {
        val extension = path.substringAfterLast('.', "").lowercase()
        val type = if (extension == "jpeg") "jpg" else extension

        if (rules.fileTypes.isNotEmpty() && type !in rules.fileTypes) {
            problems += PostingProblem.FILE_TYPE_FORBIDDEN
            continue
        }

        val file = fileOf(path) ?: run {
            problems += PostingProblem.FILE_MISSING
            continue
        }

        if (rules.maxFilesKb > 0 && file.sizeBytes > rules.maxFilesKb * BYTES_PER_KB) {
            problems += PostingProblem.FILE_TOO_BIG
        }
    }

    // ErrorEmptyOp: ОП-пост без файла двач не принимает.
    if (draft.isNewThread && draft.files.isEmpty()) problems += PostingProblem.THREAD_NEEDS_FILE

    return problems
}

/** Текст проблем для подсказки композера; числа берём из [rules]. */
fun postingProblemsMessage(problems: List<PostingProblem>, rules: PostingRules): String =
    problems.joinToString("; ") { problem ->
        when (problem) {
            PostingProblem.POSTING_DISABLED -> "Постинг на доске запрещён"
            PostingProblem.EMPTY_DRAFT -> "Нужен комментарий или файл: пустой пост двач не принимает"
            PostingProblem.COMMENT_TOO_LONG -> "Комментарий длиннее лимита доски: ${rules.maxComment} символов"
            PostingProblem.FILE_MISSING -> "Файл по указанному пути не найден"
            PostingProblem.FILE_TYPE_FORBIDDEN ->
                "Доска не разрешает этот тип файла (можно: ${rules.fileTypes.joinToString("/")})"

            PostingProblem.FILE_TOO_BIG -> "Размер файлов больше лимита доски: ${rules.maxFilesKb} КБ на пост"
            PostingProblem.THREAD_NEEDS_FILE -> "Для создания треда нужен файл: двач не принимает пустой ОП-пост"
        }
    }

private const val BYTES_PER_KB = 1024L

class Client(
    private val client: DefaultApi
) {

    suspend fun getBoards(): List<Board> {
        return client.apiMobileV2BoardsGet().body()
    }

    /**
     * Треды доски в порядке bump: прилепленные сверху, остальные как на доске.
     */
    suspend fun getThreads(boardId: String): List<ThreadPreview> {
        return client.boardCatalogJsonGet(boardId).body().threads.orEmpty()
    }

    /**
     * Посты треда от ОП-поста до последнего.
     *
     * /api/mobile/v2/after/{board}/{thread}/{num} отдаёт посты, начиная с [num],
     * поэтому идём от номера треда и докупаем хвост, пока ответ не станет пустым.
     */
    suspend fun getThreadPosts(boardId: String, threadNum: Int): List<Post> {
        val posts = mutableListOf<Post>()
        var from = threadNum

        while (posts.size < MAX_THREAD_POSTS) {
            val response = client.apiMobileV2AfterBoardThreadNumGet(boardId, threadNum, from).body()

            response.error?.let { throw ApiException(it) }

            val batch = response.posts.orEmpty().filter { it.num >= from }
            if (batch.isEmpty()) break

            posts += batch
            from = batch.maxOf { it.num } + 1
        }

        return posts.distinctBy { it.num }
    }

    /**
     * Краткая информация о треде: номер, время создания, количество постов.
     */
    suspend fun getThreadInfo(boardId: String, threadNum: Int): ThreadInfo {
        val response = client.apiMobileV2InfoBoardThreadGet(boardId, threadNum).body()

        response.error?.let { throw ApiException(it) }

        val thread = response.thread ?: return ThreadInfo(threadNum, 0, 0)

        return ThreadInfo(
            num = thread.num ?: threadNum,
            posts = thread.posts ?: 0,
            timestamp = thread.timestamp ?: 0
        )
    }

    data class ThreadInfo(
        val num: Int,
        val posts: Int,
        val timestamp: Int
    )

    /**
     * Отправка поста или треда: `POST /user/posting`.
     *
     * Капчу решаем emoji капчей ([emojiCaptchaId]) — для неё ключи приложения не нужны:
     * id выдаёт /api/captcha/emoji/id, после решения /click отдаёт `emoji_captcha_id`.
     * Если ключи приложения заданы окружением (GOVNACH_APP_PUBLIC_KEY/GOVNACH_APP_PRIVATE_KEY)
     * и id капчи нет, идём через appid: app_response = sha256(app_response_id + '|' + private key).
     * Файлы идут полем `file[]`: имя поля задаём сами, сгенерированный клиент отдаёт FormPart как есть.
     */
    suspend fun post(draft: PostingDraft, emojiCaptchaId: String? = null): PostingResult {
        require(draft.comment.isNotBlank() || draft.files.isNotEmpty()) { "Пост не может быть пустым" }

        // Двач ждёт поле с именем `file[]`, иначе часть не считается файлом. Байты читаем целиком: лимит доски 40 МБ на пост.
        @Suppress("UNCHECKED_CAST") // ktor собирает форму и из ByteArray, а сгенерированный клиент объявляет тип как InputProvider
        val files = draft.files.map { path ->
            val file = File(path)

            FormPart(
                FILE_FIELD,
                file.readBytes(),
                Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                }
            ) as FormPart<InputProvider>
        }.ifEmpty { null }

        val publicKey = setting("GOVNACH_APP_PUBLIC_KEY")
        val privateKey = setting("GOVNACH_APP_PRIVATE_KEY")
        val useAppId = emojiCaptchaId == null && publicKey != null && privateKey != null

        if (emojiCaptchaId == null && !useAppId) {
            throw IllegalStateException("Сначала решите капчу: без ключей приложения нужен emoji_captcha_id")
        }

        val appResponseId = if (useAppId) getCaptchaAppId(publicKey!!, draft.boardId, draft.threadNum) else null

        val response = try {
            client.userPostingPost(
                captchaType = if (useAppId) CaptchaType.appid else CaptchaType.emoji_captcha,
                board = draft.boardId,
                thread = draft.threadNum,
                name = draft.name?.ifBlank { null },
                subject = draft.subject?.ifBlank { null },
                comment = draft.comment.ifBlank { null },
                appResponseId = appResponseId,
                appResponse = appResponseId?.let { sha256Hex("$it|$privateKey") },
                emojiCaptchaId = emojiCaptchaId,
                file = files
            ).body()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Апи вернул неожиданный ответ на отправку поста: ${e.message}", e)
        }

        response.error?.let { throw ApiException(it) }
        if (response.result != 1) {
            throw IllegalStateException("Пост не опубликован (код ${response.result ?: "нет данных"})")
        }

        return if (draft.isNewThread) {
            PostingResult(thread = response.thread ?: response.num, num = null)
        } else {
            PostingResult(num = response.num ?: draft.threadNum, thread = null)
        }
    }

    /** Новый id emoji капчи для доски и (опционально) треда. Живёт недолго — просим перед показом. */
    suspend fun requestEmojiCaptcha(boardId: String, threadNum: Int? = null): EmojiCaptcha {
        val response = try {
            client.apiCaptchaEmojiIdGet(boardId, threadNum).body()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Апи не выдал emoji капчу: ${e.message}", e)
        }

        response.error?.let { throw ApiException(it) }
        if (response.result != 1) throw IllegalStateException("Апи не выдал id emoji капчи (код ${response.result})")

        return showEmojiCaptcha(response.id)
    }

    /** Текущее состояние капчи: картинка с правильными emoji и клавиатура вариантов. */
    suspend fun showEmojiCaptcha(captchaId: String): EmojiCaptcha {
        val response = try {
            client.apiCaptchaEmojiShowGet(captchaId).body()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Апи не отдал состояние emoji капчи: ${e.message}", e)
        }

        val keyboard = response.keyboard.orEmpty()

        return EmojiCaptcha(
            id = captchaId,
            imageBase64 = response.image,
            keyboardBase64 = keyboard,
            solved = response.success
        )
    }

    /**
     * Клик по эмодзи [emojiNumber] (нумерация с 0). Двач либо отдаёт новую клавиатуру,
     * либо `success` — тогда капча решена и её id идёт в пост.
     */
    suspend fun clickEmojiCaptcha(captchaId: String, emojiNumber: Int): EmojiCaptcha {
        val response = try {
            client.apiCaptchaEmojiClickPost(
                ApiCaptchaEmojiClickPostRequest(
                    captchaTokenID = captchaId,
                    emojiNumber = emojiNumber
                )
            ).body()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Апи не принял клик по emoji капче: ${e.message}", e)
        }

        return EmojiCaptcha(
            id = captchaId,
            imageBase64 = response.image,
            keyboardBase64 = response.keyboard.orEmpty(),
            solved = response.success
        )
    }

    /**
     * app_response_id для отправки поста. Живёт 180 секунд — получаем прямо перед постом.
     * Для нового треда [threadNum] не передаём: номера ещё нет.
     */
    suspend fun getCaptchaAppId(publicKey: String, boardId: String, threadNum: Int? = null): String {
        val response = try {
            client.apiCaptchaAppIdPublicKeyGet(publicKey, boardId, threadNum).body()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException(
                "Апи капчи отказал: ключ не зарегистрирован или апи изменился (${e.message}). " +
                    "Ключи для приложений выдаёт admin@2ch.hk.",
                e
            )
        }

        if (response.result != 1) {
            response.error?.let { throw ApiException(it) }
            throw IllegalStateException("Апи капчи не выдал id (код ${response.result})")
        }

        return response.id
    }

    private fun setting(name: String): String? =
        System.getProperty(name) ?: System.getenv(name)

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_THREAD_POSTS = 1000
        const val FILE_FIELD = "file[]"
    }
}
