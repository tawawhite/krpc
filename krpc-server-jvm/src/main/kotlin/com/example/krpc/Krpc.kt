package com.example.krpc

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationFeature
import io.ktor.http.ContentType
import io.ktor.request.contentType
import io.ktor.request.receiveStream
import io.ktor.request.receiveText
import io.ktor.response.respondBytes
import io.ktor.response.respondText
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf

class Krpc private constructor(configuration: Configuration) {
    private val services = configuration.services.associateBy { it.serviceName }

    class Configuration {
        var services = emptyList<ServiceHandler>()
    }

    companion object Feature : ApplicationFeature<Application, Configuration, Krpc> {
        override val key = AttributeKey<Krpc>("krpc")
        private const val serviceArg = "service"
        private const val rpcArg = "rpc"

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): Krpc {
            val configuration = Configuration()
            configuration.configure()
            val feature = Krpc(configuration)
            pipeline.routing {
                post("/{$serviceArg}/{$rpcArg}") {
                    feature.interceptCall(context, ::proceed)
                }
            }
            return feature
        }
    }

    private suspend fun interceptCall(call: ApplicationCall, proceed: suspend () -> Unit) {
        // Get service.
        val service = services[call.parameters[serviceArg]!!]
        if (service == null) {
            proceed()
            return
        }

        // Get RPC handler.
        val handler = service.rpcHandler(call.parameters[rpcArg]!!)
        if (handler == null) {
            proceed()
            return
        }

        // Call handler
        runHandler(handler, call)
    }

    private suspend fun <I : Any, O : Any> runHandler(handler: RpcHandler<I, O>, call: ApplicationCall) {
        // TODO use Accept header
        val contentType = call.request.contentType()
        val request: Try<I> = Try(ErrorMappers.INTERNAL.printingStackTraces()) {
            when (contentType) {
                ContentType.Application.Json -> JSON.parse(handler.deserializationStrategy, call.receiveText())
                ContentType.Application.OctetStream -> ProtoBuf.load(
                    handler.deserializationStrategy,
                    call.receiveStream().readBytes()
                )
                else -> {
                    raise(Error.INVALID_ARGUMENT, "ContentType '$contentType' is not supported. Must be either '${ContentType.Application.Json}' or '${ContentType.Application.OctetStream}'.")
                }
            }
        }

        val response = request.flatMap { handler.run(it) }
        val serializationStrategy = handler.serializationStrategy
        when (contentType) {
            ContentType.Application.Json -> {
                val serializedResponse = Try(ErrorMappers.INTERNAL.printingStackTraces()) {
                    JSON.stringify(serializationStrategy, response)
                }
                val text = when (serializedResponse) {
                    is Success -> serializedResponse.value
                    is Failure -> JSON.stringify(serializationStrategy, serializedResponse)
                }
                call.respondText(text, contentType)
            }
            ContentType.Application.OctetStream -> {
                val serializedResponse = Try(ErrorMappers.INTERNAL.printingStackTraces()) {
                    ProtoBuf.dump(serializationStrategy, response)
                }
                val bytes = when (serializedResponse) {
                    is Success -> serializedResponse.value
                    is Failure -> ProtoBuf.dump(serializationStrategy, serializedResponse)
                }
                call.respondBytes(bytes, contentType)
            }
        }
    }
}
