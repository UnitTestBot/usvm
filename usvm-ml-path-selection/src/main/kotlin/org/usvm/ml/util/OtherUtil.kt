package org.usvm.ml.util

import java.io.File

val otherAllClasspath: List<File>
    get() {
        return otherClasspath.map { File(it) }
    }

private val otherClasspath: List<String>
    get() {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparatorChar)
            .toList()
    }
