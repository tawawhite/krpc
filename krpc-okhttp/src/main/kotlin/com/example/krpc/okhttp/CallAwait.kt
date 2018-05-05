package com.example.krpc.okhttp

import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

// Taken from https://github.com/gildor/kotlin-coroutines-okhttp
suspend fun Call.await(): Response {
	return suspendCancellableCoroutine { continuation ->
		enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				continuation.resume(response)
			}

			override fun onFailure(call: Call, e: IOException) {
				// Don't bother with resuming the continuation if it is already cancelled.
				if (continuation.isCancelled) return
				continuation.resumeWithException(e)
			}
		})

		continuation.invokeOnCompletion {
			if (continuation.isCancelled)
				try {
					cancel()
				} catch (ex: Throwable) {
					//Ignore cancel exception
				}
		}
	}
}