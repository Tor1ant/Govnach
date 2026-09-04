package com.github.tor1ant.govnach.di

import com.github.tor1ant.govnach.client.Client
import com.github.tor1ant.govnach.ui.model.HomeViewModel
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.koin.dsl.module
import org.openapitools.client.apis.DefaultApi

val appModule = module {

    single {
        DefaultApi(
            httpClientConfig = { clientConfig ->
                clientConfig.install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true

                        // ХАК ДЛЯ OPENAPI: явно регистрируем контекстные сериализаторы,
                        // если генератор пытается запрашивать List напрямую
                        serializersModule = SerializersModule {
                            // Здесь при необходимости можно добавить кастомные модули,
                            // но часто включение правильного контекста сериализации решает проблему
                        }
                    })
                }
            }
        )
    }
    single { Client(get()) }
    single { HomeViewModel(client = get()) }
}
