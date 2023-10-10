package org.usvm.annotations

import org.usvm.annotations.codegeneration.MemberDescriptorInfo
import org.usvm.annotations.codegeneration.generateDescriptorChecks
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@SupportedAnnotationTypes("org.usvm.annotations.SymbolicMemberDescriptor")
@SupportedOptions("headerPath")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class SymbolicMemberDescriptorProcessor: AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.size != 1)
            return false
        val annotation = annotations.stream().findFirst().get()
        val annotatedElements = roundEnv.getElementsAnnotatedWith(annotation)
        val info = getInfo(annotatedElements)
        val code = generateDescriptorChecks(info)
        val headerPath = processingEnv.options["headerPath"] ?: error("Header path not specified")
        val file = File(headerPath, "MemberDescriptors.h")
        file.writeText(code)
        file.createNewFile()
        return true
    }

    private fun getInfo(elements: Collection<Element>): List<MemberDescriptorInfo> =
        elements.map { element ->
            val annotation = element.getAnnotation(SymbolicMemberDescriptor::class.java)
            MemberDescriptorInfo(
                annotation.nativeTypeName,
                annotation.nativeMemberName,
                element.simpleName.toString()
            )
        }
}