package com.example.krpc

interface Server {
	/**
	 * Starts this server.
	 *
	 * @param wait if true, this function does not exit until server stops and exits.
	 */
	fun start(wait: Boolean = false)

	/**
	 * Stops this server.
	 *
	 * @param gracePeriod the maximum amount of time in milliseconds to allow for activity to cool down.
	 * @param timeout the maximum amount of time in milliseconds to wait until server stops gracefully
	 */
	fun stop(gracePeriod: Long, timeout: Long)

	/**
	 * Bind a [ServiceHandler] to this server.
	 *
	 * @throws [kotlin.IllegalArgumentException] if there is already a [ServiceHandler] with the same serviceName bound to the server.
	 */
	fun bindService(serviceHandler: ServiceHandler)
}