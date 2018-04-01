package com.example.klient.example

import com.example.klient.processor.GenerateClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class Application

@RestController
@RequestMapping("/hello")
@GenerateClient("HelloClient")
class HelloController {

	@RequestMapping("/{aPathVariable}", method = [RequestMethod.POST])
	fun echo(@RequestBody theBody: String,
			 @RequestParam aRequestParam: Int,
			 @RequestHeader aHeader: String,
			 @PathVariable aPathVariable: Long): String {
		return """I received:
			|theBody: $theBody
			|aRequestParam: $aRequestParam
			|aHeader: $aHeader
			|aPathVariable: $aPathVariable
		""".trimMargin()
	}

	@RequestMapping("/{userId}", method = [RequestMethod.PUT])
	fun saveUser(@RequestBody user: User, @PathVariable userId: Int) {
		println("Saving user $user with id $userId")
	}

}

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}