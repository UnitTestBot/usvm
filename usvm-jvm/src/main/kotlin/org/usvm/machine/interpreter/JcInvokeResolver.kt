package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.logger
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.exitWithValue
import org.usvm.machine.state.skipMethodInvocation
import org.usvm.types.first

interface JcInvokeResolver {
    fun JcStepScope.resolveStaticInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcStepScope.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcStepScope.resolveVirtualInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
        approximate: (JcState, JcMethod, List<UExpr<out USort>>) -> UExpr<out USort>?
    )
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

        doWithState { callMethod(method, arguments) }
    }

    override fun JcStepScope.resolveSpecialInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        doWithState { callMethod(method, arguments) }
    }

    override fun JcStepScope.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        doWithState { callMethod(method, arguments) }
    }

    override fun JcStepScope.resolveVirtualInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
        approximate: (JcState, JcMethod, List<UExpr<out USort>>) -> UExpr<out USort>?
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

                    state.callVirtualMethod(concreteMethod.method, arguments, approximate)
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

            doWithState {
                callVirtualMethod(concreteMethod.method, arguments, approximate)
            }
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

    override fun JcStepScope.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        TODO("Dynamic invoke")
    }

    private fun JcStepScope.skip(method: JcMethod): Boolean {
        if (method.enclosingClass.name == "java.lang.Throwable") {
            doWithState {
                skipMethodInvocation(applicationGraph)
            }
            return true
        }
        return false
    }

    private fun JcState.callVirtualMethod(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
        approximate: (JcState, JcMethod, List<UExpr<out USort>>) -> UExpr<out USort>?
    ) {
        val approximation = approximate(this, method, arguments)
        if (approximation != null) {
            exitWithValue(method, approximation)
            return
        }

        callMethod(method, arguments)
    }

    private fun JcState.callMethod(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (method.isNative) {
            mockNativeMethod(method, arguments)
            return
        }

        addNewMethodCall(applicationGraph, method, arguments)
    }

    private fun JcState.mockNativeMethod(method: JcMethod, arguments: List<UExpr<out USort>>) = with(ctx) {
//        logger.warn { "Mocked: ${method.enclosingClass.name}::${method.name}" }

        val returnType = with(applicationGraph) { method.typed }.returnType

        if (returnType == cp.void) {
            skipMethodInvocation(applicationGraph)
            return@with
        }

        val mockSort = ctx.typeToSort(returnType)
        val mockValue = memory.mock { call(method, arguments.asSequence(), mockSort) }

        if (mockSort == addressSort) {
            pathConstraints += memory.types.evalIsSubtype(mockValue.asExpr(addressSort), returnType)
        }

        exitWithValue(method, mockValue)
    }
}
