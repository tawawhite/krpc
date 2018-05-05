package com.example.krpc.example

import com.example.krpc.core.HttpClient
import com.example.krpc.core.HttpMethod
import com.example.krpc.core.HttpRequest
import com.example.krpc.core.HttpRequestBody
import com.example.krpc.okhttp.OkHttpClient
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.contentCharset
import io.ktor.request.contentType
import io.ktor.request.receiveChannel
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.Base64
import java.util.concurrent.TimeUnit

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
			val body = when (serialization) {
				Serialization.JSON -> HttpRequestBody(
					JSON.stringify(GetRequest.serializer(), request),
					Serialization.JSON.mediaType
				)
				Serialization.PROTOBUF -> HttpRequestBody(
					Base64.getEncoder().encodeToString(ProtoBuf.dump(GetRequest.serializer(), request)),
					Serialization.PROTOBUF.mediaType
				)
			}
			val url = "$baseUrl/UserService/getUser"
			val httpResponse = httpClient.send(HttpRequest(url, HttpMethod.POST, body))
			if (httpResponse.status.successful) {
				val responseBody = httpResponse.body!!.content
				return when (serialization) {
					Serialization.JSON -> JSON.parse(GetResponse.serializer(), responseBody)
					Serialization.PROTOBUF -> ProtoBuf.load(GetResponse.serializer(), Base64.getDecoder().decode(responseBody))
				}
			} else {
				// TODO parse FailureException
				throw Exception()
			}
		}
	}
}

// TODO no two function can have the same name
// TODO handle number of arguments = 0?
fun UserService.Companion.binder(implementation: UserService): ServiceHandler {
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

enum class Serialization(val mediaType: String) {
	JSON("application/json"),
	PROTOBUF("application/octet-stream")
}

interface RpcHandler<I : Any, O : Any> {
	val requestLoader: KSerialLoader<I>

	val responseSaver: KSerialSaver<O>

	suspend fun run(request: I): O
}

interface ServiceHandler {
	val serviceName: String

	fun rpcHandler(rpc: String): RpcHandler<*, *>?
}

class Server(
	port: Int = 80,
	host: String = "0.0.0.0"
) : ApplicationEngine {

	private companion object {
		const val serviceArg = "service"
		const val rpcArg = "rpc"
	}

	private val serviceBinders = hashMapOf<String, ServiceHandler>()
	private val engine = embeddedServer(Netty, port, host) {
		routing {
			post("/{$serviceArg}/{$rpcArg}") {
				handleCall(context)
			}
		}
	}

	override val environment: ApplicationEngineEnvironment
		get() = engine.environment

	override fun start(wait: Boolean): ApplicationEngine = engine.start(wait)

	override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
		engine.stop(gracePeriod, timeout, timeUnit)
	}

	fun bindService(serviceHandler: ServiceHandler) {
		val name = serviceHandler.serviceName
		if (serviceBinders.containsKey(name)) {
			throw IllegalArgumentException("Server already bound to service named $name")
		}
		serviceBinders[name] = serviceHandler
	}

	private suspend fun handleCall(call: ApplicationCall) {
		// Get service
		val service = serviceBinders[call.parameters[serviceArg]!!]
		if (service == null) {
			call.respond(HttpStatusCode.NotFound, "")
			return
		}

		// Get handler
		val handler = service.rpcHandler(call.parameters[rpcArg]!!)
		if (handler == null) {
			call.respond(HttpStatusCode.NotFound, "")
			return
		}

		// Call handler
		runHandler(handler, call)
	}

	private suspend fun <I : Any, O : Any> runHandler(handler: RpcHandler<I, O>, call: ApplicationCall) {
		// TODO use Accept header
		val contentType = call.request.contentType()
		val mediaType = "${contentType.contentType}/${contentType.contentSubtype}"

		val requestBody = call.receiveChannel().toInputStream()
			.bufferedReader(call.request.contentCharset() ?: Charsets.UTF_8)
			.use { it.readText() }
		val request = when (mediaType) {
			Serialization.JSON.mediaType -> JSON.parse(handler.requestLoader, requestBody)
			Serialization.PROTOBUF.mediaType -> ProtoBuf.load(
				handler.requestLoader,
				Base64.getDecoder().decode(requestBody)
			)
			else -> {
				call.respond(
					HttpStatusCode.UnsupportedMediaType,
					"Media type '$mediaType' is not supported. Must be either '${Serialization.JSON.mediaType}' or '${Serialization.JSON.mediaType}'."
				)
				return
			}
		}

		val response = handler.run(request)
		when (mediaType) {
			Serialization.JSON.mediaType -> {
				val json = JSON.stringify(handler.responseSaver, response)
				call.respondText(json, ContentType.Application.Json)
			}
			Serialization.PROTOBUF.mediaType -> {
				val content = Base64.getEncoder().encodeToString(ProtoBuf.dump(handler.responseSaver, response))
				call.respondText(content, ContentType.Application.OctetStream)
			}
		}
	}
}

fun main(args: Array<String>) {
	val port = 8080
	Server(port = port).run {
		bindService(UserService.binder(UserServiceImpl))
		start()
	}

	val httpClient = OkHttpClient(okhttp3.OkHttpClient())
	val userService = UserService.create(httpClient, "http://127.0.0.1:$port")
	runBlocking {
		println(userService.getUser(GetRequest(42)))
	}
}