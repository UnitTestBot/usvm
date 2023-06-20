package org.usvm.util

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.cfg.locals
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists

val allClasspath: List<File>
    get() {
        return classpath.map { File(it) }
    }

private val classpath: List<String>
    get() {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparatorChar)
            .toList()
    }
