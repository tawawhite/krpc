package com.example.krpc.example

import com.example.krpc.FailureException
import com.example.krpc.Serialization
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val userService = UserService.client("http://127.0.0.1:8080", Serialization.JSON)
    runBlocking {
        GetRequest(42).let { println("$it => ${userService.tryGetUser(it)}") }
        GetRequest(-1).let { println("$it => ${userService.tryGetUser(it)}") }
        GetRequest(42).let { println("$it => ${userService.getUser(it)}") }
        GetRequest(-1).let { println("$it => ${try {
            userService.getUser(it)
        } catch (e: FailureException) {
            "threw exception: $e"
        }}") }
    }
}
