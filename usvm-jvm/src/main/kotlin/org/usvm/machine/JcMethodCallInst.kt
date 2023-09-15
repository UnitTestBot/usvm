package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort

/**
 * Auxiliary instruction to handle method calls.
 * */
sealed interface JcMethodCallBaseInst : JcInst {
    val method: JcMethod

    override val operands: List<JcExpr>
        get() = emptyList()

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        error("Auxiliary instruction")
    }
}

/**
 * Entrypoint method call instruction.
 * Can be used as initial instruction to start an analysis process.
 * */
data class JcMethodEntrypointInst(
    override val method: JcMethod,
    val entrypointArguments: List<Pair<JcRefType, UHeapRef>>
) : JcMethodCallBaseInst {
    // We don't care about the location of the entrypoint
    override val location: JcInstLocation
        get() = JcInstLocationImpl(method, -1, -1)
}

sealed interface JcMethodCall {
    val location: JcInstLocation
    val method: JcMethod
    val arguments: List<UExpr<out USort>>
    val returnSite: JcInst
}

/**
 * Concrete method call instruction. This call may be replaced with approximation.
 * Otherwise, the [method] is invoked with [arguments] and after method execution
 * the next instruction should be [returnSite].
 * */
data class JcConcreteMethodCallInst(
    override val location: JcInstLocation,
    override val method: JcMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst
) : JcMethodCallBaseInst, JcMethodCall

/**
 * Virtual method call instruction.
 * The [method] is virtual and depends on the actual instance type.
 * The machine shouldn't invoke this method directly but should first
 * resolve it to the [JcConcreteMethodCallInst].
 * */
data class JcVirtualMethodCallInst(
    override val location: JcInstLocation,
    override val method: JcMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst
) : JcMethodCallBaseInst, JcMethodCall {
    fun toConcreteMethodCall(concreteMethod: JcMethod): JcConcreteMethodCallInst =
        JcConcreteMethodCallInst(location, concreteMethod, arguments, returnSite)
}
