package com.example.krpc.js

import com.example.krpc.FailureException
import com.example.krpc.Serialization
import com.example.krpc.example.GetRequest
import com.example.krpc.example.UserService
import com.example.krpc.example.client
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    GlobalScope.launch {
        listOf(Serialization.JSON, Serialization.PROTOBUF).forEach { serialization ->
            println("=== Performing requests using $serialization serialization ===")
            val userService = UserService.client("http://127.0.0.1:8080", serialization)
            GetRequest(42).let { println("$it => ${userService.tryGetUser(it)}") }
            GetRequest(-1).let { println("$it => ${userService.tryGetUser(it)}") }
            GetRequest(42).let { println("$it => ${userService.getUser(it)}") }
            GetRequest(-1).let {
                println(
                    "$it => ${try {
                        userService.getUser(it)
                    } catch (e: FailureException) {
                        "threw exception: $e"
                    }}"
                )
            }
            println()
        }
    }
}
