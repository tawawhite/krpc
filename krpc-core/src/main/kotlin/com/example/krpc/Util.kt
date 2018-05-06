package com.example.krpc

import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.stringFromUtf8Bytes
import kotlinx.serialization.toUtf8Bytes

suspend fun <I : Any, O : Any> makeRpc(
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
			stringFromUtf8Bytes(ProtoBuf.dump(requestSaver, request)),
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
			Serialization.PROTOBUF -> ProtoBuf.load(responseLoader, responseBody.toUtf8Bytes())
		}
	} else {
		// TODO parse FailureException
		throw Exception()
	}
}