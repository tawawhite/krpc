package com.example.krpc.core

enum class HttpMethod {
	DELETE,
	GET,
	HEAD,
	OPTIONS,
	PATCH,
	POST,
	PUT,
	TRACE
}

data class HttpRequestBody(
	val content: String,
	val mediaType: String? = null
)

data class HttpRequest(
	val url: String,
	val method: HttpMethod,
	val body: HttpRequestBody? = null,
	val headers: Map<String, String> = emptyMap()
)