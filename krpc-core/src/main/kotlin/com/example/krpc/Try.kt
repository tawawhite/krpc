package com.example.krpc

sealed class Try<out T> {
	companion object {
		inline operator fun <T> invoke(f: TryBuilder.() -> T): Try<T> {
			return try {
				Success(TryBuilder.f())
			} catch (e: FailureException) {
				e.failure
			} catch (t: Throwable) {
				Failure(mapError(t), t.message)
			}
		}

		/**
		 * Map some exceptions from [kotlin.Exception] to an [Error].
		 */
		@PublishedApi
		internal fun mapError(t: Throwable): Error = when(t) {
			is IllegalArgumentException, is UnsupportedOperationException -> Error.INVALID_ARGUMENT
			is IllegalStateException, is NullPointerException -> Error.INTERNAL
			is IndexOutOfBoundsException -> Error.OUT_OF_RANGE
			is NotImplementedError -> Error.UNIMPLEMENTED
			is AssertionError -> Error.FAILED_PRECONDITION
			is NoSuchElementException -> Error.NOT_FOUND
			else -> Error.UNKNOWN
		}
	}

	object TryBuilder {
		fun <T> Try<T>.get(): T = when (this) {
			is Success -> value
			is Failure -> throw FailureException(this)
		}

		fun raise(error: Error, message: String? = null): Nothing {
			throw FailureException(error, message)
		}
	}
}

data class Success<out T>(val value: T) : Try<T>()

data class Failure(val error: Error, val message: String? = null) : Try<Nothing>()

/** Throwing this exception in a Try {} block will return [failure] as the result. */
data class FailureException(val failure: Failure) : Exception() {
	constructor(error: Error, message: String? = null) : this(Failure(error, message))
}

// Inspired from https://github.com/grpc/grpc/blob/master/doc/statuscodes.md.
enum class Error {
	CANCELLED,
	UNKNOWN,
	INVALID_ARGUMENT,
	DEADLINE_EXCEEDED,
	NOT_FOUND,
	ALREADY_EXISTS,
	PERMISSION_DENIED,
	UNAUTHENTICATED,
	RESOURCE_EXHAUSTED,
	FAILED_PRECONDITION,
	ABORTED,
	OUT_OF_RANGE,
	UNIMPLEMENTED,
	INTERNAL,
	UNAVAILABLE,
	DATA_LOSS,
}

inline fun <A, B> Try<A>.fold(ft: (Error, String?) -> B, f: (A) -> B): Try<B> = when(this) {
	is Success -> Try { f(value) }
	is Failure -> Try { ft(error, message) }
}

inline fun <A, B> Try<A>.map(f: (A) -> B): Try<B> = when(this) {
	is Success -> Try { f(value) }
	is Failure -> this
}

inline fun <A, B> Try<A>.flatMap(f: (A) -> Try<B>): Try<B> = when(this) {
	is Success -> f(value)
	is Failure -> this
}

inline fun <A> Try<A>.recover(f: (Error, String?) -> A): Try<A> = when(this) {
	is Success -> this
	is Failure -> Try { f(error, message) }
}

inline fun <A> Try<A>.recoverWith(f: (Error, String?) -> Try<A>): Try<A> = when(this) {
	is Success -> this
	is Failure -> f(error, message)
}
