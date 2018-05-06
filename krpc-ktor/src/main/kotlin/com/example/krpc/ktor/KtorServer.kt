package com.example.krpc.ktor

import com.example.krpc.RpcHandler
import com.example.krpc.Serialization
import com.example.krpc.Server
import com.example.krpc.ServiceHandler
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
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.stringFromUtf8Bytes
import kotlinx.serialization.toUtf8Bytes
import java.util.concurrent.TimeUnit

class KtorServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
	factory: ApplicationEngineFactory<TEngine, TConfiguration>,
	port: Int = 80,
	host: String = "0.0.0.0"
) : Server {

	private companion object {
		const val serviceArg = "service"
		const val rpcArg = "rpc"
	}

	private val serviceBinders = HashMap<String, ServiceHandler>()

	val engine = embeddedServer(factory, port, host) {
		routing {
			post("/{$serviceArg}/{$rpcArg}") {
				handleCall(context)
			}
		}
	}

	override fun start(wait: Boolean) {
		engine.start(wait)
	}

	override fun stop(gracePeriod: Long, timeout: Long) {
		engine.stop(gracePeriod, timeout, TimeUnit.MILLISECONDS)
	}

	override fun bindService(serviceHandler: ServiceHandler) {
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
				requestBody.toUtf8Bytes()
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
				val content = stringFromUtf8Bytes(ProtoBuf.dump(handler.responseSaver, response))
				call.respondText(content, ContentType.Application.OctetStream)
			}
		}
	}
}