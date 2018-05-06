package com.example.krpc.processor

import com.example.krpc.HttpClient
import com.example.krpc.RpcHandler
import com.example.krpc.Serialization
import com.example.krpc.ServiceHandler
import com.google.auto.common.BasicAnnotationProcessor
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.auto.service.AutoService
import com.google.common.collect.SetMultimap
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nullable
import java.nio.file.Paths
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic
import kotlin.coroutines.experimental.Continuation

@AutoService(Processor::class)
class ServiceProcessor : BasicAnnotationProcessor() {
	companion object {
		const val outputDirOption = "krpc.outputDir"
	}

	override fun getSupportedSourceVersion(): SourceVersion {
		return SourceVersion.latestSupported()
	}

	override fun initSteps(): Iterable<ProcessingStep> {
		return listOf(ProcessingStep(processingEnv))
	}

	override fun getSupportedOptions(): Set<String> {
		return setOf(outputDirOption)
	}
}

class ProcessingStep(private val env: ProcessingEnvironment) : BasicAnnotationProcessor.ProcessingStep {
	private companion object {
		// Stub
		const val httpClientName = "httpClient"
		const val baseUrlName = "baseUrl"
		const val serializationName = "serialization"
		// Handler
		const val delegateName = "delegate"
	}

	private val generatedDirectory =
		Paths.get(env.options[ServiceProcessor.outputDirOption] ?: env.options["kapt.kotlin.generated"])

	override fun annotations(): Set<Class<out Annotation>> = setOf(Service::class.java)

	override fun process(elements: SetMultimap<Class<out Annotation>, Element>): Set<Element> {
		val annotatedInterfaces =
			ElementFilter.typesIn(elements[Service::class.java]).filter { it.kind == ElementKind.INTERFACE }
		annotatedInterfaces.forEach { theInterface ->
			// Check that there is a companion object on the interface
			val companion = ElementFilter.typesIn(theInterface.enclosedElements).find { it.simpleName.toString() == "Companion" }
			if (companion == null) {
				env.messager.printMessage(Diagnostic.Kind.ERROR, "Interface ${theInterface.simpleName} has no Companion object", theInterface)
				return@forEach
			}
			val companionType = companion.asType().asTypeName()

			// Check all methods and filter those that don't fulfil the requirements
			val allMethods = ElementFilter.methodsIn(theInterface.enclosedElements)
			val methods = allMethods.mapNotNull(this::checkMethod)
			if (methods.size != allMethods.size) {
				// Some methods failed the check
				return@forEach
			}

			// Check that we don't have two methods with the same name
			val methodsWithSameName = methods.groupBy { it.name }.filter { it.value.size > 1 }
			if (methodsWithSameName.isNotEmpty()) {
				methodsWithSameName.flatMap { it.value }.map { it.element }.forEach {
					env.messager.printMessage(Diagnostic.Kind.ERROR, "Different methods can not have the same name", it)
				}
				return@forEach
			}

			val qualifiedName = theInterface.qualifiedName
			val parts = qualifiedName.split(".")
			val packageName = parts.subList(0, parts.size - 1).joinToString(".")
			val simpleName = parts.last()
			val fileName = "${simpleName}_Krpc"

			val interfaceStub = interfaceStub(theInterface, methods)
			val interfaceHandler = interfaceHandler(theInterface, methods)
			val interfaceType = theInterface.asType().asTypeName()
			FileSpec.builder(packageName, fileName)
				.addStaticImport(HttpClient::class.java.`package`.name, "makeRpc")
				.addStaticImport(RpcHandler::class.java.`package`.name, "rpcHandler")
				.addType(interfaceStub)
				.addType(interfaceHandler)
				.addFunction(interfaceStubFactory(interfaceStub, interfaceType, companionType))
				.addFunction(interfaceHandlerFactory(interfaceHandler, interfaceType, companionType))
				.build()
				.writeTo(generatedDirectory)
		}
		return emptySet()
	}

	private fun checkMethod(method: ExecutableElement): Method? {
		// Check parameters:
		//  - First one is input
		//  - Second one is Continuation<T>, where T is the output (return) type
		if (method.parameters.size != 2) {
			env.messager.printMessage(Diagnostic.Kind.ERROR, "Each method should have exactly one parameter", method)
			return null
		}

		// Output
		val continuation = method.parameters[1].asType()
		val methodName = method.simpleName.toString()
		if (!MoreTypes.isTypeOf(Continuation::class.java, continuation)) {
			env.messager.printMessage(Diagnostic.Kind.ERROR, "$methodName should be a suspending function", method)
			return null
		}

		val outputType = MoreTypes.asWildcard(MoreTypes.asDeclared(continuation).typeArguments[0]).superBound
		if (!checkType(outputType)) {
			env.messager.printMessage(
				Diagnostic.Kind.ERROR,
				"$methodName return type should be a non-generic type annotated with @kotlinx.serialization.Serializable",
				method
			)
			return null
		}

		// Input
		val input = method.parameters[0]
		val inputName = input.simpleName.toString()
		if (MoreElements.isAnnotationPresent(input, Nullable::class.java)) {
			env.messager.printMessage(Diagnostic.Kind.ERROR, "$inputName can not be null", input)
			return null
		}

		val inputType = input.asType()
		if (!checkType(inputType)) {
			env.messager.printMessage(
				Diagnostic.Kind.ERROR,
				"$inputName should be a non-generic type annotated with @kotlinx.serialization.Serializable",
				input
			)
			return null
		}

		return Method(method, inputName, inputType, outputType)
	}

