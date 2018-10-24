package com.example.krpc

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.content.TextContent
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.stringFromUtf8Bytes
import kotlinx.serialization.toUtf8Bytes

suspend fun <I : Any, O : Any> makeRpc(
    httpClient: HttpClient,
    url: String,
    serialization: Serialization,
    request: I,
    serializationStrategy: SerializationStrategy<I>,
    deserializationStrategy: DeserializationStrategy<O>
): O {
    val responseBody = httpClient.post<String>(url) {
        val text: String = when (serialization) {
            Serialization.JSON -> JSON.stringify(serializationStrategy, request)
            Serialization.PROTOBUF -> stringFromUtf8Bytes(ProtoBuf.dump(serializationStrategy, request))
        }
        body = TextContent(text, serialization.contentType)
    }
    return when (serialization) {
        Serialization.JSON -> JSON.parse(deserializationStrategy, responseBody)
        Serialization.PROTOBUF -> ProtoBuf.load(deserializationStrategy, responseBody.toUtf8Bytes())
    }
}
