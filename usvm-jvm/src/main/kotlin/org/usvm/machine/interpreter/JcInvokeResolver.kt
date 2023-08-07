package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findMethodOrNull
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.types.first

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
    private val forkOnRemainingTypes: Boolean = false,
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

        if (concreteRef.address <= INITIAL_INPUT_ADDRESS) {
            val typeStream = calcOnState { models.first().typeStreamOf(concreteRef) }

            val inheritors = typeSelector.choose(method, typeStream)
            val typeConstraints = inheritors.map { type ->
                calcOnState {
                    ctx.mkAnd(memory.types.evalIsSubtype(instance, type), memory.types.evalIsSupertype(instance, type))
                }
            }
            val typeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()
            inheritors.mapIndexedTo(typeConstraintsWithBlockOnStates) { idx, type ->
                val isExpr = typeConstraints[idx]
                val concreteMethod = (type as JcClassType).findMethodOrNull(method.name, method.description)
                    ?: error("Can't find method $method in type $type")

                val block = { state: JcState ->
                    state.addNewMethodCall(applicationGraph, concreteMethod.method, arguments)
                }
                isExpr to block
            }

            if (forkOnRemainingTypes) {
                val excludeAllTypesConstraint = ctx.mkOr(typeConstraints.map { ctx.mkNot(it) })
                typeConstraintsWithBlockOnStates += excludeAllTypesConstraint to { } // do nothing, just exclude types
            }

            forkMulti(typeConstraintsWithBlockOnStates)
        } else {
            val type = calcOnState { memory.typeStreamOf(concreteRef) }.first() as JcClassType

            val concreteMethod = type.findMethodOrNull(method.name, method.description)
                ?: error("Can't find method $method in type $type")

            doWithState { addNewMethodCall(applicationGraph, concreteMethod.method, arguments) }
        }
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

    override fun JcStepScope.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        TODO("Dynamic invoke")
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
}
