package org.usvm.instrumentation.jacodb.util

import org.jacodb.api.JcClasspath
import java.nio.file.Path

data class ExecutionContext(
    val cm: JcClasspath,
    val loader: ClassLoader,
    val classPath: List<Path>
)
