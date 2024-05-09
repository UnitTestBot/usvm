package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort

/**
 * An interface for instructions that replace or surround some [originalInst].
 */
interface JcTransparentInstruction : JcInst {
    val originalInst: JcInst
}

/**
 * Auxiliary instruction to handle method calls.
 * */
sealed interface JcMethodCallBaseInst : JcTransparentInstruction {
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
    val entrypointArguments: List<Pair<JcRefType, UHeapRef>>,
) : JcMethodCallBaseInst {
    // We don't care about the location of the entrypoint
    override val location: JcInstLocation
        get() = JcInstLocationImpl(method, -1, -1)

    override val originalInst: JcInst = method.instList.first()
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
    override val method: JcMethod,
    override val location: JcInstLocation,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst,
    val entrypoint: JcInst? = null
) : JcMethodCallBaseInst, JcMethodCall {
    override val originalInst: JcInst = returnSite

    fun resolveEntrypoint(applicationGraph: JcApplicationGraph) =
        copy(entrypoint = applicationGraph.entryPoints(method).singleOrNull())
}

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
    override val returnSite: JcInst,
) : JcMethodCallBaseInst, JcMethodCall {
    fun toConcreteMethodCall(
        concreteMethod: JcMethod,
        applicationGraph: JcApplicationGraph
    ): JcConcreteMethodCallInst =
        JcConcreteMethodCallInst(concreteMethod, location, arguments, returnSite)
            .resolveEntrypoint(applicationGraph)

    override val originalInst: JcInst = returnSite
}

/**
 * Invoke dynamic instruction.
 * The [dynamicCall] can't be processed and the machine
 * must resolve it to some [JcConcreteMethodCallInst] or approximate.
 * */
data class JcDynamicMethodCallInst(
    val dynamicCall: JcDynamicCallExpr,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst,
) : JcMethodCallBaseInst, JcMethodCall {
    override val location: JcInstLocation = returnSite.location
    override val method: JcMethod = dynamicCall.method.method
    override val originalInst: JcInst = returnSite
}
