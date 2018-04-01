package com.example.klient.gson

import com.example.klient.core.Serializer
import com.google.gson.Gson

class GsonSerializer(private val gson: Gson) : Serializer {
	override fun <T> toJson(value: T): String {
		return gson.toJson(value)
	}

	override fun <T> fromJson(string: String, clazz: Class<T>): T {
		return gson.fromJson(string, clazz)
	}
}