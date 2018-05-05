package com.example.krpc

interface HttpClient {

	suspend fun send(request: HttpRequest): HttpResponse

}