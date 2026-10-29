package com.github.tor1ant.govnach

import com.github.tor1ant.govnach.client.Client
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.openapitools.client.apis.DefaultApi
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Живой прогон капчи без ключей приложения: id → показ → клики.
 *
 * Обращается в сеть, поэтому по умолчанию пропускается: `GOVNACH_CAPTCHA_TEST=1`.
 * Пост не отправляем — только проверяем, что капча выдаётся и что клики отвечают.
 */
class EmojiCaptchaSmokeTest {

    private fun client(): Client = Client(
        DefaultApi(
            httpClientConfig = { config ->
                config.install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }
        )
    )

    @Test
    fun captchaIsServedAndClickable() {
        if (System.getenv("GOVNACH_CAPTCHA_TEST") != "1") return

        runBlocking {
            val client = client()
            val captcha = client.requestEmojiCaptcha(boardId = "b")

            println("captcha id=${captcha.id.take(24)}… keyboard=${captcha.keyboardBase64.size}")
            assertTrue(captcha.imageBase64 != null, "капча должна прийти картинкой")
            assertTrue(captcha.keyboardBase64.isNotEmpty(), "капча должна прийти с клавиатурой")
            assertTrue(!captcha.isSolved, "свежая капча ещё не решена")

            var current = captcha
            repeat(4) { step ->
                current = client.clickEmojiCaptcha(captcha.id, emojiNumber = step)
                println("click $step -> solved=${current.isSolved} keyboard=${current.keyboardBase64.size}")
            }

            assertTrue(
                current.isSolved || current.keyboardBase64.isNotEmpty(),
                "клик должен либо решить капчу, либо дать новую клавиатуру"
            )
        }
    }
}
