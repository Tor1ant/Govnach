package com.github.tor1ant.govnach.di

import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.ui.media.MediaImageLoader
import com.github.tor1ant.govnach.ui.media.platformMediaOpener
import com.github.tor1ant.govnach.ui.model.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import org.koin.dsl.module
import org.openapitools.client.apis.DefaultApi
import java.io.File

val appModule = module {

    single {
        DefaultApi(
            httpClientConfig = { clientConfig ->
                clientConfig.install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true

                        serializersModule = kotlinx.serialization.modules.SerializersModule {
                        }
                    })
                }
            }
        )
    }

    /**
     * Отдельный клиент для медиа без content negotiation: тянет сырые байты,
     * диск-кэш okhttp переиспользует превью и картинки между проходами по треду.
     */
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    cache(Cache(File(System.getProperty("java.io.tmpdir"), "govnach-media-cache"), 64L * 1024 * 1024))
                }
            }
        }
    }

    single { MediaImageLoader(get()) }
    single { platformMediaOpener() }
    single { Client(get()) }
    single { HomeViewModel(client = get()) }
}
