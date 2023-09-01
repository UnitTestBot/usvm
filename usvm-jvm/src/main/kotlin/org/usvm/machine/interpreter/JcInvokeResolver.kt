package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.toType
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addMethodCall
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

        doWithState { addMethodCall(method, arguments) }
    }

    override fun JcStepScope.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        doWithState { addMethodCall(method, arguments) }
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
                    ctx.mkAnd(
                        memory.types.evalIsSubtype(instance, type),
                        memory.types.evalIsSupertype(instance, type)
                    )
                }
            }

            val typeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()

            inheritors.mapIndexedTo(typeConstraintsWithBlockOnStates) { idx, type ->
                val isExpr = typeConstraints[idx]

                val block = { state: JcState ->
                    val concreteMethod = type.findMethod(method.name, method.description)
                        ?: error("Can't find method $method in type ${type.typeName}")

                    state.addMethodCall(concreteMethod.method, arguments)
                }

                isExpr to block
            }

            if (forkOnRemainingTypes) {
                val excludeAllTypesConstraint = ctx.mkAnd(typeConstraints.map { ctx.mkNot(it) })
                typeConstraintsWithBlockOnStates += excludeAllTypesConstraint to { } // do nothing, just exclude types
            }

            forkMulti(typeConstraintsWithBlockOnStates)
        } else {
            val type = calcOnState { memory.typeStreamOf(concreteRef) }.first()

            val concreteMethod = type.findMethod(method.name, method.description)
                ?: error("Can't find method $method in type ${type.typeName}")

            doWithState { addMethodCall(concreteMethod.method, arguments) }
        }
    }

    private fun JcType.findMethod(name: String, desc: String): JcTypedMethod? = when (this) {
        is JcClassType -> findClassMethod(name, desc)
        // Array types are objects and have methods of java.lang.Object
        is JcArrayType -> jcClass.toType().findClassMethod(name, desc)
        else -> error("Unexpected type: $this")
    }

    private fun JcClassType.findClassMethod(name: String, desc: String): JcTypedMethod? {
        val method = findMethodOrNull { it.name == name && it.method.description == desc }
        if (method != null) return method

        /**
         * Method implementation was not found in current class but class is instantiatable.
         * Therefore, method implementation is provided by the super class.
         * */
        val superClass = superType
        if (superClass != null) {
            return superClass.findClassMethod(name, desc)
        }

        return null
    }

    override fun JcStepScope.resolveSpecialInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
    ) {
        if (skip(method)) {
            return
        }

        doWithState { addMethodCall(method, arguments) }
    }

    override fun JcStepScope.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        TODO("Dynamic invoke")
    }

    private fun JcStepScope.skip(method: JcMethod): Boolean {
        if (method.enclosingClass.name == "java.lang.Throwable") {
            doWithState {
                val nextStmt = applicationGraph.successors(lastStmt).single()
                newStmt(nextStmt)
            }
            return true
        }
        return false
    }
}
