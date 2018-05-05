package com.example.krpc

import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf

suspend fun <I : Any, O : Any> makeCall(
	httpClient: HttpClient,
	url: String,
	serialization: Serialization,
	request: I,
	requestSaver: KSerialSaver<I>,
	responseLoader: KSerialLoader<O>
): O {
	val body = when (serialization) {
		Serialization.JSON -> HttpRequestBody(
			JSON.stringify(requestSaver, request),
			Serialization.JSON.mediaType
		)
		Serialization.PROTOBUF -> HttpRequestBody(
			encodeBase64(ProtoBuf.dump(requestSaver, request)),
			Serialization.PROTOBUF.mediaType
		)
	}
	val httpResponse = httpClient.send(
		HttpRequest(
			url,
			HttpMethod.POST,
			body
		)
	)
	if (httpResponse.status.successful) {
		val responseBody = httpResponse.body!!.content
		return when (serialization) {
			Serialization.JSON -> JSON.parse(responseLoader, responseBody)
			Serialization.PROTOBUF -> ProtoBuf.load(responseLoader, decodeBase64(responseBody))
		}
	} else {
		// TODO parse FailureException
		throw Exception()
	}
}

fun encodeBase64(bytes: ByteArray): String {
	TODO()
}

fun decodeBase64(string: String): ByteArray {
	TODO()
}
