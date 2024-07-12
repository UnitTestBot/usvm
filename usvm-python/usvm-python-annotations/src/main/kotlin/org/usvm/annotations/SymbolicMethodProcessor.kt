package org.usvm.annotations

import org.usvm.annotations.codegeneration.generateSymbolicMethod
import org.usvm.annotations.codegeneration.generateSymbolicMethodInitialization
import org.usvm.annotations.ids.SymbolicMethodId
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType

@SupportedAnnotationTypes("org.usvm.annotations.SymbolicMethod")
@SupportedOptions("headerPath")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class SymbolicMethodProcessor: AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.size != 1)
            return false
        val annotation = annotations.stream().findFirst().get()
        val annotatedElements = roundEnv.getElementsAnnotatedWith(annotation)
        val functionsCode = generateFunctions(annotatedElements)
        val init = generateSymbolicMethodInitialization()
        val headerPath = getHeaderPath(processingEnv)
        val file = File(headerPath, "SymbolicMethods.h")
        file.writeText(init + "\n\n" + functionsCode)
        file.createNewFile()
        return true
    }

    private fun generateFunctions(annotatedElements: Collection<Element>): String {
        val definedIds = mutableSetOf<SymbolicMethodId>()
        val result = annotatedElements.fold("") { acc, element ->
            require(element is ExecutableElement)
            val formatMsg = "Incorrect signature of SymbolicMethod ${element.simpleName}"
            require(element.parameters.size == 3) { formatMsg }
            val arg0 = element.parameters.first().asType().toString().split(".").last()
            require(arg0 == "ConcolicRunContext") { formatMsg }
            val arg1 = element.parameters[1].asType().toString().split(".").last()
            require(arg1 == "SymbolForCPython") { formatMsg }
            val arg2 = element.parameters[2].asType()
            require(arg2 is ArrayType) { formatMsg }
            val arg2Elem = arg2.componentType.toString().split(".").last()
            require(arg2Elem == "SymbolForCPython") { formatMsg }
            val elementAnnotation = element.getAnnotation(SymbolicMethod::class.java)!!
            require(elementAnnotation.id !in definedIds) {
                "SymbolicMethodId ${elementAnnotation.id} must be used in SymbolicMethod only once"
            }
            definedIds.add(elementAnnotation.id)
            val headerAnnotation = element.getAnnotation(CPythonAdapterJavaMethod::class.java)
                ?: error("Function annotated with SymbolicMethod must also be annotated with CPythonAdapterJavaMethod")
            val name = headerAnnotation.cName
            elementAnnotation.id.cName = name
            acc + generateSymbolicMethod(elementAnnotation.id) + "\n\n"
        }
        SymbolicMethodId.values().forEach {
            require(it in definedIds) {
                "SymbolicMethodId $it has no definition"
            }
            require(it.cName != null)
        }
        return result
    }
}