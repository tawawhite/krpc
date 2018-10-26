package com.example.krpc

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.content.ByteArrayContent
import io.ktor.content.TextContent
import io.ktor.http.ContentType

internal suspend fun httpPost(httpClient: HttpClient, url: String, body: String): String {
    return httpClient.post(url) {
        this.body = TextContent(body, ContentType.Application.Json)
    }
}

internal suspend fun httpPost(httpClient: HttpClient, url: String, body: ByteArray): ByteArray {
    return httpClient.post(url) {
        this.body = ByteArrayContent(body, ContentType.Application.OctetStream)
    }
}
