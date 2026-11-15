package com.github.tor1ant.govnach.di

import com.github.tor1ant.govnach.client.AccessStore
import com.github.tor1ant.govnach.client.AppLog
import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.client.PreferencesAccessStore
import com.github.tor1ant.govnach.client.ServerAccessCookies
import com.github.tor1ant.govnach.client.ServerAccessState
import com.github.tor1ant.govnach.client.serverAccessPlugin
import com.github.tor1ant.govnach.ui.media.MediaImageLoader
import com.github.tor1ant.govnach.ui.media.MediaSourceResolver
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
import org.koin.core.qualifier.named
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

                // Порядок как в приложении: сначала подтверждения (возраст, ключ доступа), потом
                // HttpCookies — он дополняет заголовок Cookie тем, что пришло в ответах сеанса.
                clientConfig.install(serverAccessPlugin(access))
                clientConfig.install(HttpCookies) {
                    // Хранилище отдаёт сохранённые подтверждения для адресов двача и только для них.
                    // Без этого HttpCookies на этапе отправки переписывает заголовок своим (пустым)
                    // состоянием и удаляет ключ — а с ним и доступ к закрытым разделам.
                    storage = ServerAccessCookies(access)
                }
            }
        )
    }

    /**
     * Отдельный клиент для медиа без content negotiation: тянет сырые байты,
     * диск-кэш okhttp переиспользует превью и картинки между проходами по треду.
     *
     * Подтверждения здесь так же обязательны, как и в апи: файлы закрытых разделов
     * (`/h/`, `/ho/`, `/e/`, `/nf/`, `/fet/`) отдаются только с ключом доступа, а без него
     * сервер отвечает 500 «Not found» — именно поэтому превью и видео не грузились.
     */
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    cache(Cache(File(System.getProperty("java.io.tmpdir"), "govnach-media-cache"), 64L * 1024 * 1024))
                }
            }
            install(serverAccessPlugin(get<ServerAccessState>()))
            install(HttpCookies) {
                storage = ServerAccessCookies(get<ServerAccessState>())
            }
        }
    }

    /**
     * Клиент без подтверждений: им проверяется, отдаётся ли файл без ключа доступа.
     * Отдельный экземпляр нужен, чтобы проверка не делила cookie-состояние с основным.
     */
    single(qualifier = named("anonymous")) { HttpClient(OkHttp) }

    single { MediaImageLoader(get()) }
    single { MediaUrlResolver(get()) }
    single { MediaSourceResolver(http = get(), anonymous = get(named("anonymous"))) }
    single { platformMediaOpener() }
    // Один журнал на всё приложение: клиент пишет в него, консоль его показывает.
    single { AppLog() }
    single { Client(get(), get(), get()) }
    single { HomeViewModel(client = get()) }
}