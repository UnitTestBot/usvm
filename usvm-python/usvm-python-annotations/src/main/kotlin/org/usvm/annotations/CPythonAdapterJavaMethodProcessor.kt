package org.usvm.annotations

import org.usvm.annotations.codegeneration.DefinitionDescriptor
import org.usvm.annotations.codegeneration.generateCPythonAdapterDefs
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

@SupportedAnnotationTypes("org.usvm.annotations.CPythonAdapterJavaMethod")
@SupportedOptions("headerPath")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class CPythonAdapterJavaMethodProcessor: AbstractProcessor() {
    private val converter = ConverterToJNITypeDescriptor()

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.size != 1)
            return false
        val annotation = annotations.stream().findFirst().get()
        val annotatedElements = roundEnv.getElementsAnnotatedWith(annotation)
        val definitions = annotatedElements.map { element ->
            val curAnnotation = element.getAnnotation(CPythonAdapterJavaMethod::class.java)
            DefinitionDescriptor(
                curAnnotation.cName,
                element.simpleName.toString(),
                converter.convert(element.asType())
            )
        }
        val headerPath = processingEnv.options["headerPath"] ?: error("Header path not specified")
        val file = File(headerPath, "CPythonAdapterMethods.h")
        val code = generateCPythonAdapterDefs(definitions)
        file.writeText(code)
        file.createNewFile()
        return true
    }
}