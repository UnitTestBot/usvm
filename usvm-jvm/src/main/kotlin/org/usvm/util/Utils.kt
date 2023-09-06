package org.usvm.util

import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import kotlin.reflect.KClass

fun JcContext.extractJcType(clazz: KClass<*>): JcType = cp.findTypeOrNull(clazz.qualifiedName!!)!!

fun JcContext.extractJcRefType(clazz: KClass<*>): JcRefType = extractJcType(clazz) as JcRefType

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

/**
 * Checks if the method is the same as its definition (i.e. it is false for
 * subclasses' methods which use base definition).
 */
fun JcMethod.isDefinition(): Boolean {
    if (instList.size == 0) {
        return true
    }
    return instList.first().location.method == this
}
