package org.usvm

import io.ksmt.utils.asExpr
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
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
import org.usvm.state.JcMethodResult
import org.usvm.state.JcState
import org.usvm.state.WrappedException
import org.usvm.state.addEntryMethodCall
import org.usvm.state.lastStmt
import org.usvm.state.newStmt
import org.usvm.state.returnValue
import org.usvm.state.throwException

typealias JcStepScope = StepScope<JcState, JcType, JcTypedField>

@Suppress("UNUSED_PARAMETER")
class JcInterpreter(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
) : UInterpreter<JcState>() {

    fun getInitialState(method: JcTypedMethod): JcState {
        val solver = ctx.solver<JcTypedField, JcType, JcTypedMethod>()
        val model = solver.emptyModel()

        val state = JcState(
            ctx,
            models = listOf(model)
        )
        state.addEntryMethodCall(applicationGraph, method)

        val scope = StepScope(state)
        if (!method.isStatic) {
            with(ctx) {
                val thisLValue = URegisterValue(addressSort, 0)

                val ref = state.memory.read(thisLValue).asExpr(addressSort)

                scope.assert(mkEq(ref, nullRef).not())
//                val isConstraint = state.memory.types.evalIs(ref, method.enclosingType)
//                scope.assert(isConstraint)
            }
        }

//        with(ctx) {
//            method.parameters.withIndex().forEach { (idx, param) ->
//                val type = param.type
//                val lvalue = URegisterValue(addressSort, idx + if (!method.isStatic) 1 else 0)
//                val ref = state.memory.read(lvalue)
//                if (ref.sort == addressSort) {
//                    val isConstraint = state.memory.types.evalIs(ref.asExpr(addressSort), type)
//                    scope.assert(isConstraint)
//                }
//            }
//        }

        check(scope.alive)

        return state
    }


    private val localVarToIdx = mutableMapOf<JcMethod, MutableMap<String, Int>>() // (method, localName) -> idx

    override fun step(state: JcState): StepResult<JcState> {
        val stmt = state.lastStmt
        val scope = StepScope(state)
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
        scope: StepScope<JcState, JcType, JcTypedField>,
        exception: Exception,
        lastStmt: JcInst,
    ) {
        applicationGraph.successors(lastStmt) // TODO: check catchers for lastStmt
        scope.calcOnState { throwException(exception) }
    }

    private fun visitAssignInst(scope: JcStepScope, stmt: JcAssignInst) {
        val exprResolver = JcExprResolver(ctx, scope, applicationGraph, ::mapLocalToIdxMapper)
        val lvalue = exprResolver.resolveLValue(stmt.lhv) ?: return
        val expr = exprResolver.resolveExpr(stmt.rhv) ?: return

        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState {
            memory.write(lvalue, expr)
            newStmt(nextStmt)
        }
    }

    private fun visitIfStmt(scope: JcStepScope, stmt: JcIfInst) {
        val exprResolver = JcExprResolver(ctx, scope, applicationGraph, ::mapLocalToIdxMapper)

        val boolExpr = with(ctx) {
            exprResolver
                .resolveExpr(stmt.condition)
                ?.asExpr(boolSort)
                ?: return
        }

        val (posStmt, negStmt) = applicationGraph.successors(stmt).run { take(2).toList() }

        scope.fork(
            boolExpr,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
    }

    private fun visitReturnStmt(scope: JcStepScope, stmt: JcReturnInst) {
        val exprResolver = JcExprResolver(ctx, scope, applicationGraph, ::mapLocalToIdxMapper)

        val valueToReturn = stmt.returnValue?.let { exprResolver.resolveExpr(it) ?: return } ?: ctx.mkVoidValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitGotoStmt(scope: JcStepScope, stmt: JcGotoInst) {
        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState { newStmt(nextStmt) }
    }

    private fun visitCatchStmt(scope: JcStepScope, stmt: JcCatchInst) {
        TODO("Not yet implemented")
    }

    private fun visitSwitchStmt(scope: JcStepScope, stmt: JcSwitchInst) {
        TODO("Not yet implemented")
    }

    private fun visitThrowStmt(scope: JcStepScope, stmt: JcThrowInst) {
        scope.calcOnState {
            throwException(WrappedException(stmt.throwable.type.typeName))
        }
    }

    private fun visitCallStmt(scope: JcStepScope, stmt: JcCallInst) {
        val exprResolver = JcExprResolver(ctx, scope, applicationGraph, ::mapLocalToIdxMapper)

        val result = requireNotNull(scope.calcOnState { methodResult })

        when (result) {
            JcMethodResult.NoCall -> {
                exprResolver.resolveExpr(stmt.callExpr)
            }

            is JcMethodResult.Success -> {
                val nextStmt = applicationGraph.successors(stmt).single()
                scope.doWithState {
                    methodResult = JcMethodResult.NoCall
                    newStmt(nextStmt)
                } ?: return
            }

            is JcMethodResult.Exception -> {

            }
        }
    }

    private fun mapLocalToIdxMapper(method: JcTypedMethod, local: JcLocal) =
        when (local) {
            is JcLocalVar -> localVarToIdx
                .getOrPut(method.method) { mutableMapOf() }
                .run {
                    getOrPut(local.name) { method.parameters.size + size + if (method.isStatic) 0 else 1 }
                }

            is JcThis -> 0
            is JcArgument -> local.index + if (method.isStatic) 0 else 1
            else -> error("Unexpected local: $local")
        }
}