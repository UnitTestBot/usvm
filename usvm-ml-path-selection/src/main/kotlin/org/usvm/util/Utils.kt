package org.usvm.util

import org.jacodb.api.*
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.api.ext.toType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.JcTransparentInstruction
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import kotlin.reflect.KClass

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

fun JcContext.extractJcType(clazz: KClass<*>): JcType = cp.findTypeOrNull(clazz.qualifiedName!!)!!

fun JcContext.extractJcRefType(clazz: KClass<*>): JcRefType = extractJcType(clazz) as JcRefType

val JcClassOrInterface.enumValuesField: JcTypedField
    get() = toType().findFieldOrNull("\$VALUES") ?: error("No \$VALUES field found for the enum type $this")

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

internal fun UWritableMemory<JcType>.allocHeapRef(type: JcType, useStaticAddress: Boolean): UConcreteHeapRef =
    if (useStaticAddress) allocStatic(type) else allocConcrete(type)

tailrec fun JcInst.originalInst(): JcInst = if (this is JcTransparentInstruction) originalInst.originalInst() else this

fun getMethodFullName(method: Any?): String {
    return if (method is JcMethod) {
        "${method.enclosingClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})"
    } else {
        method.toString()
    }
}
