package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcThrowInst
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.URegisterLValue
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.WrappedException
import org.usvm.machine.state.addEntryMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localIdx
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.machine.state.throwException
import org.usvm.solver.USatResult

typealias JcStepScope = StepScope<JcState, JcType, JcField>

/**
 * A JacoDB interpreter.
 */
class JcInterpreter(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
) : UInterpreter<JcState>() {
    fun getInitialState(method: JcMethod): JcState {
        val state = JcState(ctx)
        state.addEntryMethodCall(applicationGraph, method)

        val typedMethod = with(applicationGraph) { method.typed }

        if (!method.isStatic) {
            with(ctx) {
                val thisLValue = URegisterLValue(addressSort, 0)
                val ref = state.memory.read(thisLValue).asExpr(addressSort)
                state.pathConstraints += mkEq(ref, nullRef).not()
                state.pathConstraints += mkIsExpr(ref, typedMethod.enclosingType)
            }
        }

        typedMethod.parameters.forEachIndexed { idx, typedParameter ->
            with(ctx) {
                val type = typedParameter.type
                if (type is JcRefType) {
                    val argumentLValue = URegisterLValue(typeToSort(type), method.localIdx(idx))
                    val ref = state.memory.read(argumentLValue).asExpr(addressSort)
                    state.pathConstraints += mkIsExpr(ref, type)
                }
            }
        }

        val solver = ctx.solver<JcField, JcType, JcMethod>()

        val model = (solver.check(state.pathConstraints, useSoftConstraints = true) as USatResult).model
        state.models = listOf(model)

        return state
    }

    override fun step(state: JcState): StepResult<JcState> {
        val stmt = state.lastStmt
        val scope = StepScope(state)

        // handle exception firstly
        val result = state.methodResult
        if (result is JcMethodResult.Exception) {
            handleException(scope, result.exception, stmt)
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
        exception: Exception,
        lastStmt: JcInst,
    ) {
        applicationGraph.successors(lastStmt) // TODO: check catchers for lastStmt

        scope.calcOnState { throwException(exception) }
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
        TODO("Not yet implemented")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun visitSwitchStmt(scope: JcStepScope, stmt: JcSwitchInst) {
        TODO("Not yet implemented")
    }

    private fun visitThrowStmt(scope: JcStepScope, stmt: JcThrowInst) {
        scope.calcOnState {
            throwException(WrappedException(stmt.throwable.type.typeName))
        }
    }

    private fun visitCallStmt(scope: JcStepScope, stmt: JcCallInst) {
        val exprResolver = exprResolverWithScope(scope)

        val result = requireNotNull(scope.calcOnState { methodResult })

        when (result) {
            JcMethodResult.NoCall -> {
                exprResolver.resolveJcExpr(stmt.callExpr)
            }

            is JcMethodResult.Success -> {
                val nextStmt = stmt.nextStmt
                scope.doWithState {
                    methodResult = JcMethodResult.NoCall
                    newStmt(nextStmt)
                } ?: return
            }

            is JcMethodResult.Exception -> error("Exception should be handled earlier")
        }
    }

    private fun exprResolverWithScope(scope: JcStepScope) =
        JcExprResolver(ctx, scope, applicationGraph, ::mapLocalToIdxMapper)

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
}
