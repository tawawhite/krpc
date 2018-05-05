package com.example.krpc.core

class HttpCallException(val httpResponse: HttpResponse) : RuntimeException()