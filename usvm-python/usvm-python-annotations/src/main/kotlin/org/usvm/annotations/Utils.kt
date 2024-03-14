package org.usvm.annotations

import java.io.File
import javax.annotation.processing.ProcessingEnvironment

fun getHeaderPath(processingEnv: ProcessingEnvironment): File {
    val headerPath = processingEnv.options["headerPath"] ?: error("Header path not specified")
    val result = File(headerPath)
    result.mkdirs()
    return result
}
