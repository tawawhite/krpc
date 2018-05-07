package com.example.krpc.example

import com.example.krpc.Serialization
import com.example.krpc.ktor.KtorServer
import com.example.krpc.okhttp.OkHttpClient
import com.example.krpc.processor.Service
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable

// @SerialId is not necessary if not using protobuf serialization
@Serializable
data class User(
	@SerialId(1) val id: Int,
	@SerialId(2) val firstName: String,
	@SerialId(3) val lastName: String
)

@Serializable
data class GetRequest(
	@SerialId(1) val id: Int
)

@Serializable
data class GetResponse(
	@SerialId(1) val user: User
)

@Service
interface UserService {
	companion object;

	suspend fun getUser(request: GetRequest): GetResponse
}

object UserServiceImpl : UserService {
	override suspend fun getUser(request: GetRequest): GetResponse {
		return GetResponse(User(request.id, "Jordan", "Demeulenaere"))
	}
}

private fun startServer(port: Int) {
	KtorServer(Netty, port = port).run {
		bindService(UserService.handler(UserServiceImpl))
		start()
	}
}

private fun makeRequest(port: Int) {
	val httpClient = OkHttpClient(okhttp3.OkHttpClient())
	val userService = UserService.create(httpClient, "http://127.0.0.1:$port", Serialization.PROTOBUF)
	runBlocking {
		println(userService.getUser(GetRequest(42)))
	}
}

fun main(args: Array<String>) {
	val port = 8080
	startServer(port)
	makeRequest(port)
}
