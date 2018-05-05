package com.example.krpc.core

data class HttpStatus(
	val code: Int,
	val message: String
) {
	companion object {
		private val successfulCodeRange = 200 until 300
	}

	val successful = code in successfulCodeRange
}

data class HttpResponseBody(
	val content: String,
	val mediaType: String? = null
)

data class HttpResponse(
	val request: HttpRequest,
	val status: HttpStatus,
	val body: HttpResponseBody? = null,
	val headers: Map<String, List<String>> = emptyMap()
)