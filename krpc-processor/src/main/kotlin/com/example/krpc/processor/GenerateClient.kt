package com.example.krpc.processor

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateClient(
	val className: String = "",
	val packageName: String = ""
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class IgnoreMapping