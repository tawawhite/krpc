package com.example.krpc

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf

internal expect suspend fun httpPost(url: String, body: String): String
internal expect suspend fun httpPost(url: String, body: ByteArray): ByteArray

suspend fun <I : Any, O : Any> makeRpc(
    url: String,
    service: String,
    rpc: String,
    serialization: Serialization,
    request: I,
    serializationStrategy: SerializationStrategy<I>,
    deserializationStrategy: DeserializationStrategy<Try<O>>
): Try<O> {
    val postUrl = "$url/$service/$rpc"
    return when (serialization) {
        Serialization.JSON -> {
            val body = JSON.stringify(serializationStrategy, request)
            val response = httpPost(postUrl, body)
            JSON.parse(deserializationStrategy, response)
        }
        Serialization.PROTOBUF -> {
            val body = ProtoBuf.dump(serializationStrategy, request)
            val response = httpPost(postUrl, body)
            ProtoBuf.load(deserializationStrategy, response)
        }
    }
}

suspend fun <I : Any, O : Any> makeRpcOrThrow(
    url: String,
    service: String,
    rpc: String,
    serialization: Serialization,
    request: I,
    serializationStrategy: SerializationStrategy<I>,
    deserializationStrategy: DeserializationStrategy<Try<O>>
): O {
    return when (val result = makeRpc(url, service, rpc, serialization, request, serializationStrategy, deserializationStrategy)) {
        is Success -> result.value
        is Failure -> throw FailureException(result.error, result.message)
    }
}
