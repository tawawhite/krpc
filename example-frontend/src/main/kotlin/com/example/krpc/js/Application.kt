package com.example.krpc.js

import com.example.krpc.Serialization
import com.example.krpc.example.GetRequest
import com.example.krpc.example.UserService
import com.example.krpc.example.client
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    val userService = UserService.client("http://127.0.0.1:8080", Serialization.PROTOBUF)
    GlobalScope.launch {
        println(userService.getUser(GetRequest(1)))
    }
}
