package com.example.klient.core

import kotlin.reflect.KClass

interface Serializer {

	fun <T> toJson(value: T): String

	fun <T : Any> fromJson(string: String, clazz: KClass<T>): T

}

inline fun <reified T : Any> Serializer.fromJson(string: String) = fromJson(string, T::class)