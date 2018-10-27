package com.example.krpc

import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.js.Js
import io.ktor.client.response.readBytes
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod

private val httpClient = HttpClient(Js)

internal actual suspend fun httpPost(url: String, body: String): String {
    return httpPost(httpClient, url, body)
}

internal actual suspend fun httpPost(url: String, body: ByteArray): ByteArray {
    // TODO: Replace by httpPost(httpClient, url, body) once receiving ByteArray is implemented in Ktor JS.
    return httpClient.call(url) {
        method = HttpMethod.Post
        this.body = ByteArrayContent(body, ContentType.Application.OctetStream)
    }.response.readBytes()
}
