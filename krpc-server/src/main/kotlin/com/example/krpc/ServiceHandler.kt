package com.example.krpc

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

interface ServiceHandler {
    val serviceName: String

    fun rpcHandler(rpc: String): RpcHandler<*, *>?
}

interface RpcHandler<I : Any, O : Any> {
    val deserializationStrategy: DeserializationStrategy<I>

    val serializationStrategy: SerializationStrategy<Try<O>>

    suspend fun run(request: I): Try<O>
}

fun <I : Any, O : Any> unsafeRpcHandler(
    deserializationStrategy: DeserializationStrategy<I>,
    serializationStrategy: SerializationStrategy<Try<O>>,
    run: suspend (I) -> O
): RpcHandler<I, O> {
    return rpcHandler(deserializationStrategy, serializationStrategy) { input -> Try { run(input) } }
}

fun <I : Any, O : Any> rpcHandler(
    deserializationStrategy: DeserializationStrategy<I>,
    serializationStrategy: SerializationStrategy<Try<O>>,
    run: suspend (I) -> Try<O>
): RpcHandler<I, O> {
    return object : RpcHandler<I, O> {
        override val deserializationStrategy: DeserializationStrategy<I> = deserializationStrategy
        override val serializationStrategy: SerializationStrategy<Try<O>> = serializationStrategy

        override suspend fun run(request: I): Try<O> = run(request)
    }
}
