package com.github.tor1ant.govnach.client

import org.openapitools.client.apis.DefaultApi
import org.openapitools.client.models.Board

class Client(
    private val client: DefaultApi
) {

    suspend fun getBoards(): List<Board> {
        return client.apiMobileV2BoardsGet().body()
    }

}