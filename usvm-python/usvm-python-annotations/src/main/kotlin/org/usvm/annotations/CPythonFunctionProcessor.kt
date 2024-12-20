package org.usvm.annotations

import org.usvm.annotations.codegeneration.generateCPythonFunctionHeader
import org.usvm.annotations.codegeneration.generateCPythonFunctionsImpls
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@SupportedAnnotationTypes("org.usvm.annotations.CPythonFunction")
@SupportedOptions("headerPath")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class CPythonFunctionProcessor : AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.size != 1) {
            return false
        }
        val annotation = annotations.stream().findFirst().get()
        val annotatedElements = roundEnv.getElementsAnnotatedWith(annotation)
        val descriptions = getDescriptions(annotatedElements)
        val headerName = "CPythonFunctions.h"
        val implCode = generateCPythonFunctionsImpls(descriptions, headerName)
        val headerCode = generateCPythonFunctionHeader(descriptions)
        val headerPath = getHeaderPath(processingEnv)
        val headerFile = File(headerPath, headerName)
        headerFile.writeText(headerCode)
        headerFile.createNewFile()
        val implFile = File(headerPath, "CPythonFunctions.c")
        implFile.writeText(implCode)
        implFile.createNewFile()
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
            val firstType = executable.parameters
                .first()
                .asType()
                .getTypeName()
            require(firstType == "ConcolicRunContext") {
                "First argument of function annotated with CPythonFunction must be ConcolicRunContext"
            }
            val numberOfArguments = type.parameterTypes.size - 1
            val argJavaTypes = type.parameterTypes.drop(1).map(::convertJavaType)
            require(curAnnotation.argCTypes.size == numberOfArguments) {
                "Incorrect value of argCTypes (size must be $numberOfArguments)"
            }
            require(curAnnotation.argConverters.size == numberOfArguments) {
                "Incorrect value of argConverters (size must be $numberOfArguments)"
            }
            val args = (argJavaTypes zip curAnnotation.argCTypes zip curAnnotation.argConverters).map { (p, conv) ->
                val (javaType, cType) = p
                ArgumentDescription(cType, javaType, conv)
            }
            val returnTypeName = executable.returnType.toString().split(".").last()
            require(returnTypeName == "void" || returnTypeName == "SymbolForCPython") {
                "Return type must be void or SymbolForCPython, not $returnTypeName"
            }
            when (val returnType = convertJavaType(executable.returnType)) {
                JavaType.JObject -> {
                    val descr = ArgumentDescription(
                        CType.PyObject,
                        JavaType.JObject,
                        ObjectConverter.ObjectWrapper
                    )
                    CPythonFunctionDescription(
                        name,
                        args,
                        descr,
                        "0",
                        "Py_None",
                        curAnnotation.addToSymbolicAdapter
                    )
                }
                JavaType.NoType -> {
                    val descr = ArgumentDescription(
                        CType.CInt,
                        JavaType.NoType,
                        ObjectConverter.NoConverter
                    )
                    CPythonFunctionDescription(
                        name,
                        args,
                        descr,
                        "-1",
                        "0",
                        curAnnotation.addToSymbolicAdapter
                    )
                }
                else -> {
                    error("Incorrect Java return type $returnType for CPythonFunction")
                }
            }
        }

    private fun convertJavaType(typeMirror: TypeMirror): JavaType {
        if (typeMirror is ArrayType) {
            require(typeMirror.componentType.toString().split(".").last() == "SymbolForCPython") {
                "Array must consist of SymbolForCPython"
            }
            return JavaType.JObjectArray
        }
        return when (typeMirror.kind) {
            TypeKind.LONG -> JavaType.JLong
            TypeKind.INT -> JavaType.JInt
            TypeKind.DECLARED -> JavaType.JObject
            TypeKind.BOOLEAN -> JavaType.JBoolean
            TypeKind.VOID -> JavaType.NoType
            else -> error("Unsupported Java type: $typeMirror")
        }
    }
}
