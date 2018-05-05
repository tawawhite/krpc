package com.example.krpc.example

import com.example.krpc.processor.GenerateClient
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult

data class User(val firstName: String)

@RestController
@RequestMapping("/hello")
@GenerateClient("HelloClient")
class HelloController {

	@RequestMapping("/{pathVariable}", method = [RequestMethod.POST])
	fun echo(
		@RequestBody body: String,
		@RequestParam requestParam: Int?,
		@RequestHeader(required = false) header: String,
		@PathVariable pathVariable: Long
	): String {
		return """I received:
			|body: $body
			|requestParam: $requestParam
			|header: $header
			|pathVariable: $pathVariable
		""".trimMargin()
	}

	@RequestMapping(method = [RequestMethod.GET])
	fun getAllusers(): List<User?> = listOf()

	@PutMapping
	fun saveUser(@RequestBody user: User): DeferredResult<Unit> = DeferredResult()
}

fun main(args: Array<String>) {
	val funSpec = FunSpec.builder("myFunction")
	val parameter = ParameterSpec.builder("body", Int::class).build()
	funSpec.addParameter(parameter)
	val nameAllocator = NameAllocator()
	nameAllocator.newName(parameter.name, parameter)
	nameAllocator.newName("body", "body")

	funSpec.addStatement("val %L = %L", nameAllocator.get("body"), 3)
	funSpec.addStatement("println(%N)", nameAllocator.get(parameter))
	FileSpec.builder("com.example", "MyFile")
		.addFunction(funSpec.build())
		.build().writeTo(System.out)
}