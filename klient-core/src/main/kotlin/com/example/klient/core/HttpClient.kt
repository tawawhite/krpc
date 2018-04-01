package com.example.klient.core

interface HttpClient {

	suspend fun send(request: HttpRequest): HttpResponse

}