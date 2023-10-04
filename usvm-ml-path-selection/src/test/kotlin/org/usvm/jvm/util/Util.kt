package org.usvm.org.usvm.jvm.util

import java.io.File

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