	private fun interfaceStub(
		theInterface: TypeElement,
		methods: List<Method>
	): TypeSpec {
		val className = "${theInterface.simpleName}_Stub"
		return TypeSpec.classBuilder(className)
			.addModifiers(KModifier.PRIVATE)
			.addSuperinterface(theInterface.asClassName())
			.primaryConstructor(
				FunSpec.constructorBuilder()
					.addParameter(httpClientName, HttpClient::class)
					.addParameter(baseUrlName, String::class)
					.addParameter(serializationName, Serialization::class.java)
					.build()
			)
			.addProperty(
				PropertySpec.builder(httpClientName, HttpClient::class, KModifier.PRIVATE)
					.initializer(httpClientName)
					.build()
			)
			.addProperty(
				PropertySpec.builder(baseUrlName, String::class, KModifier.PRIVATE)
					.initializer(baseUrlName)
					.build()
			)
			.addProperty(
				PropertySpec.builder(serializationName, Serialization::class, KModifier.PRIVATE)
					.initializer(serializationName)
					.build()
			)
			.addFunctions(methods.map { methodImplementation(it, theInterface.simpleName.toString()) })
			.build()
	}

	private fun methodImplementation(method: Method, interfaceName: String): FunSpec {
		val methodName = method.name
		val inputType = method.inputType
		val outputType = method.outputType

		val parameterSpec = ParameterSpec.builder(method.inputName, method.inputType.asTypeName()).build()
		val nameAllocator = NameAllocator()
		nameAllocator.newName(parameterSpec.name, parameterSpec)
		val urlName = nameAllocator.newName("url")
		return FunSpec.builder(methodName)
			.addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
			.addParameter(parameterSpec)
			.returns(outputType.asTypeName())
			.addStatement("val %N = this.%N + %S", urlName, baseUrlName, "/$interfaceName/$methodName")
			.addStatement(
				"return makeRpc(this.%N, %N, this.%N, %N, %T.serializer(), %T.serializer())",
				httpClientName,
				urlName,
				serializationName,
				parameterSpec,
				inputType,
				outputType
			).build()
	}

	private class Method(
		val element: ExecutableElement,
		val inputName: String,
		val inputType: TypeMirror,
		val outputType: TypeMirror
	) {

		val name = element.simpleName.toString()

	}
	private fun checkType(type: TypeMirror): Boolean {
		listOf<String>().map { }
		return type.kind == TypeKind.DECLARED
			&& MoreTypes.asDeclared(type).typeArguments.isEmpty()
			&& MoreElements.isAnnotationPresent(MoreTypes.asElement(type), Serializable::class.java)
	}
	private fun interfaceHandler(
		theInterface: TypeElement,
		methods: List<Method>
	): TypeSpec {
		val className = "${theInterface.simpleName}_Handler"

		return TypeSpec.classBuilder(className)
			.addModifiers(KModifier.PRIVATE)
			.addSuperinterface(ServiceHandler::class.java)
			.primaryConstructor(
				FunSpec.constructorBuilder()
					.addParameter(delegateName, theInterface.asClassName())
					.build()
			)
			.addProperty(
				PropertySpec.builder(ServiceHandler::serviceName.name, String::class, KModifier.OVERRIDE)
					.initializer("%S", theInterface.simpleName.toString())
					.build()
			)
			.addProperty(
				PropertySpec.builder(delegateName, theInterface.asClassName(), KModifier.PRIVATE)
					.initializer(delegateName)
					.build()
			)
			.addProperties(methods.map { methodHandler(it) })
			.addFunction(rpcHandlerImplementation(methods))
			.build()
	}

	private fun methodHandler(method: Method): PropertySpec {
		val methodName = method.name
		val returnType: TypeName = ParameterizedTypeName.get(
			RpcHandler::class.asTypeName(),
			method.inputType.asTypeName(),
			method.outputType.asTypeName()
		)
		return PropertySpec.builder("_$methodName", returnType, KModifier.PRIVATE)
			.initializer("rpcHandler(%T.serializer(), %T.serializer(), { %N.%N(it) })", method.inputType, method.outputType, delegateName, methodName)
			.build()
	}

	private fun rpcHandlerImplementation(methods: List<Method>): FunSpec {
		val rpcName = "rpc"

		val builder = FunSpec.builder(ServiceHandler::rpcHandler.name)
			.addModifiers(KModifier.OVERRIDE)
			.addParameter(rpcName, String::class)
			.beginControlFlow("return when (%N)", rpcName)
		methods.forEach {
			val methodName = it.name
			builder.addStatement("%S -> _%N", methodName, methodName)
		}
		return builder
			.addStatement("else -> null")
			.endControlFlow()
			.build()
	}

	private fun interfaceStubFactory(interfaceStub: TypeSpec, interfaceType: TypeName, companionType: TypeName): FunSpec {
		return FunSpec.builder("create")
			.receiver(companionType)
			.returns(interfaceType)
			.addParameter(httpClientName, HttpClient::class)
			.addParameter(baseUrlName, String::class)
			.addParameter(
				ParameterSpec.builder(serializationName, Serialization::class.java)
					.defaultValue("%T.%L", Serialization::class.java, Serialization.JSON)
					.build()
			)
			.addStatement("return %N(%N, %N, %N)", interfaceStub, httpClientName, baseUrlName, serializationName)
			.build()
	}

	private fun interfaceHandlerFactory(interfaceHandler: TypeSpec, interfaceType: TypeName, companionType: TypeName): FunSpec {
		val delegateName = "delegate"
		return FunSpec.builder("handler")
			.receiver(companionType)
			.returns(ServiceHandler::class.java)
			.addParameter(delegateName, interfaceType)
			.addStatement("return %N(%N)", interfaceHandler, delegateName)
			.build()
	}
}
