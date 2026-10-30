package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.ui.media.MediaPaths
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.openapitools.client.models.CatalogThreads
import org.openapitools.client.models.FileModel
import org.openapitools.client.models.MobileThreadPostsAfter
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Интеграционный прогон медиа-ссылок двача: превью и исходник должны скачиваться
 * по путям FileModel.path/thumbnail с base-url клиента.
 *
 * Включается так же, как RealApiSmokeTest: `GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest`
 */
class RealApiMediaTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun thumbnailAndSrcDownload() {
        if (System.getenv("GOVNACH_API_TEST") != "1") return

        runBlocking {
            val http = HttpClient(OkHttp)

            val catalog = json.decodeFromString<CatalogThreads>(
                String(bytesOf(http.get(MediaPaths.BASE_URL + "/b/catalog.json")))
            )
            val threads = catalog.threads.orEmpty()
            assertTrue(threads.isNotEmpty(), "catalog.json должен вернуть треды")

            val thread = threads.maxByOrNull { it.filesCount ?: 0 }!!
            val posts = json.decodeFromString<MobileThreadPostsAfter>(
                String(bytesOf(http.get(MediaPaths.BASE_URL + "/api/mobile/v2/after/${thread.board}/${thread.num}/${thread.num}")))
            ).posts.orEmpty()

            val files: List<FileModel> = posts.flatMap { it.files.orEmpty() }
            assertTrue(files.isNotEmpty(), "в треде ${thread.num} должны быть файлы")

            val image = files.firstOrNull { it.type in IMAGE_TYPES }
            val video = files.firstOrNull { it.type in VIDEO_TYPES }

            image?.let { file ->
                val tn = http.get(MediaPaths.thumbnail(file))
                assertTrue(tn.status.value in 200..299, "превью ${file.thumbnail} должно скачиваться")
                assertTrue(bytesOf(tn).size > 512, "превью не должно быть пустым")

                val src = http.get(MediaPaths.src(file))
                assertTrue(src.status.value in 200..299, "исходник ${file.path} должен скачиваться")
                assertTrue(bytesOf(src).size > 1024, "исходник не должен быть пустым")
            }

            video?.let { file ->
                val tn = http.get(MediaPaths.thumbnail(file))
                assertTrue(tn.status.value in 200..299, "превью видео ${file.thumbnail} должно скачиваться")

                val src = http.get(MediaPaths.src(file))
                assertTrue(src.status.value in 200..299, "видео ${file.path} должно скачиваться")
            }

            http.close()
        }
    }

    private suspend fun bytesOf(response: io.ktor.client.statement.HttpResponse): ByteArray =
        response.bodyAsChannel().readRemaining().readBytes()

    private companion object {
        val IMAGE_TYPES = intArrayOf(1, 2, 3, 4, 5, 9)
        val VIDEO_TYPES = intArrayOf(6, 10)
    }
}
