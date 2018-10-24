package com.example.krpc

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

interface ServiceHandler {
    val serviceName: String

    fun rpcHandler(rpc: String): RpcHandler<*, *>?
}

interface RpcHandler<I : Any, O : Any> {
    val deserializationStrategy: DeserializationStrategy<I>

    val serializationStrategy: SerializationStrategy<O>

    suspend fun run(request: I): O
}

fun <I : Any, O : Any> rpcHandler(
    deserializationStrategy: DeserializationStrategy<I>,
    serializationStrategy: SerializationStrategy<O>,
    run: suspend (I) -> O
): RpcHandler<I, O> {
    return object : RpcHandler<I, O> {
        override val deserializationStrategy: DeserializationStrategy<I> = deserializationStrategy
        override val serializationStrategy: SerializationStrategy<O> = serializationStrategy

        override suspend fun run(request: I): O = run(request)
    }
}
