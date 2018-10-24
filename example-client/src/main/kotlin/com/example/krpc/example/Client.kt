package com.example.krpc.example

import com.example.krpc.Serialization
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val userService = UserService.client(HttpClient(CIO), "http://127.0.0.1:8080", Serialization.PROTOBUF)
    runBlocking {
        println(userService.getUser(GetRequest(1)))
    }
}
