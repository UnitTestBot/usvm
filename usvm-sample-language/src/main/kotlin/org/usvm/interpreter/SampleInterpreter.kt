package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.UInterpreter
import org.usvm.language.Call
import org.usvm.language.Goto
import org.usvm.language.If
import org.usvm.language.Return
import org.usvm.language.SampleType
import org.usvm.language.SetLabel
import org.usvm.language.SetValue
import org.usvm.lastStmt

typealias SampleStepScope = StepScope<SampleState, SampleType>

/**
 * Symbolic interpreter for a sample language.
 */
context(SampleStateOperations)
class SampleInterpreter(
    private val ctx: UContext,
) : UInterpreter<SampleState>() {

    /**
     * Interpreters a single step inside a symbolic [state].
     *
     * @return next states.
     */
    override fun step(state: SampleState): StepResult<SampleState> {
        val scope = SampleStepScope(ctx, state)
        when (val stmt = state.lastStmt) {
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
        val exprResolver = ExprResolver(scope)

        scope.processCall(
            onNoCall = {
                val resolvedArgs = stmt.args.map { exprResolver.resolveExpr(it) ?: return }
                scope.doWithState { callMethod(stmt.method, resolvedArgs) } ?: return
            },
            onSuccess = { returnedValue ->
                val lvalue = stmt.lvalue
                if (lvalue != null) {
                    requireNotNull(returnedValue)
                    val lvalueExpr = exprResolver.resolveLValue(lvalue) ?: return
                    scope.doWithState { memory.write(lvalueExpr, returnedValue) }
                }

                val nextStmt = successors(stmt).single()
                scope.doWithState { newStmt(nextStmt) } ?: return
            }
        )
    }

    private fun visitGoto(scope: SampleStepScope, stmt: Goto) {
        val nextStmt = successors(stmt).single()
        scope.doWithState { newStmt(nextStmt) }
    }

    private fun visitIf(scope: SampleStepScope, stmt: If) {
        val exprResolver = ExprResolver(scope)

        val boolExpr = exprResolver.resolveBoolean(stmt.condition) ?: return

        val (posStmt, negStmt) = successors(stmt).take(2).toList()

        scope.fork(
            boolExpr,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
    }

    private fun visitReturn(scope: SampleStepScope, stmt: Return) {
        val exprResolver = ExprResolver(scope)

        val valueToReturn = stmt.valueToReturn?.let { exprResolver.resolveExpr(it) }

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitSetLabel(scope: SampleStepScope, stmt: SetLabel) {
        val nextStmt = successors(stmt).single()
        scope.doWithState { newStmt(nextStmt) }
    }

    private fun visitSetValue(scope: SampleStepScope, stmt: SetValue) {
        val exprResolver = ExprResolver(scope)
        val lvalue = exprResolver.resolveLValue(stmt.lvalue) ?: return
        val expr = exprResolver.resolveExpr(stmt.expr) ?: return

        val nextStmt = successors(stmt).single()
        scope.doWithState {
            memory.write(lvalue, expr)
            newStmt(nextStmt)
        }
    }
}