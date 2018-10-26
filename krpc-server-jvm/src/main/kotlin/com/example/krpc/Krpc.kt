package com.example.krpc

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationFeature
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.contentType
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.stringFromUtf8Bytes
import kotlinx.serialization.toUtf8Bytes

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
            call.respond(HttpStatusCode.NotFound, "")
            return
        }

        // Call handler
        runHandler(handler, call)
    }

    private suspend fun <I : Any, O : Any> runHandler(handler: RpcHandler<I, O>, call: ApplicationCall) {
        // TODO use Accept header
        val contentType = call.request.contentType()

        val requestBody = call.receiveText()
        val request = when (contentType) {
            ContentType.Application.Json -> JSON.parse(handler.deserializationStrategy, requestBody)
            ContentType.Application.OctetStream -> ProtoBuf.load(
                handler.deserializationStrategy,
                requestBody.toUtf8Bytes()
            )
            else -> {
                call.respond(
                    HttpStatusCode.UnsupportedMediaType,
                    "ContentType '$contentType' is not supported. Must be either '${ContentType.Application.Json}' or '${ContentType.Application.OctetStream}'."
                )
                return
            }
        }

        val response = handler.run(request)
        when (contentType) {
            ContentType.Application.Json -> {
                try {
                    val json = JSON.stringify(handler.serializationStrategy, response)
                    call.respondText(json, contentType)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
            }
            ContentType.Application.OctetStream -> {
                val content = stringFromUtf8Bytes(ProtoBuf.dump(handler.serializationStrategy, response))
                call.respondText(content, contentType)
            }
        }
    }
}
