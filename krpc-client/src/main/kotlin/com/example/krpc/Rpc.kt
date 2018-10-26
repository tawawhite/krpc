package com.example.krpc

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.stringFromUtf8Bytes
import kotlinx.serialization.toUtf8Bytes

internal expect suspend fun httpPost(url: String, body: String, bodyType: Serialization): String

suspend fun <I : Any, O : Any> makeRpc(
    url: String,
	service: String,
	rpc: String,
    serialization: Serialization,
    request: I,
    serializationStrategy: SerializationStrategy<I>,
    deserializationStrategy: DeserializationStrategy<O>
): O {
	val postUrl = "$url/$service/$rpc"
	val body: String = when (serialization) {
		Serialization.JSON -> JSON.stringify(serializationStrategy, request)
		Serialization.PROTOBUF -> stringFromUtf8Bytes(ProtoBuf.dump(serializationStrategy, request))
	}
    val responseBody = httpPost(postUrl, body, serialization)
    return when (serialization) {
        Serialization.JSON -> JSON.parse(deserializationStrategy, responseBody)
        Serialization.PROTOBUF -> ProtoBuf.load(deserializationStrategy, responseBody.toUtf8Bytes())
    }
}
