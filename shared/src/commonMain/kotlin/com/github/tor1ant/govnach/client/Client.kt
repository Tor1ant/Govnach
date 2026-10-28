package com.github.tor1ant.govnach.client

import org.openapitools.client.apis.DefaultApi
import org.openapitools.client.models.Board
import org.openapitools.client.models.Error
import org.openapitools.client.models.Post
import org.openapitools.client.models.ThreadPreview

/**
 * Ошибка, которую вернул двач в теле ответа (поле error при HTTP 200).
 */
class ApiException(
    val error: Error,
    message: String = error.message ?: "Ошибка запроса (код ${error.code ?: "неизвестен"})"
) : Exception(message)

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

    private companion object {
        const val MAX_THREAD_POSTS = 1000
    }
}
