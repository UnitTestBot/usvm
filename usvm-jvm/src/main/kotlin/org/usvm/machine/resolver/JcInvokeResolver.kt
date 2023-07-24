package org.usvm.machine.resolver

import io.ksmt.utils.asExpr
import org.jacodb.api.JcClassType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.findMethodOrNull
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcStepScope
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall

interface JcInvokeResolver {
    fun JcStepScope.resolveInvoke(method: JcTypedMethod, arguments: List<UExpr<out USort>>)
}

class JcVirtualInvokeResolver(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
) : JcInvokeResolver {
    override fun JcStepScope.resolveInvoke(method: JcTypedMethod, arguments: List<UExpr<out USort>>) {
        val instance = arguments.first().asExpr(ctx.addressSort)
        val concreteRef = calcOnState { models.first().eval(instance) }?.asExpr(ctx.addressSort) as UConcreteHeapRef
        val typeStream = if (concreteRef.address < 0) {
            calcOnState { models.first().typeStreamOf(concreteRef) }
        } else {
            calcOnState { memory.typeStreamOf(concreteRef) }
        } ?: return
        val inheritors = typeStream.take(5)
        val conditionalWithBlockOnStates = inheritors.map { type ->
            val isExpr = calcOnState {
                with(ctx) {
                    memory.types.evalIsSubtype(instance, type) and
                        memory.types.evalIsSupertype(instance, type)
                }
            }!!
            val concreteMethod = (type as JcClassType).findMethodOrNull(method.name, method.method.description)
                ?: error("Not found")
            val block = { state: JcState -> state.addNewMethodCall(applicationGraph, concreteMethod.method, arguments) }
            isExpr to block
        }
        forkMulti(conditionalWithBlockOnStates)
    }
}