package com.example.krpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

private val httpClient = HttpClient(Js)

internal actual suspend fun httpPost(url: String, body: String, bodyType: Serialization): String {
    return httpPost(httpClient, url, body, bodyType)
}
