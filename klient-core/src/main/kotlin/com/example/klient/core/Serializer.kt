package com.example.klient.core

interface Serializer {

	fun <T> toJson(value: T): String

	fun <T> fromJson(string: String, clazz: Class<T>): T

}