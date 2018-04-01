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

	@RequestMapping(method = [RequestMethod.POST])
	fun echo(@RequestBody theBody: String,
			 @RequestParam aRequestParam: Int,
			 @RequestHeader aHeader: String): String {
		return """I received:
			|theBody: $theBody
			|aRequestParam: $aRequestParam
			|aHeader: $aHeader
		""".trimMargin()
	}

	@RequestMapping("/{userId}", method = [RequestMethod.PUT])
	fun saveUser(@RequestBody user: User, @PathVariable userId: Int) {
		println("Saving user $user with id $userId")
	}

}

class User(var firstName: String? = null,
		   var lastName: String? = null) {
	override fun toString(): String {
		return "User(firstName=$firstName, lastName=$lastName)"
	}
}