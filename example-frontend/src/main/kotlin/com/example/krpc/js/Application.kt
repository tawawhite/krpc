package com.example.krpc.js

import com.example.krpc.Serialization
import com.example.krpc.example.GetRequest
import com.example.krpc.example.UserService
import com.example.krpc.example.client
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    val userService = UserService.client(HttpClient(Js), "", Serialization.PROTOBUF)
    GlobalScope.launch {
        println(userService.getUser(GetRequest(1)))
    }
}
