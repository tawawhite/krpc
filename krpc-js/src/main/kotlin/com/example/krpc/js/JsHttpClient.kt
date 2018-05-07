package com.example.krpc.js

import com.example.krpc.HttpClient
import com.example.krpc.HttpRequest
import com.example.krpc.HttpResponse
import kotlinx.coroutines.experimental.await
import org.w3c.fetch.RequestInit
import kotlin.browser.window

class JsHttpClient : HttpClient {
	override suspend fun send(request: HttpRequest): HttpResponse {
		val url = request.url
		val body = request.body?.content
		val mediaType = request.body?.mediaType
		val headers = if (mediaType == null) {
			request.headers
		} else {
			request.headers + Pair("Content-Type", mediaType)
		}
		val method = request.method.toString()
		val response = window.fetch(url, object : RequestInit {
			override var body: dynamic = body
			override var headers: dynamic = headers
			override var method: String? = method
		}).await()
		TODO()
	}
}