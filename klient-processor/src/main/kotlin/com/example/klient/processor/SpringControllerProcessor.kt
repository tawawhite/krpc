package com.example.klient.processor

import com.example.klient.core.HttpCallException
import com.example.klient.core.HttpClient
import com.example.klient.core.HttpMethod
import com.example.klient.core.HttpRequest
import com.example.klient.core.HttpRequestBody
import com.example.klient.core.Serializer
import com.google.auto.common.BasicAnnotationProcessor
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.auto.service.AutoService
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.SetMultimap
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.annotations.NotNull
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Paths
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic
import kotlin.reflect.jvm.internal.impl.name.FqName
import kotlin.reflect.jvm.internal.impl.platform.JavaToKotlinClassMap

@AutoService(Processor::class)
class SpringControllerProcessor : BasicAnnotationProcessor() {
	companion object {
		const val outputDirOption = "klient.outputDir"
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

// TODO : Support GetMapping, etc
// TODO : Check invalid position of GenerateClient annotation
// TODO : Handle flux, Deferred, etc
// TODO : Factory, autoconfiguration
class ProcessingStep(private val env: ProcessingEnvironment) : BasicAnnotationProcessor.ProcessingStep {
	companion object {
		private val httpClient = VariableTag("httpClient")
		private val serializer = VariableTag("serializer")
		private val baseUrl = VariableTag("baseUrl")
	}

	// No data class because we need equals with === semantic
	private class VariableTag(val name: String)

	private val generatedDirectory = Paths.get(env.options[SpringControllerProcessor.outputDirOption]
		?: env.options["kapt.kotlin.generated"])
	private val functionsByClass = LinkedListMultimap.create<String, FunSpec>()
	private val nameAllocatorByClass = hashMapOf<String, NameAllocator>()

	override fun annotations(): Set<Class<out Annotation>> {
		return setOf(GenerateClient::class.java)
	}

	override fun process(elements: SetMultimap<Class<out Annotation>, Element>): Set<Element> {
		env.messager.printMessage(Diagnostic.Kind.NOTE, "Klient: sources will be generated in $generatedDirectory")

		val annotatedClasses = ElementFilter.typesIn(elements[GenerateClient::class.java])
		val annotatedMethods = ElementFilter.methodsIn(elements[GenerateClient::class.java])
		val methodsToConsider = annotatedMethods + annotatedClasses.flatMap { ElementFilter.methodsIn(it.enclosedElements) }
		methodsToConsider.toSet().forEach { method ->
			if (shouldIncludeMethod(method)) {
				val className = generatedClassName(method)
				val nameAllocator = nameAllocatorByClass.getOrPut(className) {
					NameAllocator().apply {
						newName(httpClient.name, httpClient)
						newName(serializer.name, serializer)
						newName(baseUrl.name, baseUrl)
					}
				}
				val httpMethods = getHttpMethods(method)
				if (httpMethods.size == 1) {
					functionsByClass.put(className, getMainFunction(method, httpMethods[0], nameAllocator.clone()))
				} else {
					httpMethods.forEach { functionsByClass.put(className, getForwardedFunction(method, it)) }
					functionsByClass.put(className, getMainFunction(method, null, nameAllocator.clone()))
				}
			}
		}

		functionsByClass.asMap().forEach { qualifiedName, functions ->
			val nameAllocator = nameAllocatorByClass[qualifiedName]!!
			val parts = qualifiedName.split(".")
			val packageName = parts.subList(0, parts.size - 1).joinToString(".")
			val className = parts.last()
			val httpClientName = nameAllocator.get(httpClient)
			val serializerName = nameAllocator.get(serializer)
			val baseUrlName = nameAllocator.get(baseUrl)
			val typeSpec = TypeSpec.classBuilder(className)
				.primaryConstructor(FunSpec.constructorBuilder()
					.addParameter(httpClientName, HttpClient::class)
					.addParameter(serializerName, Serializer::class)
					.addParameter(baseUrlName, String::class)
					.build())
				.addProperty(PropertySpec.builder(httpClientName, HttpClient::class, KModifier.PRIVATE)
					.initializer(httpClientName)
					.build())
				.addProperty(PropertySpec.builder(serializerName, Serializer::class, KModifier.PRIVATE)
					.initializer(serializerName)
					.build())
				.addProperty(PropertySpec.builder(baseUrlName, String::class, KModifier.PRIVATE)
					.initializer(baseUrlName)
					.build())
				.addFunctions(functions).build()
			FileSpec.builder(packageName, className)
				.addType(typeSpec)
				.build()
				.writeTo(generatedDirectory)
		}
		return emptySet()
	}

	private fun shouldIncludeMethod(method: ExecutableElement): Boolean {
		return !MoreElements.isAnnotationPresent(method, IgnoreMapping::class.java)
			&& MoreElements.isAnnotationPresent(method, RequestMapping::class.java)
			&& (MoreElements.isAnnotationPresent(method, ResponseBody::class.java)
			|| MoreElements.isAnnotationPresent(method.enclosingElement, ResponseBody::class.java)
			|| MoreElements.isAnnotationPresent(method.enclosingElement, RestController::class.java))
	}

	private fun generatedClassName(method: ExecutableElement): String {
		val parentElement = MoreElements.asType(method.enclosingElement)
		val parts = parentElement.qualifiedName.split(".")
		val defaultPackageName = parts.subList(0, parts.lastIndex).joinToString(".")
		val defaultClassName = parentElement.simpleName.toString() + "_Client"
		if (MoreElements.isAnnotationPresent(method, GenerateClient::class.java)) {
			val annotation = method.getAnnotation(GenerateClient::class.java)
			if (annotation.className != "" || annotation.packageName != "") {
				val className = if (annotation.className != "") annotation.className else defaultClassName
				val packageName = if (annotation.packageName != "") annotation.packageName else defaultPackageName
				return "$packageName.$className"
			}
		}
		if (MoreElements.isAnnotationPresent(parentElement, GenerateClient::class.java)) {
			val annotation = parentElement.getAnnotation(GenerateClient::class.java)
			if (annotation.className != "" || annotation.packageName != "") {
				val className = if (annotation.className != "") annotation.className else defaultClassName
				val packageName = if (annotation.packageName != "") annotation.packageName else defaultPackageName
				return "$packageName.$className"
			}
		}
		return "$defaultPackageName.$defaultClassName"
	}

	private fun getForwardedFunction(method: ExecutableElement, httpMethod: HttpMethod): FunSpec {
		val builder = FunSpec.builder(method.simpleName.toString() + "_${httpMethod.name.toLowerCase()}")
			.addModifiers(KModifier.SUSPEND)
		// We don't need to be careful about overriding properties name given that we just forward the arguments
		addParametersAndReturnType(builder, method, NameAllocator())
		val parameters = method.parameters.joinToString(", ") { it.simpleName.toString() }
		if (parameters.isEmpty()) {
			builder.addStatement("return %N(%T.%L)", method.simpleName.toString(), HttpMethod::class, httpMethod)
		} else {
			builder.addStatement("return %N(%T.%L, %N)", method.simpleName.toString(), HttpMethod::class, httpMethod, parameters)
		}
		return builder.build()
	}

	private fun getMainFunction(method: ExecutableElement, httpMethod: HttpMethod?, nameAllocator: NameAllocator): FunSpec {
		val builder = FunSpec.builder(method.simpleName.toString()).addModifiers(KModifier.SUSPEND)
		with(builder) {
			// Allocated names
			val baseUrlName = nameAllocator.get(baseUrl)
			val httpClientName = nameAllocator.get(httpClient)
			val serializerName = nameAllocator.get(serializer)

			// Parameters & return type
			val httpMethodName = VariableTag("httpMethod").run { nameAllocator.newName(this.name, this) }
			if (httpMethod == null) {
				addModifiers(KModifier.PRIVATE)
				addParameter(ParameterSpec.builder(httpMethodName, HttpMethod::class).build())
			}
			addParametersAndReturnType(this, method, nameAllocator)

			// Local variables
			val bodyContentName = VariableTag("bodyContent").run { nameAllocator.newName(this.name, this) }
			val bodyName = VariableTag("body").run { nameAllocator.newName(this.name, this) }
			val headersName = VariableTag("headers").run { nameAllocator.newName(this.name, this) }
			val paramsName = VariableTag("params").run { nameAllocator.newName(this.name, this) }
			val pathVariablesName = VariableTag("pathVariables").run { nameAllocator.newName(this.name, this) }
			val urlName = VariableTag("url").run { nameAllocator.newName(this.name, this) }
			val httpRequestName = VariableTag("httpRequest").run { nameAllocator.newName(this.name, this) }
			val httpResponseName = VariableTag("httpResponse").run { nameAllocator.newName(this.name, this) }

			// Body
			val body = method.parameters.firstOrNull { MoreElements.isAnnotationPresent(it, RequestBody::class.java) }
			if (body == null) {
				addStatement("val %N = null", bodyName)
			} else {
				if (MoreTypes.isTypeOf(String::class.java, body.asType())) {
					addStatement("val %N = %N", bodyContentName, nameAllocator.get(body))
				} else {
					addStatement("val %N = %N.toJson(%N)", bodyContentName, serializer, nameAllocator.get(body))
				}
				addStatement("val %N = %T(%N, %S)", bodyName, HttpRequestBody::class, bodyContentName, "application/json")
			}

			// Headers
			val headers = method.parameters.filter { MoreElements.isAnnotationPresent(it, RequestHeader::class.java) }
			if (headers.isEmpty()) {
				addStatement("val %N = emptyMap<%T, %T>()", headersName, String::class, String::class)
			} else {
				createStringMap(builder, headersName, headers, RequestHeader::class.java, nameAllocator)
			}

			// Request Params
			val params = method.parameters.filter { MoreElements.isAnnotationPresent(it, RequestParam::class.java) }
			if (params.isNotEmpty()) {
				createStringMap(builder, paramsName, params, RequestParam::class.java, nameAllocator)
			}

			// Path variables
			val pathVariables = method.parameters.filter { MoreElements.isAnnotationPresent(it, PathVariable::class.java) }
			if (pathVariables.isNotEmpty()) {
				createStringMap(builder, pathVariablesName, pathVariables, PathVariable::class.java, nameAllocator)
			}

			// URL
			val url = getUrl(method)
			if (params.isEmpty() && pathVariables.isEmpty()) {
				if (url != "") {
					addStatement("val %N = %N + %S", urlName, baseUrlName, url)
				} else {
					addStatement("val %N = %N", urlName, baseUrlName)
				}
			} else {
				if (url != "") {
					addStatement("var %N = %N + %S", urlName, baseUrlName, url)
				} else {
					addStatement("var %N = %N", urlName, baseUrlName)
				}
			}

			if (pathVariables.isNotEmpty()) {
				addStatement("%N = %N.toList().fold(%N, { current, (variable, value) -> current.replace(\"{\$variable}\", value) } )", urlName, pathVariablesName, urlName)
			}

			mapOf<String, String>().toList().joinToString("") { "${it.first}=${it.second}" }
			if (params.isNotEmpty()) {
				addStatement("%N += %S + %N.toList().joinToString(%S) { %S }", urlName, "?", paramsName, "&", "\${it.first}=\${it.second}")
			}

			// Method
			if (httpMethod != null) {
				addStatement("val %N = %T.%L", httpMethodName, HttpMethod::class, httpMethod)
			}

			// HttpRequest
			addStatement("val %N = %T(%N, %N, %N, %N)", httpRequestName, HttpRequest::class, urlName, httpMethodName, bodyName, headersName)

			// HttpResponse
			addStatement("val %N = %N.send(%N)", httpResponseName, httpClientName, httpRequestName)
			addStatement("if (!%N.status.successful) {", httpResponseName)
			addStatement("%>throw %T(%N)", HttpCallException::class, httpResponseName)
			addStatement("%<}")

			val returnType = method.returnType.asTypeName().javaToKotlinType()
			if (returnType != UNIT) {
				if (isReturnTypeNotNull(method)) {
					if (MoreTypes.isTypeOf(String::class.java, method.returnType)) {
						addStatement("return %N.body!!.content", httpResponseName)
					} else {
						addStatement("return %N.fromJson(%N.body!!.content, %T::class.java)", serializerName, httpResponseName, returnType)
					}
				} else {
					if (MoreTypes.isTypeOf(String::class.java, method.returnType)) {
						addStatement("return %N.body?.content", httpResponseName)
					} else {
						addStatement("return %N.body?.content?.let { %N.fromJson(it, %T::class.java) }", httpResponseName, serializerName, returnType)
					}
				}
			}
		}
		return builder.build()
	}

	private fun addParametersAndReturnType(builder: FunSpec.Builder, method: ExecutableElement, nameAllocator: NameAllocator) {
		builder.addParameters(method.parameters.mapNotNull { element ->
			if (shouldIncludeParam(element)) {
				nameAllocator.newName(element.simpleName.toString(), element)
				val name = nameAllocator.get(element)
				val baseType = element.asType().asTypeName().javaToKotlinType()
				val type = if (!isNotNull(element)) baseType.asNullable() else baseType
				ParameterSpec.builder(name, type)
					.jvmModifiers(element.modifiers)
					.build()
			} else {
				null
			}
		})

		val baseReturnType = method.returnType.asTypeName().javaToKotlinType()
		val returnType = if (!isReturnTypeNotNull(method)) baseReturnType.asNullable() else baseReturnType
		builder.returns(returnType)
	}

	private fun shouldIncludeParam(element: VariableElement): Boolean {
		return MoreElements.isAnnotationPresent(element, RequestBody::class.java)
			|| MoreElements.isAnnotationPresent(element, RequestHeader::class.java)
			|| MoreElements.isAnnotationPresent(element, RequestParam::class.java)
			|| MoreElements.isAnnotationPresent(element, PathVariable::class.java)
	}

	private fun isNotNull(element: VariableElement): Boolean {
		val isPrimitive = element.asType().kind.isPrimitive
		return isPrimitive || MoreElements.isAnnotationPresent(element, NotNull::class.java)
	}

	private fun isReturnTypeNotNull(method: ExecutableElement): Boolean {
		val isPrimitive = method.returnType.kind.isPrimitive
		return isPrimitive || MoreElements.isAnnotationPresent(method, NotNull::class.java)
	}

	private fun getUrl(method: ExecutableElement): String {
		val annotation = MoreElements.getAnnotationMirror(method, RequestMapping::class.java).get()
		return if (MoreElements.isAnnotationPresent(method.enclosingElement, RequestMapping::class.java)) {
			val parentAnnotation = MoreElements.getAnnotationMirror(method.enclosingElement, RequestMapping::class.java).get()
			getRequestMappingPath(parentAnnotation) + getRequestMappingPath(annotation)
		} else {
			getRequestMappingPath(annotation)
		}
	}

	private fun getRequestMappingPath(annotation: AnnotationMirror): String {
		val values = annotation.elementValues
		values.entries.forEach { (key, value) ->
			val simpleName = key.simpleName.toString()
			if (simpleName == "value" || simpleName == "path") {
				@Suppress("UNCHECKED_CAST")
				val paths = value.value as List<AnnotationValue>
				return if (paths.isNotEmpty()) {
					paths[0].value as String
				} else {
					""
				}
			}
		}
		return ""
	}

	private fun getHttpMethods(method: ExecutableElement): List<HttpMethod> {
		val methods = getRequestMappingMethod(MoreElements.getAnnotationMirror(method, RequestMapping::class.java).get()) +
			MoreElements.getAnnotationMirror(method.enclosingElement, RequestMapping::class.java).transform { getRequestMappingMethod(it!!) }.or(emptyList())
		return if (methods.isEmpty()) {
			HttpMethod.values().toList()
		} else {
			methods
		}
	}

	private fun getRequestMappingMethod(annotation: AnnotationMirror): List<HttpMethod> {
		val values = annotation.elementValues
		values.entries.forEach { (key, value) ->
			if (key.simpleName.toString() == "method") {
				@Suppress("UNCHECKED_CAST")
				val methods = value.value as List<AnnotationValue>
				return methods.map { HttpMethod.valueOf((it.value as VariableElement).simpleName.toString()) }
			}
		}
		return emptyList()
	}

	private fun createStringMap(builder: FunSpec.Builder, mapName: String, elements: List<VariableElement>, annotation: Class<out Annotation>, nameAllocator: NameAllocator) {
		val hasNullable = elements.any { !isNotNull(it) }
		if (hasNullable) {
			builder.addStatement("val %N = listOf<%T<%T, %T?>>(", mapName, Pair::class, String::class, String::class)
		} else {
			builder.addStatement("val %N = mapOf<%T, %T>(", mapName, String::class, String::class)
		}
		elements.forEachIndexed { index, variable ->
			val prefix = if (index == 0) "%>%>" else ""
			val suffix = if (index != elements.lastIndex) "," else ""
			val paramName = getValueOrName(MoreElements.getAnnotationMirror(variable, annotation).get(), variable)
			val variableName = nameAllocator.get(variable)
			if (MoreTypes.isTypeOf(String::class.java, variable.asType())) {
				builder.addStatement("$prefix%S to %N$suffix", paramName, variableName)
			} else if (isNotNull(variable)) {
				builder.addStatement("$prefix%S to %N.toJson(%N)$suffix", paramName, nameAllocator.get(serializer), variableName)
			} else {
				builder.addStatement("$prefix%S to %N.let { %N.toJson(it) }$suffix", paramName, variableName, nameAllocator.get(serializer))
			}
		}

		if (hasNullable) {
			builder.addStatement("%<%<).mapNotNull { (key, value) -> if (value != null) Pair(key, value) else null }.toMap()")
		} else {
			builder.addStatement("%<%<)")
		}
	}

	private fun getValueOrName(annotation: AnnotationMirror, element: Element): String {
		val values = annotation.elementValues
		values.entries.forEach { (key, value) ->
			val simpleName = key.simpleName.toString()
			if (simpleName == "value" || simpleName == "name") {
				return value.value as String
			}
		}
		return element.simpleName.toString()
	}

	// From https://github.com/square/kotlinpoet/issues/236#issuecomment-377784099
	private fun TypeName.javaToKotlinType(): TypeName {
		return if (this is ParameterizedTypeName) {
			ParameterizedTypeName.get(
				rawType.javaToKotlinType() as ClassName,
				*typeArguments.map { it.javaToKotlinType() }.toTypedArray()
			)
		} else {
			val className =
				JavaToKotlinClassMap.INSTANCE.mapJavaToKotlin(FqName(toString()))
					?.asSingleFqName()?.asString()

			return if (className == null) {
				this
			} else {
				ClassName.bestGuess(className)
			}
		}
	}

}
