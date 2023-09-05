package org.usvm.util

import java.io.File

const val LOG_BASE = 1.42

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

fun Collection<Float>.average(): Float {
    return this.sumOf { it.toDouble() }.toFloat() / this.size
}

fun Number.log(): Float {
    return kotlin.math.log(this.toDouble() + 1, LOG_BASE).toFloat()
}

fun UInt.log(): Float {
    return this.toDouble().log()
}

fun <T> List<T>.getLast(count: Int): List<T> {
    return this.subList(this.size - count, this.size)
}

fun String.escape(): String {
    val result = StringBuilder(this.length)
    this.forEach { ch ->
        result.append(
            when (ch) {
                '\n' -> "\\n"
                '\t' -> "\\t"
                '\b' -> "\\b"
                '\r' -> "\\r"
                '\"' -> "\\\""
                '\'' -> "\\\'"
                '\\' -> "\\\\"
                '$' -> "\\$"
                else -> ch
            }
        )
    }
    return result.toString()
}
