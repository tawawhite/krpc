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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
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
// TODO : Handle nullability (in return type too) and required = false in annotations
// TODO : Restriction of methods instead of taking first and multiple methods doing the same thing if multiple methods
// TODO : Check invalid position of GenerateClient annotation
// TODO : Handle nested method paths
// TODO : Handle flux, Deferred, etc
class ProcessingStep(private val env: ProcessingEnvironment) : BasicAnnotationProcessor.ProcessingStep {
	companion object {
		const val httpClientName = "httpClient"
		const val serializerName = "serializer"
		const val baseUrlName = "baseUrl"
	}

	private val functionsByClass = LinkedListMultimap.create<String, FunSpec>()

	override fun annotations(): Set<Class<out Annotation>> {
		return setOf(GenerateClient::class.java)
	}

	override fun process(elements: SetMultimap<Class<out Annotation>, Element>): Set<Element> {
		if (!env.options.containsKey(SpringControllerProcessor.outputDirOption)) {
			env.messager.printMessage(Diagnostic.Kind.ERROR, "Klient : you have to specify the output directory with the following processor option : ${SpringControllerProcessor.outputDirOption}")
			return emptySet()
		}
		val generatedDirectory = Paths.get(env.options[SpringControllerProcessor.outputDirOption])

		val annotatedClasses = ElementFilter.typesIn(elements[GenerateClient::class.java])
		val annotatedMethods = ElementFilter.methodsIn(elements[GenerateClient::class.java])
		val methodsToConsider = annotatedMethods + annotatedClasses.flatMap { ElementFilter.methodsIn(it.enclosedElements) }
		methodsToConsider.toSet().forEach { method ->
			if (shouldIncludeMethod(method)) {
				val className = generatedClassName(method)
				functionsByClass.put(className, getFunction(method))
			}
		}

		functionsByClass.asMap().forEach { qualifiedName, functions ->
			val parts = qualifiedName.split(".")
			val packageName = parts.subList(0, parts.size - 1).joinToString(".")
			val className = parts.last()
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

	private fun getFunction(method: ExecutableElement): FunSpec {
		val builder = FunSpec.builder(method.simpleName.toString()).addModifiers(KModifier.SUSPEND)
		with(builder) {
			// Parameters
			addParameters(method.parameters.mapNotNull { element ->
				if (shouldIncludeParam(element)) {
					val name = element.simpleName.toString()
					val type = element.asType().asTypeName().javaToKotlinType()
					ParameterSpec.builder(name, type)
							.jvmModifiers(element.modifiers)
							.build()
				} else {
					null
				}
			})

			// Return type
			val returnType = method.returnType.asTypeName().javaToKotlinType()
			returns(returnType)

			// Local variables
			val bodyContentName = "bodyContent"
			val bodyName = "body"
			val mediaType = "application/json"
			val headersName = "headers"
			val paramsName = "params"
			val pathVariablesName = "pathVariables"
			val urlName = "url"
			val httpMethodName = "method"
			val httpRequestName = "httpRequest"
			val httpResponseName = "httpResponse"

			// Body
			val body = method.parameters.firstOrNull { MoreElements.isAnnotationPresent(it, RequestBody::class.java) }
			if (body == null) {
				addStatement("val %N = null", bodyName)
			} else {
				if (MoreTypes.isTypeOf(String::class.java, body.asType())) {
					addStatement("val %N = %N", bodyContentName, body.simpleName)
				} else {
					addStatement("val %N = %N.toJson(%N)", bodyContentName, serializerName, body.simpleName)
				}
				addStatement("val %N = %T(%N, %S)", bodyName, HttpRequestBody::class, bodyContentName, mediaType)
			}

			// Headers
			val headers = method.parameters.filter { MoreElements.isAnnotationPresent(it, RequestHeader::class.java) }
			if (headers.isEmpty()) {
				addStatement("val %N = emptyMap<%T, %T>()", headersName, String::class, String::class)
			} else {
				createStringMap(builder, headersName, headers, RequestHeader::class.java)
			}

			// Request Params
			val params = method.parameters.filter { MoreElements.isAnnotationPresent(it, RequestParam::class.java) }
			if (params.isNotEmpty()) {
				createStringMap(builder, paramsName, params, RequestParam::class.java)
			}

			// Path variables
			val pathVariables = method.parameters.filter { MoreElements.isAnnotationPresent(it, PathVariable::class.java) }
			if (pathVariables.isNotEmpty()) {
				createStringMap(builder, pathVariablesName, pathVariables, PathVariable::class.java)
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
			val httpMethod = getHttpMethod(method)
			addStatement("val %N = %T.%L", httpMethodName, HttpMethod::class, httpMethod)

			// HttpRequest
			addStatement("val %N = %T(%N, %N, %N, %N)", httpRequestName, HttpRequest::class, urlName, httpMethodName, bodyName, headersName)

			// HttpResponse
			addStatement("val %N = %N.send(%N)", httpResponseName, httpClientName, httpRequestName)
			addStatement("if (!%N.status.successful) {", httpResponseName)
			addStatement("%>throw %T(%N)", HttpCallException::class, httpResponseName)
			addStatement("%<}")

			if (returnType != UNIT) {
				// TODO handle nullability
				if (MoreTypes.isTypeOf(String::class.java, method.returnType)) {
					addStatement("return %N.body!!.content", httpResponseName)
				} else {
					addStatement("return %N.fromJson(%N.body!!.content, %T::class.java)", serializerName, httpResponseName, returnType)
				}
			}
		}
		return builder.build()
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

	private fun getHttpMethod(method: ExecutableElement): HttpMethod {
		val annotation = MoreElements.getAnnotationMirror(method, RequestMapping::class.java).get()
		val httpMethod = getRequestMappingMethod(annotation)
		if (httpMethod == null) {
			if (MoreElements.isAnnotationPresent(method.enclosingElement, RequestMapping::class.java)) {
				val parentAnnotation = MoreElements.getAnnotationMirror(method.enclosingElement, RequestMapping::class.java).get()
				val parentHttpMethod = getRequestMappingMethod(parentAnnotation)
				if (parentHttpMethod != null) {
					return parentHttpMethod
				}
			}
			env.messager.printMessage(Diagnostic.Kind.ERROR, "You have to specify the HTTP method", method, annotation)
			return HttpMethod.GET
		} else {
			return httpMethod
		}
	}

	private fun getRequestMappingMethod(annotation: AnnotationMirror): HttpMethod? {
		val values = annotation.elementValues
		values.entries.forEach { (key, value) ->
			val simpleName = key.simpleName.toString()
			if (simpleName == "method") {
				@Suppress("UNCHECKED_CAST")
				val methods = value.value as List<AnnotationValue>
				if (methods.isNotEmpty()) {
					val element = methods[0].value as VariableElement
					return HttpMethod.valueOf(element.simpleName.toString())
				}
			}
		}
		return null
	}

	private fun createStringMap(builder: FunSpec.Builder, mapName: String, elements: List<VariableElement>, annotation: Class<out Annotation>) {
		builder.addStatement("val %N = mapOf<%T, %T>(", mapName, String::class, String::class)
		elements.forEachIndexed { index, variable ->
			val prefix = if (index == 0) "%>%>" else ""
			val suffix = if (index != elements.lastIndex) "," else ""
			val paramName = getValueOrName(MoreElements.getAnnotationMirror(variable, annotation).get(), variable)
			if (MoreTypes.isTypeOf(String::class.java, variable.asType())) {
				builder.addStatement("$prefix%S to %N$suffix", paramName, variable.simpleName)
			} else {
				builder.addStatement("$prefix%S to %N.toJson(%N)$suffix", paramName, serializerName, variable.simpleName)
			}
		}
		builder.addStatement("%<%<)")
	}

	private fun shouldIncludeParam(element: VariableElement): Boolean {
		return MoreElements.isAnnotationPresent(element, RequestBody::class.java)
				|| MoreElements.isAnnotationPresent(element, RequestHeader::class.java)
				|| MoreElements.isAnnotationPresent(element, RequestParam::class.java)
				|| MoreElements.isAnnotationPresent(element, PathVariable::class.java)
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
