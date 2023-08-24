package org.usvm.instrumentation.util

import java.io.File
import java.net.URLClassLoader

val File.isJar get() = this.name.endsWith(".jar")
val File.isClass get() = this.name.endsWith(".class")
val File.className get() = this.name.removeSuffix(".class")

val File.classLoader get() = URLClassLoader(arrayOf(toURI().toURL()))

val File.allEntries: List<File>
    get() {
        val result = mutableListOf<File>()
        val queue = queueOf(this)
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            if (current.isFile) {
                result += current
            } else if (current.isDirectory) {
                queue.addAll(current.listFiles() ?: arrayOf())
            }
        }
        return result
    }
