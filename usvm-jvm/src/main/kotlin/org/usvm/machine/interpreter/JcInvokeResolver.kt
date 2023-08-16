package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectClass
import org.jacodb.api.ext.short
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
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.types.first

interface JcInvokeResolver {
    fun JcExprResolver.resolveStaticInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcExprResolver.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcExprResolver.resolveVirtualInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcExprResolver.resolveSpecialInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
    fun JcExprResolver.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>)
}

class JcVirtualInvokeResolver(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
    private val typeSelector: JcTypeSelector,
    private val forkOnRemainingTypes: Boolean = false,
) : JcInvokeResolver {
    override fun JcExprResolver.resolveStaticInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        scope.doWithState { callMethod(this@resolveStaticInvoke, method, arguments) }
    }

    override fun JcExprResolver.resolveSpecialInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
    ) {
        if (skip(method)) {
            return
        }

        scope.doWithState { callMethod(this@resolveSpecialInvoke, method, arguments) }
    }

    override fun JcExprResolver.resolveVirtualInvoke(
        method: JcMethod,
        arguments: List<UExpr<out USort>>,
    ) {
        if (skip(method)) {
            return
        }

        val instance = arguments.first().asExpr(ctx.addressSort)
        val concreteRef = scope.calcOnState { models.first().eval(instance) } as UConcreteHeapRef

        if (concreteRef.address <= INITIAL_INPUT_ADDRESS) {
            val typeStream = scope.calcOnState { models.first().typeStreamOf(concreteRef) }

            val inheritors = typeSelector.choose(method, typeStream)
            val typeConstraints = inheritors.map { type ->
                scope.calcOnState {
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

                    state.callMethod(this, concreteMethod.method, arguments)
                }

                isExpr to block
            }

            if (forkOnRemainingTypes) {
                val excludeAllTypesConstraint = ctx.mkAnd(typeConstraints.map { ctx.mkNot(it) })
                typeConstraintsWithBlockOnStates += excludeAllTypesConstraint to { } // do nothing, just exclude types
            }

            scope.forkMulti(typeConstraintsWithBlockOnStates)
        } else {
            val type = scope.calcOnState { memory.typeStreamOf(concreteRef) }.first()

            val concreteMethod = type.findMethod(method.name, method.description)
                ?: error("Can't find method $method in type ${type.typeName}")

            scope.doWithState { callMethod(this@resolveVirtualInvoke, concreteMethod.method, arguments) }
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

    override fun JcExprResolver.resolveLambdaInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        if (skip(method)) {
            return
        }

        scope.doWithState { callMethod(this@resolveLambdaInvoke, method, arguments) }
    }

    override fun JcExprResolver.resolveDynamicInvoke(method: JcMethod, arguments: List<UExpr<out USort>>) {
        TODO("Dynamic invoke")
    }

    private fun JcExprResolver.skip(method: JcMethod): Boolean {
        if (method.enclosingClass.name == "java.lang.Throwable") {
            scope.doWithState {
                skipMethodInvocation()
            }
            return true
        }
        return false
    }

    private fun JcState.callMethod(
        exprResolver: JcExprResolver,
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ) {
        if (approximateMethod(exprResolver, method, arguments)) {
            return
        }

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
            skipMethodInvocation()
            return@with
        }

        val mockSort = if (returnType is JcRefType) {
            addressSort
        } else {
            when (returnType) {
                cp.boolean -> booleanSort
                cp.byte -> byteSort
                cp.short -> shortSort
                cp.int -> integerSort
                cp.long -> longSort
                cp.char -> charSort
                cp.float -> floatSort
                cp.double -> doubleSort
                else -> error("Unexpected primitive type: $returnType")
            }
        }

        val mockValue = memory.mock { call(method, arguments.asSequence(), mockSort) }

        exitWithValue(method, mockValue)
    }

    // TODO: use approximations
    private fun JcState.approximateMethod(
        exprResolver: JcExprResolver,
        method: JcMethod,
        arguments: List<UExpr<out USort>>
    ): Boolean {
        /**
         * Approximate assertions enabled check.
         * It is correct to enable assertions during analysis.
         * */
        if (method.enclosingClass == ctx.classType.jcClass && method.name == "desiredAssertionStatus") {
            exitWithValue(method, ctx.trueExpr)
            return true
        }

        /**
         * Approximate retrieval of class instance for primitives.
         * */
        if (method.enclosingClass == ctx.classType.jcClass && method.name == "getPrimitiveClass") {
            val classNameRef = arguments.single()
            val predefinedTypeNames = listOf(
                PredefinedPrimitives.Boolean,
                PredefinedPrimitives.Byte,
                PredefinedPrimitives.Short,
                PredefinedPrimitives.Int,
                PredefinedPrimitives.Long,
                PredefinedPrimitives.Char,
                PredefinedPrimitives.Float,
                PredefinedPrimitives.Double
            ).associateBy { exprResolver.mkStringConstRef(it, this) }

            val argumentTypeName = predefinedTypeNames[classNameRef] ?: return false
            val primitive = PredefinedPrimitives.of(argumentTypeName, ctx.cp) ?: return false

            val classRef = with(exprResolver) {
                resolveClassRef(primitive)
            }

            exitWithValue(method, classRef)
            return true
        }

        if (method.enclosingClass == ctx.cp.objectClass && method.name == "getClass") {
            val instance = arguments.first().asExpr(ctx.addressSort)
            val possibleTypes = memory.typeStreamOf(instance).take(2)

            val type = possibleTypes.singleOrNull() ?: return false

            val concreteTypeRef = with(exprResolver) {
                resolveClassRef(type)
            }
            exitWithValue(method, concreteTypeRef)
            return true
        }

        if (method.isNative && method.hasVoidReturnType() && method.parameters.isEmpty()) {
            if (method.enclosingClass.declaration.location.isRuntime) {
                /**
                 * Native methods in the standard library with no return value and no
                 * arguments have no visible effect and can be skipped
                 * */
                exitWithValue(method, ctx.mkVoidValue())
                return true
            }
        }

        return false
    }

    private fun JcMethod.hasVoidReturnType(): Boolean =
        returnType.typeName == ctx.cp.void.typeName

    private fun JcState.exitWithValue(method: JcMethod, value: UExpr<out USort>) {
        methodResult = JcMethodResult.Success(method, value)
        newStmt(lastStmt)
    }

    private fun JcState.skipMethodInvocation() {
        val nextStmt = applicationGraph.successors(lastStmt).single()
        newStmt(nextStmt)
    }
}
