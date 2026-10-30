package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.LocalFile
import com.github.tor1ant.govnach.client.PostingDraft
import com.github.tor1ant.govnach.client.PostingProblem
import com.github.tor1ant.govnach.client.PostingRules
import com.github.tor1ant.govnach.client.errorCodeMessage
import com.github.tor1ant.govnach.client.postingProblemsMessage
import com.github.tor1ant.govnach.client.validatePosting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверка черновика поста на ограничения доски — всё офлайн, файлы подменяют fakeFileOf.
 */
class PostingValidationTest {

    /** Правила как у /b/: 15000 символов комментария, 40 МБ файла (max_files_size в КБ). */
    private val boardRules = PostingRules(
        postingEnabled = true,
        allowName = true,
        allowSubject = false,
        maxComment = 15_000,
        maxFilesKb = 40_960,
        fileTypes = setOf("jpg", "png", "gif", "webp")
    )

    private val replyDraft = PostingDraft(boardId = "b", threadNum = 3817293, comment = "анон с картинкой")
    private val okFile = LocalFile(name = "cat.jpg", sizeBytes = 512 * 1024)

    private fun fakeFiles(vararg sizes: Pair<String, Long>): (String) -> LocalFile? {
        val byPath = sizes.toMap()
        return { path -> byPath[path]?.let { LocalFile(path.substringAfterLast('/'), it) } }
    }

    @Test
    fun cleanReplyHasNoProblems() {
        val problems = validatePosting(
            rules = boardRules,
            draft = replyDraft.copy(files = listOf("/tmp/cat.jpg")),
            fileOf = fakeFiles("/tmp/cat.jpg" to okFile.sizeBytes)
        )

        assertTrue(problems.isEmpty(), "обычный ответ в тред не должен давать проблем: $problems")
    }

    @Test
    fun disabledBoardShortCircuitsToSingleProblem() {
        val problems = validatePosting(
            rules = boardRules.copy(postingEnabled = false),
            draft = PostingDraft(boardId = "b", comment = "", files = listOf("/tmp/nope.xyz")),
            fileOf = { null }
        )

        assertEquals(listOf(PostingProblem.POSTING_DISABLED), problems)
    }

    @Test
    fun emptyDraftNeedsCommentOrFile() {
        val reply = validatePosting(boardRules, PostingDraft(boardId = "b", threadNum = 1), fileOf = { null })

        assertEquals(listOf(PostingProblem.EMPTY_DRAFT), reply)

        val newThread = validatePosting(
            rules = boardRules.copy(maxComment = 0),
            draft = PostingDraft(boardId = "b", comment = ""),
            fileOf = { null }
        )

        assertTrue(PostingProblem.THREAD_NEEDS_FILE in newThread, "ОП-пост без файла: $newThread")
    }

    @Test
    fun commentLongerThanBoardLimit() {
        val problems = validatePosting(
            rules = boardRules.copy(maxComment = 10),
            draft = replyDraft.copy(comment = "а".repeat(11)),
            fileOf = fakeFiles("/tmp/cat.jpg" to okFile.sizeBytes)
        )

        assertEquals(listOf(PostingProblem.COMMENT_TOO_LONG), problems)
    }

    @Test
    fun boardFileTypeListIsCheckedByExtension() {
        val forbidden = validatePosting(
            rules = boardRules,
            draft = replyDraft.copy(files = listOf("/tmp/clip.mp4")),
            fileOf = fakeFiles("/tmp/clip.mp4" to 1024L)
        )

        assertEquals(listOf(PostingProblem.FILE_TYPE_FORBIDDEN), forbidden)

        val jpegIsJpg = validatePosting(
            rules = boardRules,
            draft = replyDraft.copy(files = listOf("/tmp/Cat.JPEG")),
            fileOf = fakeFiles("/tmp/Cat.JPEG" to 1024L)
        )

        assertTrue(jpegIsJpg.isEmpty(), "jpeg — то же что jpg: $jpegIsJpg")
    }

    @Test
    fun missingFileAtPathReported() {
        val problems = validatePosting(
            rules = boardRules,
            draft = replyDraft.copy(files = listOf("/tmp/cat.jpg")),
            fileOf = fakeFiles()
        )

        assertEquals(listOf(PostingProblem.FILE_MISSING), problems)
    }

    @Test
    fun fileSizeOverBoardLimitButExactlyAtLimitIsFine() {
        val limitBytes = 2 * 1024L

        val tooBig = validatePosting(
            rules = boardRules.copy(maxFilesKb = 2),
            draft = replyDraft.copy(files = listOf("/tmp/cat.jpg")),
            fileOf = fakeFiles("/tmp/cat.jpg" to limitBytes + 1)
        )
        assertEquals(listOf(PostingProblem.FILE_TOO_BIG), tooBig)

        val exactlyLimit = validatePosting(
            rules = boardRules.copy(maxFilesKb = 2),
            draft = replyDraft.copy(files = listOf("/tmp/cat.jpg")),
            fileOf = fakeFiles("/tmp/cat.jpg" to limitBytes)
        )
        assertTrue(exactlyLimit.isEmpty(), "ровно лимит — ещё можно: $exactlyLimit")
    }

    @Test
    fun newThreadWithoutFileIsRejectedButReplyNeedsNoFile() {
        val problems = validatePosting(
            rules = boardRules,
            draft = PostingDraft(boardId = "b", comment = "название треда"),
            fileOf = fakeFiles()
        )

        assertEquals(listOf(PostingProblem.THREAD_NEEDS_FILE), problems)

        val replyWithoutFile = validatePosting(boardRules, replyDraft, fileOf = fakeFiles())
        assertTrue(replyWithoutFile.isEmpty(), "ответ в тред без файла легален: $replyWithoutFile")
    }

    @Test
    fun problemMessageCarriesBoardLimits() {
        val message = postingProblemsMessage(
            problems = listOf(PostingProblem.COMMENT_TOO_LONG, PostingProblem.FILE_MISSING),
            rules = boardRules
        )

        assertTrue(message.contains("15000"), "лимит символов в тексте: $message")
        assertTrue(message.contains("не найден"), "файл не найден в тексте: $message")
    }

    @Test
    fun errorCodesHaveReadableMessages() {
        assertEquals(
            "Тред нельзя создать без файла",
            errorCodeMessage(-19)
        )
        assertTrue(errorCodeMessage(-302)?.contains("180") == true, "-302 про ttl: ${errorCodeMessage(-302)}")

        val withoutAppid = listOf(-5, -6, -7, -20, 403)
        for (code in withoutAppid) {
            assertTrue(errorCodeMessage(code)?.isNotBlank() == true, "код $code должен быть подписан")
        }

        assertEquals(null, errorCodeMessage(-9999), "неизвестный код без подписи")
    }
}
