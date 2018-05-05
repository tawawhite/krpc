package com.example.krpc.example

import com.example.krpc.gson.GsonSerializer
import com.example.krpc.okhttp.OkHttpClient
import com.google.gson.Gson
import kotlinx.coroutines.experimental.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class Application {
	@Bean
	fun start() = CommandLineRunner {
		val httpClient = OkHttpClient(okhttp3.OkHttpClient())
		val serializer = GsonSerializer(Gson())
		val helloClient = HelloClient(httpClient, serializer, "http://127.0.0.1:8080")
		runBlocking {
			println("Received response from serviceA:")
			println(helloClient.echo("This is the body", 42, "Header value here", 12345))
		}
	}
}

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}