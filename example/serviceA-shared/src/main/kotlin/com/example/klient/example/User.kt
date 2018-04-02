package com.example.klient.example

class User(var firstName: String? = null,
		   var lastName: String? = null) {
	override fun toString(): String {
		return "User(firstName=$firstName, lastName=$lastName)"
	}
}