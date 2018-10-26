package com.example.krpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

private val httpClient = HttpClient(CIO)

internal actual suspend fun httpPost(url: String, body: String): String {
    return httpPost(httpClient, url, body)
}

internal actual suspend fun httpPost(url: String, body: ByteArray): ByteArray {
    return httpPost(httpClient, url, body)
}
