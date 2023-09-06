package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.usvm.UExpr
import org.usvm.USort

/**
 * Auxiliary instruction to handle method calls.
 * */
sealed interface UMethodCallBaseJcInst : JcInst {
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
data class UMethodEntrypointJcInst(
    override val method: JcMethod
) : UMethodCallBaseJcInst {
    // We don't care about the location of the entrypoint
    override val location: JcInstLocation
        get() = JcInstLocationImpl(method, 0, 0)
}

sealed interface UJcMethodCall {
    val location: JcInstLocation
    val method: JcMethod
    val arguments: List<UExpr<out USort>>
    val returnSite: JcInst
}

/**
 * Concrete method call instruction.
 * The [method] is invoked with [arguments] and after method execution
 * the next instruction should be [returnSite].
 * */
data class UConcreteMethodCallJcInst(
    override val location: JcInstLocation,
    override val method: JcMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst
) : UMethodCallBaseJcInst, UJcMethodCall

/**
 * Virtual method call instruction.
 * The [method] is virtual and depends on the actual instance type.
 * The machine shouldn't invoke this method directly but should first
 * resolve it to the [UConcreteMethodCallJcInst].
 * */
data class UVirtualMethodCallJcInst(
    override val location: JcInstLocation,
    override val method: JcMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst
) : UMethodCallBaseJcInst, UJcMethodCall {
    fun toConcreteMethodCall(concreteMethod: JcMethod): UConcreteMethodCallJcInst =
        UConcreteMethodCallJcInst(location, concreteMethod, arguments, returnSite)
}
