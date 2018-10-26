package com.example.krpc

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType

internal suspend fun httpPost(httpClient: HttpClient, url: String, body: String, bodyType: Serialization): String {
	return httpClient.post(url) {
		val contentType = when (bodyType) {
			Serialization.JSON -> ContentType.Application.Json
			Serialization.PROTOBUF -> ContentType.Application.OctetStream
		}
		this.body = TextContent(body, contentType)
	}
}
