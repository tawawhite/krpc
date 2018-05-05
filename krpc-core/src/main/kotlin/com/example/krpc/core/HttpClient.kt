package com.example.krpc.core

interface HttpClient {

	suspend fun send(request: HttpRequest): HttpResponse

}