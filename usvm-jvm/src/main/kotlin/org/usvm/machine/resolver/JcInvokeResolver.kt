package org.usvm.machine.resolver

import io.ksmt.utils.asExpr
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findMethodOrNull
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcStepScope
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt

interface JcInvokeResolver {
    fun JcStepScope.resolveStaticInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcStepScope.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcStepScope.resolveVirtualInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcStepScope.resolveSpecialInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcStepScope.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
}

class JcVirtualInvokeResolver(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
    private val typeSelector: JcTypeSelector,
) : JcInvokeResolver {
    override fun JcStepScope.resolveStaticInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        doWithState { addNewMethodCall(applicationGraph, method, arguments) }
    }

    override fun JcStepScope.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        doWithState { addNewMethodCall(applicationGraph, method, arguments) }
    }

    override fun JcStepScope.resolveVirtualInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
    ) {
        if (skip(method)) {
            return
        }

        val instance = arguments.first().asExpr(ctx.addressSort)
        val concreteRef = calcOnState { models.first().eval(instance) } as UConcreteHeapRef
        val typeStream = if (concreteRef.address < 0) {
            calcOnState { models.first().typeStreamOf(concreteRef) }
        } else {
            calcOnState { memory.typeStreamOf(concreteRef) }
        }
        val inheritors = typeSelector.choose(typeStream)
        val conditionalWithBlockOnStates = inheritors.map { type ->
            val isExpr = calcOnState {
                with(ctx) {
                    memory.types.evalIsSubtype(instance, type) and
                        memory.types.evalIsSupertype(instance, type)
                }
            }
            val concreteMethod = (type as JcClassType).findMethodOrNull(method.name, method.description)
                ?: error("Not found")
            val block = { state: JcState -> state.addNewMethodCall(applicationGraph, concreteMethod.method, arguments) }
            isExpr to block
        }
        forkMulti(conditionalWithBlockOnStates)
    }

    override fun JcStepScope.resolveSpecialInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
    ) {
        if (skip(method)) {
            return
        }

        doWithState { addNewMethodCall(applicationGraph, method, arguments) }
    }

    private fun JcStepScope.skip(method: JcMethod): Boolean {
        // Skip native method in static initializer
        if ((method.name == "registerNatives" && method.enclosingClass.name == "java.lang.Class")
            || (method.enclosingClass.name == "java.lang.Throwable")
        ) {
            doWithState {
                val nextStmt = applicationGraph.successors(lastStmt).single()
                newStmt(nextStmt)
            }
            return true
        }
        return false
    }

    override fun JcStepScope.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        TODO("Dynamic invoke")
    }
}