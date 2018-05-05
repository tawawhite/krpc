package com.example.krpc.gson

import com.example.krpc.core.Serializer
import com.google.gson.Gson
import kotlin.reflect.KClass

class GsonSerializer(private val gson: Gson) : Serializer {
	override fun <T> toJson(value: T): String {
		return gson.toJson(value)
	}

	override fun <T : Any> fromJson(string: String, clazz: KClass<T>): T {
		return gson.fromJson(string, clazz.java)
	}
}