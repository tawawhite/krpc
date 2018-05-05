package com.example.krpc.example

import com.example.krpc.HttpClient
import com.example.krpc.RpcHandler
import com.example.krpc.Serialization
import com.example.krpc.ServiceHandler
import com.example.krpc.ktor.KtorServer
import com.example.krpc.makeCall
import com.example.krpc.okhttp.OkHttpClient
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class User(
	val id: Int,
	val firstName: String,
	val lastName: String
)

@Serializable
data class GetRequest(val id: Int)

@Serializable
data class GetResponse(val user: User)

interface UserService {
	companion object;

	suspend fun getUser(request: GetRequest): GetResponse
}

object UserServiceImpl : UserService {
	override suspend fun getUser(request: GetRequest): GetResponse {
		return GetResponse(User(request.id, "Jordan", "Demeulenaere"))
	}
}

fun UserService.Companion.create(
	httpClient: HttpClient,
	baseUrl: String,
	serialization: Serialization = Serialization.JSON
): UserService {
	return object : UserService {
		override suspend fun getUser(request: GetRequest): GetResponse {
			val url = "$baseUrl/UserService/getUser"
			return makeCall(httpClient, url, serialization, request, GetRequest.serializer(), GetResponse.serializer())
		}
	}
}

// TODO no two function can have the same name
// TODO handle number of arguments = 0?
fun UserService.Companion.handler(implementation: UserService): ServiceHandler {
	return object : ServiceHandler {
		override val serviceName: String = "UserService"

		override fun rpcHandler(rpc: String): RpcHandler<*, *>? {
			return when (rpc) {
				"getUser" -> object : RpcHandler<GetRequest, GetResponse> {
					override val requestLoader = GetRequest.serializer()
					override val responseSaver = GetResponse.serializer()
					override suspend fun run(request: GetRequest): GetResponse = implementation.getUser(request)
				}
				else -> null
			}
		}
	}
}

fun main(args: Array<String>) {
	val port = 8080
	KtorServer(Netty, port = port).run {
		bindService(UserService.handler(UserServiceImpl))
		start()
	}

	val httpClient = OkHttpClient(okhttp3.OkHttpClient())
	val userService = UserService.create(httpClient, "http://127.0.0.1:$port")
	runBlocking {
		println(userService.getUser(GetRequest(42)))
	}
}