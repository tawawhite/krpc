package com.example.krpc

import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.internal.SerialClassDescImpl

sealed class Try<out T> {
    companion object {
        inline operator fun <T> invoke(
            errorMapper: ErrorMapper = DefaultErrorMapper,
            f: Builder.() -> T
        ): Try<T> {
            return try {
                Success(Builder.f())
            } catch (e: FailureException) {
                e.failure
            } catch (t: Throwable) {
                Failure(errorMapper.map(t), t.message)
            }
        }

        fun <T> serializer(element: KSerializer<T>): KSerializer<Try<T>> = TrySerializer(element)
    }

    private class TrySerializer<T>(private val element: KSerializer<T>) : KSerializer<Try<T>> {
        object TryDesc : SerialClassDescImpl("") {
            init {
                addElement("value", isOptional = true)
                addElement("error", isOptional = true)
                addElement("message", isOptional = true)
            }
        }

        override val descriptor: SerialDescriptor = TryDesc

        override fun deserialize(input: Decoder): Try<T> {
            @Suppress("NAME_SHADOWING")
            val input = input.beginStructure(descriptor, element)

            var value: Any? = null
            var error: Error? = null
            var message: String? = null

            mainLoop@ while (true) {
                when (val index = input.decodeElementIndex(descriptor)) {
                    0 -> value = input.decodeSerializableElement(descriptor, 0, element)
                    1 -> error = Error.valueOf(input.decodeStringElement(descriptor, 1))
                    2 -> message = input.decodeStringElement(descriptor, 2)
                    CompositeDecoder.READ_DONE -> break@mainLoop
                    else -> throw SerializationException("Unsupport element index: $index")
                }
            }

            input.endStructure(descriptor)
            return if (error != null) {
                Failure(error, message)
            } else {
                @Suppress("UNCHECKED_CAST")
                Success(value as T)
            }
        }

        override fun serialize(output: Encoder, obj: Try<T>) {
            @Suppress("NAME_SHADOWING")
            val output = output.beginStructure(descriptor, element)
            if (obj is Success) {
                output.encodeSerializableElement(descriptor, 0, element, obj.value)
            } else {
                @Suppress("NAME_SHADOWING")
                val obj = obj as Failure
                output.encodeStringElement(descriptor, 1, obj.error.name)
                if (obj.message != null) {
                    output.encodeStringElement(descriptor, 2, obj.message)
                }
            }
            output.endStructure(descriptor)
        }
    }

    object Builder {
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

/** Throwing a [FailureException] in a Try {} block will return [failure] as the result. */
data class FailureException(val failure: Failure) : Exception() {
    constructor(error: Error, message: String? = null) : this(Failure(error, message))
}

interface ErrorMapper {
    fun map(t: Throwable): Error
}

object DefaultErrorMapper : ErrorMapper {
    override fun map(t: Throwable): Error = when (t) {
        is IllegalArgumentException -> Error.INVALID_ARGUMENT
        is IllegalStateException, is NullPointerException -> Error.INTERNAL
        is IndexOutOfBoundsException -> Error.OUT_OF_RANGE
        is UnsupportedOperationException, is NotImplementedError -> Error.UNIMPLEMENTED
        is AssertionError -> Error.FAILED_PRECONDITION
        is NoSuchElementException -> Error.NOT_FOUND
        else -> Error.UNKNOWN
    }
}

// Some useful functions.
inline fun <A, B> Try<A>.fold(ft: (Error, String?) -> B, f: (A) -> B): Try<B> = when (this) {
    is Success -> Try { f(value) }
    is Failure -> Try { ft(error, message) }
}

inline fun <A, B> Try<A>.map(f: (A) -> B): Try<B> = when (this) {
    is Success -> Try { f(value) }
    is Failure -> this
}

inline fun <A, B> Try<A>.flatMap(f: (A) -> Try<B>): Try<B> = when (this) {
    is Success -> f(value)
    is Failure -> this
}

inline fun <A> Try<A>.recover(f: (Error, String?) -> A): Try<A> = when (this) {
    is Success -> this
    is Failure -> Try { f(error, message) }
}

inline fun <A> Try<A>.recoverWith(f: (Error, String?) -> Try<A>): Try<A> = when (this) {
    is Success -> this
    is Failure -> f(error, message)
}
