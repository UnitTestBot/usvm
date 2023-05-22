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

val allJars: List<File>
    get() {
        return classpath.filter { it.endsWith(".jar") }.map { File(it) }
    }


private val classpath: List<String>
    get() {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparatorChar)
            .toList()
    }

fun loadFromResources(name: String): File {
    val samples = ClassLoader
        .getSystemClassLoader()
        .getResourceAsStream(name)
        ?: error("Can't find samples")


    val tmpDir = System.getProperty("java.io.tmpdir")

    val bytecode = Paths
        .get(tmpDir, "usvm").apply {
            if (notExists()) {
                createDirectory()
            }
        }
        .resolve(name)
        .toFile()

    bytecode.deleteOnExit()

    samples.copyTo(bytecode.outputStream())
    return bytecode.parentFile
}

fun printAllMethods(jcClass: JcClassOrInterface) {
    val repr = jcClass.declaredMethods.joinToString("\n") {
        buildString {
            append(it.name)
            val flowGraph = it.flowGraph()
            appendLine("[${flowGraph.locals.size}]")
            appendLine(flowGraph.instructions.joinToString("\n").prependIndent("\t"))
        }
    }
    println(repr)
}
