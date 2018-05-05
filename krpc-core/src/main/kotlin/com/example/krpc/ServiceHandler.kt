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
