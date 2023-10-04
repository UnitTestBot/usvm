package org.usvm.jvm.interpreter

import io.ksmt.utils.asExpr
import mu.KLogging
import org.jacodb.api.*
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.void
import org.usvm.*
import org.usvm.api.allocateStaticRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.targets.JcTarget
import org.usvm.api.typeStreamOf
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.jvm.JcApplicationBlockGraph
import org.usvm.machine.*
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcFixedInheritorsNumberTypeSelector
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.interpreter.JcTypeSelector
import org.usvm.machine.state.*
import org.usvm.memory.URegisterStackLValue
import org.usvm.solver.USatResult
import org.usvm.types.first
import org.usvm.util.findMethod
import org.usvm.util.write

/**
 * A JacoDB interpreter.
 */
class JcBlockInterpreter(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationBlockGraph,
    var forkBlackList: UForkBlackList<JcState, JcInst> = UForkBlackList.createDefault(),
) : UInterpreter<JcState>() {

    companion object {
        val logger = object : KLogging() {}.logger
    }

    fun getInitialState(method: JcMethod, targets: List<JcTarget> = emptyList()): JcState {
        val state = JcState(ctx, targets = targets)
        val typedMethod = with(applicationGraph) { method.typed }

        val entrypointArguments = mutableListOf<Pair<JcRefType, UHeapRef>>()
        if (!method.isStatic) {
            with(ctx) {
                val thisLValue = URegisterStackLValue(addressSort, 0)
                val ref = state.memory.read(thisLValue).asExpr(addressSort)
                state.pathConstraints += mkEq(ref, nullRef).not()
                val thisType = typedMethod.enclosingType
                state.pathConstraints += mkIsSubtypeExpr(ref, thisType)

                entrypointArguments += thisType to ref
            }
        }

        typedMethod.parameters.forEachIndexed { idx, typedParameter ->
            with(ctx) {
                val type = typedParameter.type
                if (type is JcRefType) {
                    val argumentLValue = URegisterStackLValue(typeToSort(type), method.localIdx(idx))
                    val ref = state.memory.read(argumentLValue).asExpr(addressSort)
                    state.pathConstraints += mkIsSubtypeExpr(ref, type)

                    entrypointArguments += type to ref
                }
            }
        }

        val solver = ctx.solver<JcType>()

        val model = (solver.checkWithSoftConstraints(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypointInst = JcMethodEntrypointInst(method, entrypointArguments)
        state.newStmt(entrypointInst)
        return state
    }

    override fun step(state: JcState): StepResult<JcState> {
        val stmt = state.lastStmt

        logger.debug("Step: {}", stmt)

        val scope = StepScope(state, forkBlackList)

        // handle exception firstly
        val result = state.methodResult
        if (result is JcMethodResult.JcException) {
            handleException(scope, result, stmt)
            return scope.stepResult()
        }

        when (stmt) {
            is JcMethodCallBaseInst -> visitMethodCall(scope, stmt)
            is JcAssignInst -> visitAssignInst(scope, stmt)
            is JcIfInst -> visitIfStmt(scope, stmt)
            is JcReturnInst -> visitReturnStmt(scope, stmt)
            is JcGotoInst -> visitGotoStmt(scope, stmt)
            is JcCatchInst -> visitCatchStmt(scope, stmt)
            is JcSwitchInst -> visitSwitchStmt(scope, stmt)
            is JcThrowInst -> visitThrowStmt(scope, stmt)
            is JcCallInst -> visitCallStmt(scope, stmt)
            is JcEnterMonitorInst -> visitMonitorEnterStmt(scope, stmt)
            is JcExitMonitorInst -> visitMonitorExitStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }
        return scope.stepResult()
    }

    private fun handleException(
        scope: JcStepScope,
        exception: JcMethodResult.JcException,
        lastStmt: JcInst,
    ) {
        val catchStatements = applicationGraph.jcApplicationGraph.successors(lastStmt).filterIsInstance<JcCatchInst>().toList()

        val typeConstraintsNegations = mutableListOf<UBoolExpr>()
        val catchForks = mutableListOf<Pair<UBoolExpr, JcState.() -> Unit>>()

        val blockToFork: (JcCatchInst) -> (JcState) -> Unit = { catchInst: JcCatchInst ->
            block@{ state: JcState ->
                val lValue = exprResolverWithScope(scope).resolveLValue(catchInst.throwable) ?: return@block
                val exceptionResult = state.methodResult as JcMethodResult.JcException

                state.memory.write(lValue, exceptionResult.address)

                state.methodResult = JcMethodResult.NoCall
                state.newStmt(catchInst.nextStmt)
            }
        }

        catchStatements.forEach { catchInst ->
            val throwableTypes = catchInst.throwableTypes

            val typeConstraint = scope.calcOnState {
                val currentTypeConstraints = throwableTypes.map { memory.types.evalIsSubtype(exception.address, it) }
                val result = ctx.mkAnd(typeConstraintsNegations + ctx.mkOr(currentTypeConstraints))

                typeConstraintsNegations += currentTypeConstraints.map { ctx.mkNot(it) }

                result
            }

            catchForks += typeConstraint to blockToFork(catchInst)
        }

        val typeConditionToMiss = ctx.mkAnd(typeConstraintsNegations)
        val functionBlockOnMiss = block@{ _: JcState ->
            scope.calcOnState { throwExceptionAndDropStackFrame() }
        }

        val catchSectionMiss = typeConditionToMiss to functionBlockOnMiss

        scope.forkMulti(catchForks + catchSectionMiss)
    }

    private val typeSelector = JcFixedInheritorsNumberTypeSelector()

    private fun visitMethodCall(scope: JcStepScope, stmt: JcMethodCallBaseInst) {
        when (stmt) {
            is JcMethodEntrypointInst -> {
                scope.doWithState {
                    if (callStack.isEmpty()) {
                        val method = stmt.method
                        callStack.push(method, returnSite = null)
                        memory.stack.push(method.parametersWithThisCount, method.localsCount)
                    }
                }

                val exprResolver = exprResolverWithScope(scope)
                // Run static initializer for all enum arguments of the entrypoint
                for ((type, ref) in stmt.entrypointArguments) {
                    exprResolver.ensureExprCorrectness(ref, type) ?: return
                }

                val method = stmt.method
                val entryPoint = applicationGraph.jcApplicationGraph.entryPoints(method).single()
                scope.doWithState {
                    newStmt(entryPoint)
                }
            }

            is JcConcreteMethodCallInst -> {
                if (approximateMethod(scope, stmt)) {
                    return
                }

                if (stmt.method.isNative) {
                    mockNativeMethod(scope, stmt)
                    return
                }

                scope.doWithState {
                    addNewMethodCall(applicationGraph.jcApplicationGraph, stmt)
                }
            }

            is JcVirtualMethodCallInst -> {
                if (approximateMethod(scope, stmt)) {
                    return
                }

                resolveVirtualInvoke(stmt, scope, typeSelector, forkOnRemainingTypes = false)
            }
        }
    }

    private fun visitAssignInst(scope: JcStepScope, stmt: JcAssignInst) {
        val exprResolver = exprResolverWithScope(scope)
        val lvalue = exprResolver.resolveLValue(stmt.lhv) ?: return
        val expr = exprResolver.resolveJcExpr(stmt.rhv, stmt.lhv.type) ?: return

        val nextStmt = stmt.nextStmt
        scope.doWithState {
            memory.write(lvalue, expr)
            newStmt(nextStmt)
        }
    }

    private fun visitIfStmt(scope: JcStepScope, stmt: JcIfInst) {
        val exprResolver = exprResolverWithScope(scope)

        val boolExpr = exprResolver
            .resolveJcExpr(stmt.condition)
            ?.asExpr(ctx.boolSort)
            ?: return

        val instList = stmt.location.method.instList
        val (posStmt, negStmt) = instList[stmt.trueBranch.index] to instList[stmt.falseBranch.index]

        scope.fork(
            boolExpr,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
    }

    private fun visitReturnStmt(scope: JcStepScope, stmt: JcReturnInst) {
        val exprResolver = exprResolverWithScope(scope)
        val method = requireNotNull(scope.calcOnState { callStack.lastMethod() })
        val returnType = with(applicationGraph) { method.typed }.returnType

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolveJcExpr(it, returnType) ?: return }
            ?: ctx.mkVoidValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitGotoStmt(scope: JcStepScope, stmt: JcGotoInst) {
        val nextStmt = stmt.location.method.instList[stmt.target.index]
        scope.doWithState { newStmt(nextStmt) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun visitCatchStmt(scope: JcStepScope, stmt: JcCatchInst) {
        error("The catch instruction must be unfolded during processing of the instructions led to it. Encountered inst: $stmt")
    }

    private fun visitSwitchStmt(scope: JcStepScope, stmt: JcSwitchInst) {
        val exprResolver = exprResolverWithScope(scope)

        val switchKey = stmt.key
        // Note that the switch key can be an rvalue, for example, a simple int constant.
        val instList = stmt.location.method.instList

        with(ctx) {
            val caseStmtsWithConditions = stmt.branches.map { (caseValue, caseTargetStmt) ->
                val nextStmt = instList[caseTargetStmt]
                val jcEqExpr = JcEqExpr(cp.boolean, switchKey, caseValue)
                val caseCondition = exprResolver.resolveJcExpr(jcEqExpr)?.asExpr(boolSort) ?: return

                caseCondition to { state: JcState -> state.newStmt(nextStmt) }
            }

            // To make the default case possible, we need to ensure that all case labels are unsatisfiable
            val defaultCaseWithCondition = mkAnd(
                caseStmtsWithConditions.map { it.first.not() }
            ) to { state: JcState -> state.newStmt(instList[stmt.default]) }

            scope.forkMulti(caseStmtsWithConditions + defaultCaseWithCondition)
        }
    }

    private fun visitThrowStmt(scope: JcStepScope, stmt: JcThrowInst) {
        val resolver = exprResolverWithScope(scope)
        val address = resolver.resolveJcExpr(stmt.throwable)?.asExpr(ctx.addressSort) ?: return

        scope.calcOnState {
            throwExceptionWithoutStackFrameDrop(address, stmt.throwable.type)
        }
    }

    private fun visitCallStmt(scope: JcStepScope, stmt: JcCallInst) {
        val exprResolver = exprResolverWithScope(scope)
        exprResolver.resolveJcExpr(stmt.callExpr) ?: return

        scope.doWithState {
            val nextStmt = stmt.nextStmt
            newStmt(nextStmt)
        }
    }

    private fun visitMonitorEnterStmt(scope: JcStepScope, stmt: JcEnterMonitorInst) {
        val exprResolver = exprResolverWithScope(scope)
        exprResolver.resolveJcNotNullRefExpr(stmt.monitor, stmt.monitor.type) ?: return

        // Monitor enter makes sense only in multithreaded environment

        scope.doWithState {
            newStmt(stmt.nextStmt)
        }
    }

    private fun visitMonitorExitStmt(scope: JcStepScope, stmt: JcExitMonitorInst) {
        val exprResolver = exprResolverWithScope(scope)
        exprResolver.resolveJcNotNullRefExpr(stmt.monitor, stmt.monitor.type) ?: return

        // Monitor exit makes sense only in multithreaded environment

        scope.doWithState {
            newStmt(stmt.nextStmt)
        }
    }

    private fun exprResolverWithScope(scope: JcStepScope) =
        JcExprResolver(
            ctx,
            scope,
            ::mapLocalToIdxMapper,
            ::typeInstanceAllocator,
            ::stringConstantAllocator,
            ::classInitializerAlwaysAnalysisRequiredForType
        )

    private val localVarToIdx = mutableMapOf<JcMethod, MutableMap<String, Int>>() // (method, localName) -> idx

    // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
    private fun mapLocalToIdxMapper(method: JcMethod, local: JcLocal) =
        when (local) {
            is JcLocalVar -> localVarToIdx
                .getOrPut(method) { mutableMapOf() }
                .run {
                    getOrPut(local.name) { method.parametersWithThisCount + size }
                }

            is JcThis -> 0
            is JcArgument -> method.localIdx(local.index)
            else -> error("Unexpected local: $local")
        }

    private val JcInst.nextStmt get() = location.method.instList[location.index + 1]
    private operator fun JcInstList<JcInst>.get(instRef: JcInstRef): JcInst = this[instRef.index]

    private val stringConstantAllocatedRefs = mutableMapOf<String, UConcreteHeapRef>()

    // Equal string constants must have equal references
    private fun stringConstantAllocator(value: String, state: JcState): UConcreteHeapRef =
        stringConstantAllocatedRefs.getOrPut(value) {
            // Allocate globally unique ref with a negative address
            state.memory.allocateStaticRef()
        }

    private val typeInstanceAllocatedRefs = mutableMapOf<JcTypeInfo, UConcreteHeapRef>()

    private fun typeInstanceAllocator(type: JcType, state: JcState): UConcreteHeapRef {
        val typeInfo = resolveTypeInfo(type)
        return typeInstanceAllocatedRefs.getOrPut(typeInfo) {
            // Allocate globally unique ref with a negative address
            state.memory.allocateStaticRef()
        }
    }

    private fun classInitializerAlwaysAnalysisRequiredForType(type: JcRefType): Boolean {
        // Always analyze a static initializer for enums
        return type.jcClass.isEnum
    }

    private fun resolveTypeInfo(type: JcType): JcTypeInfo = when (type) {
        is JcClassType -> JcClassTypeInfo(type.jcClass)
        is JcPrimitiveType -> JcPrimitiveTypeInfo(type)
        is JcArrayType -> JcArrayTypeInfo(resolveTypeInfo(type.elementType))
        else -> error("Unexpected type: $type")
    }

    private sealed interface JcTypeInfo

    private data class JcClassTypeInfo(val className: String) : JcTypeInfo {
        // Don't use type.typeName here, because it contains generic parameters
        constructor(cls: JcClassOrInterface) : this(cls.name)
    }

    private data class JcPrimitiveTypeInfo(val type: JcPrimitiveType) : JcTypeInfo

    private data class JcArrayTypeInfo(val element: JcTypeInfo) : JcTypeInfo

    private fun resolveVirtualInvoke(
        methodCall: JcVirtualMethodCallInst,
        scope: JcStepScope,
        typeSelector: JcTypeSelector,
        forkOnRemainingTypes: Boolean,
    ): Unit = with(methodCall) {
        val instance = arguments.first().asExpr(ctx.addressSort)
        val concreteRef = scope.calcOnState { models.first().eval(instance) } as UConcreteHeapRef

        if (isAllocatedConcreteHeapRef(concreteRef) || isStaticHeapRef(concreteRef)) {
            // We have only one type for allocated and static heap refs
            val type = scope.calcOnState { memory.typeStreamOf(concreteRef) }.first()

            val concreteMethod = type.findMethod(method)
                ?: error("Can't find method $method in type ${type.typeName}")

            scope.doWithState {
                val concreteCall = methodCall.toConcreteMethodCall(concreteMethod.method)
                newStmt(concreteCall)
            }

            return@with
        }

        val typeStream = scope.calcOnState { models.first().typeStreamOf(concreteRef) }

            val inheritors = typeSelector.choose(method, typeStream)
            val typeConstraints = inheritors.map { type ->
                scope.calcOnState {
                    memory.types.evalTypeEquals(instance, type)
                }
            }

        val typeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()

        inheritors.mapIndexedTo(typeConstraintsWithBlockOnStates) { idx, type ->
            val isExpr = typeConstraints[idx]

                val block = { state: JcState ->
                    val concreteMethod = type.findMethod(method)
                        ?: error("Can't find method $method in type ${type.typeName}")

                val concreteCall = methodCall.toConcreteMethodCall(concreteMethod.method)
                state.newStmt(concreteCall)
            }

            isExpr to block
        }

        if (forkOnRemainingTypes) {
            val excludeAllTypesConstraint = ctx.mkAnd(typeConstraints.map { ctx.mkNot(it) })
            typeConstraintsWithBlockOnStates += excludeAllTypesConstraint to { } // do nothing, just exclude types
        }

        scope.forkMulti(typeConstraintsWithBlockOnStates)
    }

    private val approximationResolver = JcMethodApproximationResolver(ctx, applicationGraph.jcApplicationGraph)

    private fun approximateMethod(scope: JcStepScope, methodCall: JcMethodCall): Boolean {
        val exprResolver = exprResolverWithScope(scope)
        return approximationResolver.approximate(scope, exprResolver, methodCall)
    }

    private fun mockNativeMethod(
        scope: JcStepScope,
        methodCall: JcConcreteMethodCallInst
    ) = with(methodCall) {
        logger.warn { "Mocked: ${method.enclosingClass.name}::${method.name}" }

        val returnType = with(applicationGraph) { method.typed }.returnType

        if (returnType == ctx.cp.void) {
            scope.doWithState { skipMethodInvocationWithValue(methodCall, ctx.voidValue) }
            return@with
        }

        val mockSort = ctx.typeToSort(returnType)
        val mockValue = scope.calcOnState {
            memory.mock { call(method, arguments.asSequence(), mockSort) }
        }

        if (mockSort == ctx.addressSort) {
            val constraint = scope.calcOnState {
                memory.types.evalIsSubtype(mockValue.asExpr(ctx.addressSort), returnType)
            }
            scope.assert(constraint) ?: return
        }

        scope.doWithState {
            skipMethodInvocationWithValue(methodCall, mockValue)
        }
    }
}