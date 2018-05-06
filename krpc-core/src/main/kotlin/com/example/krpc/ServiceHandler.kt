package com.example.krpc

import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver

interface ServiceHandler {
	val serviceName: String

	fun rpcHandler(rpc: String): RpcHandler<*, *>?
}

interface RpcHandler<I : Any, O : Any> {
	val requestLoader: KSerialLoader<I>

	val responseSaver: KSerialSaver<O>

	suspend fun run(request: I): O
}

fun <I : Any, O : Any> rpcHandler(
	requestLoader: KSerialLoader<I>,
	responseSaver: KSerialSaver<O>,
	run: suspend (I) -> O
): RpcHandler<I, O> {
	return object : RpcHandler<I, O> {
		override val requestLoader: KSerialLoader<I> = requestLoader
		override val responseSaver: KSerialSaver<O> = responseSaver

		override suspend fun run(request: I): O = run(request)
	}
}