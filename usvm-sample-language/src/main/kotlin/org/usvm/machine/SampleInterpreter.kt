package org.usvm.machine

import mu.KLogging
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UContext
import org.usvm.UInterpreter
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.language.Call
import org.usvm.language.Goto
import org.usvm.language.If
import org.usvm.language.Return
import org.usvm.language.SampleType
import org.usvm.language.SetLabel
import org.usvm.language.SetValue
import org.usvm.language.Stmt

typealias SampleStepScope = StepScope<SampleState, SampleType, Stmt, UContext<USizeSort>>


val logger = object : KLogging() {}.logger
/**
 * Symbolic interpreter for a sample language.
 */
class SampleInterpreter(
    private val ctx: UContext<USizeSort>,
    private val applicationGraph: SampleApplicationGraph,
    private val forkBlackList: UForkBlackList<SampleState, Stmt> = UForkBlackList.createDefault()
) : UInterpreter<SampleState>() {

    /**
     * Interpreters a single step inside a symbolic [state].
     *
     * @return next states.
     */
    override fun step(state: SampleState): StepResult<SampleState> {
        val scope = StepScope(state, forkBlackList)
        val stmt = state.lastStmt
        logger.debug { "state: $state" }
        logger.debug { "step: $stmt" }
        when (stmt) {
            is Call -> visitCall(scope, stmt)
            is Goto -> visitGoto(scope, stmt)
            is If -> visitIf(scope, stmt)
            is Return -> visitReturn(scope, stmt)
            is SetLabel -> visitSetLabel(scope, stmt)
            is SetValue -> visitSetValue(scope, stmt)
        }
        return scope.stepResult()
    }

    private fun visitCall(scope: SampleStepScope, stmt: Call) {
        val exprResolver = SampleExprResolver(ctx, scope)

        val retRegister = scope.calcOnState {
            val retRegister = returnRegister
            returnRegister = null
            retRegister
        }

        when (retRegister) {
            null -> {
                val resolvedArgs = stmt.args.map { exprResolver.resolveExpr(it) ?: return }
                scope.doWithState { addNewMethodCall(applicationGraph, stmt.method, resolvedArgs) }
            }

            else -> {
                val lvalue = stmt.lvalue
                if (lvalue != null) {
                    val lvalueExpr = exprResolver.resolveLValue(lvalue) ?: return
                    scope.doWithState { memory.write(lvalueExpr, retRegister) }
                }

                val nextStmt = applicationGraph.successors(stmt).single()
                scope.doWithState { newStmt(nextStmt) }
            }
        }
    }

    private fun visitGoto(scope: SampleStepScope, stmt: Goto) {
        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState { newStmt(nextStmt) }
    }

    private fun visitIf(scope: SampleStepScope, stmt: If) {
        val exprResolver = SampleExprResolver(ctx, scope)

        val boolExpr = exprResolver.resolveBoolean(stmt.condition) ?: return

        val (posStmt, negStmt) = applicationGraph.successors(stmt).run { take(2).toList() }

        scope.fork(
            boolExpr,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
    }

    private fun visitReturn(scope: SampleStepScope, stmt: Return) {
        val exprResolver = SampleExprResolver(ctx, scope)

        val valueToReturn = stmt.valueToReturn?.let { exprResolver.resolveExpr(it) }

        scope.doWithState {
            popMethodCall(valueToReturn)
        }
    }

    private fun visitSetLabel(scope: SampleStepScope, stmt: SetLabel) {
        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState { newStmt(nextStmt) }
    }

    private fun visitSetValue(scope: SampleStepScope, stmt: SetValue) {
        val exprResolver = SampleExprResolver(ctx, scope)
        val lvalue = exprResolver.resolveLValue(stmt.lvalue) ?: return
        val expr = exprResolver.resolveExpr(stmt.expr) ?: return

        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState {
            memory.write(lvalue, expr)
            newStmt(nextStmt)
        }
    }
}
