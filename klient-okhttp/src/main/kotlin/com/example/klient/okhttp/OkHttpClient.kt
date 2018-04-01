package com.example.klient.okhttp

import com.example.klient.core.HttpClient
import com.example.klient.core.HttpRequest
import com.example.klient.core.HttpRequestBody
import com.example.klient.core.HttpResponse
import com.example.klient.core.HttpStatus
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class OkHttpClient(private val okHttpClient: okhttp3.OkHttpClient) : HttpClient {
	override suspend fun send(request: HttpRequest): HttpResponse {
		val okHttpRequest = mapRequest(request)
		return okHttpClient.newCall(okHttpRequest.build()).await().use { okHttpResponse ->
			mapResponse(okHttpResponse, request)
		}
	}

	private fun mapRequest(request: HttpRequest): Request.Builder {
		val body = request.body?.let { body ->
			RequestBody.create(body.mediaType?.let { MediaType.parse(it) }, body.content)
		}
		return Request.Builder()
				.url(request.url)
				.method(request.method.name, body)
				.headers(Headers.of(request.headers))
	}

	private fun mapResponse(okHttpResponse: Response, request: HttpRequest): HttpResponse {
		val body = okHttpResponse.body()?.let { HttpRequestBody(it.string(), it.contentType().toString()) }
		return HttpResponse(
				request = request,
				status = HttpStatus(okHttpResponse.code(), okHttpResponse.message()),
				body = body,
				headers = okHttpResponse.headers().toMultimap()
		)
	}
}