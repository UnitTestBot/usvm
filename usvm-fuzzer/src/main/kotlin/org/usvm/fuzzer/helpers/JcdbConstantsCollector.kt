package org.usvm.fuzzer.helpers

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.usvm.fuzzer.util.classType
import org.usvm.instrumentation.util.stringType

class JcdbConstantsCollector(
    private val jcClasspath: JcClasspath
) : AbstractFullRawExprSetCollector() {

    val extractedConstants = mutableMapOf<JcType, MutableSet<Any>>()

    fun collect(targetMethod: JcMethod) {
        targetMethod.rawInstList.forEach { it.accept(this) }
    }

    override fun ifMatches(expr: JcRawExpr) {
        when (expr) {
            is JcRawInt -> extractedConstants.getOrPut(jcClasspath.int) { mutableSetOf() }.add(expr.value)
            is JcRawByte -> extractedConstants.getOrPut(jcClasspath.byte) { mutableSetOf() }.add(expr.value)
            is JcRawChar -> extractedConstants.getOrPut(jcClasspath.char) { mutableSetOf() }.add(expr.value)
            is JcRawClassConstant -> extractedConstants.getOrPut(jcClasspath.classType()) { mutableSetOf() }.add(expr.className)
            is JcRawDouble -> extractedConstants.getOrPut(jcClasspath.double) { mutableSetOf() }.add(expr.value)
            is JcRawFloat -> extractedConstants.getOrPut(jcClasspath.float) { mutableSetOf() }.add(expr.value)
            is JcRawLong -> extractedConstants.getOrPut(jcClasspath.long) { mutableSetOf() }.add(expr.value)
            is JcRawMethodConstant -> {}
            is JcRawShort -> extractedConstants.getOrPut(jcClasspath.short) { mutableSetOf() }.add(expr.value)
            is JcRawStringConstant -> extractedConstants.getOrPut(jcClasspath.stringType()) { mutableSetOf() }.add(expr.value)
            else -> {}
        }
    }
}