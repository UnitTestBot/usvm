package org.usvm.annotations

import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

fun getHeaderPath(processingEnv: ProcessingEnvironment): File {
    val headerPath = processingEnv.options["headerPath"] ?: error("Header path not specified")
    val result = File(headerPath)
    result.mkdirs()
    return result
}

fun TypeMirror.getTypeName(): String =
    toString().split('.').last().split(' ').last()
