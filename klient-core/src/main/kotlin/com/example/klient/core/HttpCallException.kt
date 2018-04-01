package com.example.klient.core

class HttpCallException(val httpResponse: HttpResponse) : RuntimeException()