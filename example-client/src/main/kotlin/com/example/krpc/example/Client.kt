package com.example.krpc.example

import com.example.krpc.Serialization
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val userService = UserService.client("http://127.0.0.1:8080", Serialization.PROTOBUF)
    runBlocking {
        println(userService.getUser(GetRequest(1)))
    }
}
