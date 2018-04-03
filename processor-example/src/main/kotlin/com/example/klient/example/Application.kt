package com.example.klient.example

import com.example.klient.processor.GenerateClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hello")
@GenerateClient("HelloClient")
class HelloController {

	@RequestMapping("/{pathVariable}", method = [RequestMethod.POST])
	fun echo(@RequestBody body: String,
			 @RequestParam requestParam: Int,
			 @RequestHeader header: String,
			 @PathVariable pathVariable: Long): String {
		return """I received:
			|body: $body
			|requestParam: $requestParam
			|header: $header
			|pathVariable: $pathVariable
		""".trimMargin()
	}

	@RequestMapping(method = [RequestMethod.GET])
	fun sayHello(@RequestBody body: String) = "Hello $body"

}

fun main(args: Array<String>) {
}