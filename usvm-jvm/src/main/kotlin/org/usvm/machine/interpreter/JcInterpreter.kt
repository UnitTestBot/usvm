package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KLogging
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcInstRef
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.ext.boolean
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UInterpreter
import org.usvm.URegisterLValue
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addEntryMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localIdx
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.machine.state.throwExceptionAndDropStackFrame
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.solver.USatResult

typealias JcStepScope = StepScope<JcState, JcType, JcContext>

/**
 * A JacoDB interpreter.
 */
class JcInterpreter(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
) : UInterpreter<JcState>() {

    companion object {
        val logger = object : KLogging() {}.logger
    }

    fun getInitialState(method: JcMethod): JcState {
        val state = JcState(ctx)
        state.addEntryMethodCall(applicationGraph, method)

        val typedMethod = with(applicationGraph) { method.typed }

        if (!method.isStatic) {
            with(ctx) {
                val thisLValue = URegisterLValue(addressSort, 0)
                val ref = state.memory.read(thisLValue).asExpr(addressSort)
                state.pathConstraints += mkEq(ref, nullRef).not()
                state.pathConstraints += mkIsSubtypeExpr(ref, typedMethod.enclosingType)
            }
        }

        typedMethod.parameters.forEachIndexed { idx, typedParameter ->
            with(ctx) {
                val type = typedParameter.type
                if (type is JcRefType) {
                    val argumentLValue = URegisterLValue(typeToSort(type), method.localIdx(idx))
                    val ref = state.memory.read(argumentLValue).asExpr(addressSort)
                    state.pathConstraints += mkIsSubtypeExpr(ref, type)
                }
            }
        }

        val solver = ctx.solver<JcField, JcType, JcMethod, JcContext>()

        val model = (solver.checkWithSoftConstraints(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        return state
    }

    override fun step(state: JcState): StepResult<JcState> {
        val stmt = state.lastStmt

        logger.debug("Step: {}", stmt)

        val scope = StepScope(state)

        // handle exception firstly
        val result = state.methodResult
        if (result is JcMethodResult.JcException) {
            handleException(scope, result, stmt)
            return scope.stepResult()
        }

        when (stmt) {
            is JcAssignInst -> visitAssignInst(scope, stmt)
            is JcIfInst -> visitIfStmt(scope, stmt)
            is JcReturnInst -> visitReturnStmt(scope, stmt)
            is JcGotoInst -> visitGotoStmt(scope, stmt)
            is JcCatchInst -> visitCatchStmt(scope, stmt)
            is JcSwitchInst -> visitSwitchStmt(scope, stmt)
            is JcThrowInst -> visitThrowStmt(scope, stmt)
            is JcCallInst -> visitCallStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }
        return scope.stepResult()
    }

    private fun handleException(
        scope: JcStepScope,
        exception: JcMethodResult.JcException,
        lastStmt: JcInst,
    ) {
        val catchStatements = applicationGraph.successors(lastStmt).filterIsInstance<JcCatchInst>().toList()

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

    private val invokeResolver = JcVirtualInvokeResolver(ctx, applicationGraph, JcFixedInheritorsNumberTypeSelector())

    private fun exprResolverWithScope(scope: JcStepScope) =
        JcExprResolver(
            ctx,
            scope,
            ::mapLocalToIdxMapper,
            ::typeInstanceAllocator,
            ::stringConstantAllocator,
            invokeResolver
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
            // Allocate globally unique ref
            state.memory.heap.allocate()
        }

    private val typeInstanceAllocatedRefs = mutableMapOf<JcTypeInfo, UConcreteHeapRef>()

    private fun typeInstanceAllocator(type: JcType, state: JcState): UConcreteHeapRef {
        val typeInfo = resolveTypeInfo(type)
        return typeInstanceAllocatedRefs.getOrPut(typeInfo) {
            // Allocate globally unique ref
            state.memory.heap.allocate()
        }
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
}
