package com.example.krpc

import io.ktor.http.ContentType

enum class Serialization(val contentType: ContentType) {
    JSON(ContentType.Application.Json),
    PROTOBUF(ContentType.Application.OctetStream)
}
