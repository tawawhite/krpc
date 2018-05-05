package com.example.krpc

enum class Serialization(val mediaType: String) {
	JSON("application/json"),
	PROTOBUF("application/octet-stream")
}