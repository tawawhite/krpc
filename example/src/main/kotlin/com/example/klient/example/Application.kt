package com.example.klient.example

import com.example.klient.gson.GsonSerializer
import com.example.klient.okhttp.OkHttpClient
import com.google.gson.Gson
import kotlinx.coroutines.experimental.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
	val helloClient = HelloClient(OkHttpClient(okhttp3.OkHttpClient()), GsonSerializer(Gson()), "http://127.0.0.1:8080")
	runBlocking {
		helloClient.saveUser(User("John", "Doe"), 8)
		println("Echo result : ${helloClient.echo("Hello World", 42, "MyHeader")}")
	}
}