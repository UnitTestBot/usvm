package org.usvm.annotations

import org.usvm.annotations.codegeneration.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@SupportedAnnotationTypes("org.usvm.annotations.CPythonFunction")
@SupportedOptions("headerPath")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class CPythonFunctionProcessor: AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.size != 1)
            return false
        val annotation = annotations.stream().findFirst().get()
        val annotatedElements = roundEnv.getElementsAnnotatedWith(annotation)
        val descriptions = getDescriptions(annotatedElements)
        val code = generateCPythonFunctions(descriptions)
        val headerPath = processingEnv.options["headerPath"] ?: error("Header path not specified")
        val file = File(headerPath, "CPythonFunctions.h")
        file.writeText(code)
        file.createNewFile()
        return true
    }

    private fun getDescriptions(annotatedElements: Collection<Element>): List<CPythonFunctionDescription> =
        annotatedElements.map { element ->
            val headerAnnotation = element.getAnnotation(CPythonAdapterJavaMethod::class.java)
                ?: error("Function annotated with CPythonFunction must also be annotated with CPythonAdapterJavaMethod")
            val name = headerAnnotation.cName
            val curAnnotation = element.getAnnotation(CPythonFunction::class.java)
            val executable = element as? ExecutableElement ?: error("Incorrect usage of annotation CPythonFunction")
            val type = executable.asType() as ExecutableType
            val firstType = executable.parameters.first().asType().toString().split(".").last()
            require(firstType == "ConcolicRunContext") {
                "First argument of function annotated with CPythonFunction must be ConcolicRunContext"
            }
            val numberOfArguments = type.parameterTypes.size - 1
            val argJavaTypes = type.parameterTypes.drop(1).map(::convertJavaType)
            require(curAnnotation.argCTypes.size == numberOfArguments) {
                "Incorrect value of argCTypes"
            }
            require(curAnnotation.argConverters.size == numberOfArguments) {
                "Incorrect value of argConverters"
            }
            val args = (argJavaTypes zip curAnnotation.argCTypes zip curAnnotation.argConverters).map { (p, conv) ->
                val (javaType, cType) = p
                ArgumentDescription(cType, javaType, conv)
            }
            val result = ArgumentDescription(
                curAnnotation.cReturnType,
                convertJavaType(executable.returnType),
                curAnnotation.resultConverter
            )
            CPythonFunctionDescription(
                name,
                args,
                result,
                curAnnotation.failValue,
                curAnnotation.defaultValue,
                curAnnotation.addToSymbolicAdapter
            )
        }

    private fun convertJavaType(typeMirror: TypeMirror): JavaType {
        return when (typeMirror.kind) {
            TypeKind.LONG -> JavaType.JLong
            TypeKind.DECLARED -> JavaType.JObject
            TypeKind.VOID -> JavaType.NoType
            else -> error("Unsupported Java type: $typeMirror")
        }
    }
}