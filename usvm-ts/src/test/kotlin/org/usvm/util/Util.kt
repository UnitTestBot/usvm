package org.usvm.util

import mu.KotlinLogging
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.toPath

private val logger = KotlinLogging.logger {}

fun getResourcePathOrNull(res: String): Path? {
    require(res.startsWith("/")) { "Resource path must start with '/': '$res'" }
    return object {}::class.java.getResource(res)?.toURI()?.toPath()
}

fun getResourcePath(res: String): Path {
    return getResourcePathOrNull(res) ?: error("Resource not found: '$res'")
}

fun getResourceStream(res: String): InputStream {
    require(res.startsWith("/")) { "Resource path must start with '/': '$res'" }
    return object {}::class.java.getResourceAsStream(res)
        ?: error("Resource not found: '$res'")
}
