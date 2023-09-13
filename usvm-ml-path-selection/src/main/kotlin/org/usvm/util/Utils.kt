package org.usvm.util

import org.jacodb.api.JcMethod

const val LOG_BASE = 1.42

fun Collection<Long>.prod(): Long {
    return this.reduce { acc, l -> acc * l }
}

fun Collection<Int>.prod(): Int {
    return this.reduce { acc, l -> acc * l }
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

fun getMethodFullName(method: Any?): String {
    return if (method is JcMethod) {
        "${method.enclosingClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})"
    } else {
        method.toString()
    }
}
