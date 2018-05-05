package com.example.krpc

class HttpCallException(val httpResponse: HttpResponse) : RuntimeException()