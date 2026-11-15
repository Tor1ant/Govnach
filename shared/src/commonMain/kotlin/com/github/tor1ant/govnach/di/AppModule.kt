package com.github.tor1ant.govnach.di

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.AppLog
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.PreferencesAccessStore
import com.github.tor1ant.govnach.client.ServerAccessState
import com.github.tor1ant.govnach.client.serverAccessPlugin
import com.github.tor1ant.govnach.ui.media.MediaImageLoader
import com.github.tor1ant.govnach.ui.media.MediaUrlResolver
import com.github.tor1ant.govnach.ui.media.platformMediaOpener
import com.github.tor1ant.govnach.ui.model.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import org.koin.dsl.module
import org.openapitools.client.apis.DefaultApi
import java.io.File

val appModule = module {

    // Подтверждения двача (возраст, пароль постинга): одни на всё приложение.
    single<AccessStore> { PreferencesAccessStore }
    single { ServerAccessState(store = get()) }

    single {
        val access = get<ServerAccessState>()
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

                // Важен порядок: сначала сохранённые подтверждения (возраст), потом HttpCookies —
                // он переписывает заголовок Cookie своим состоянием и без своей cookie
                // удалил бы чужой, поэтому идёт вторым.
                clientConfig.install(serverAccessPlugin(access))
                clientConfig.install(HttpCookies)
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
            // Картинки на дваче отдаются без проверки возраста, но cookie-плагин
            // и здесь не помешает: домен тот же, подтверждение уже получено.
            install(HttpCookies)
        }
    }

    single { MediaImageLoader(get()) }
    single { MediaUrlResolver(get()) }
    single { platformMediaOpener() }
    // Один журнал на всё приложение: клиент пишет в него, консоль его показывает.
    single { AppLog() }
    single { Client(get(), get(), get()) }
    single { HomeViewModel(client = get()) }
}