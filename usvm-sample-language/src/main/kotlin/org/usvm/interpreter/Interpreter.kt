package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.UMemoryBase
import org.usvm.UModel
import org.usvm.UModelBase
import org.usvm.UPathCondition
import org.usvm.USolverBase
import org.usvm.USatResult
import org.usvm.language.Call
import org.usvm.language.Field
import org.usvm.language.Goto
import org.usvm.language.If
import org.usvm.language.Method
import org.usvm.language.Return
import org.usvm.language.SampleType
import org.usvm.language.SetLabel
import org.usvm.language.SetValue
import org.usvm.language.Stmt

/**
 * Symbolic interpreter for a sample language.
 */
class SampleInterpreter(
    private val ctx: UContext,
    private val applicationGraph: SampleApplicationGraph,
    private val solver: USolverBase<Field<*>, SampleType, Method<*>>,
) {
    private val findModel: (UMemoryBase<Field<*>, SampleType, Method<*>>, UPathCondition) -> UModel? = { memory, pc ->
        val solverResult = solver.check(memory, pc)
        (solverResult as? USatResult<UModelBase<Field<*>, SampleType>>)?.model
    }

    /**
     * Interpreters a single step inside a symbolic [state].
     *
     * @return next states.
     */
    fun step(state: ExecutionState): Collection<ExecutionState> {
        val stmt = state.lastStmt
        val scope = StepScope(ctx, state, findModel)
        step(scope, stmt)
        val newStates = scope.allStates()
        return newStates
    }

    private fun step(scope: StepScope, stmt: Stmt) {
        when (stmt) {
            is Call -> visitCall(scope, stmt)
            is Goto -> visitGoto(scope, stmt)
            is If -> visitIf(scope, stmt)
            is Return -> visitReturn(scope, stmt)
            is SetLabel -> visitSetLabel(scope, stmt)
            is SetValue -> visitSetValue(scope, stmt)
        }
    }

    private fun visitCall(scope: StepScope, stmt: Call) {
        val exprResolver = ExprResolver(scope)

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
                scope.doWithState { addNewStmt(nextStmt) }
            }
        }
    }

    private fun visitGoto(scope: StepScope, stmt: Goto) {
        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState { addNewStmt(nextStmt) }
    }

    private fun visitIf(scope: StepScope, stmt: If) {
        val exprResolver = ExprResolver(scope)

        val boolExpr = exprResolver.resolveBoolean(stmt.condition) ?: return

        val (posStmt, negStmt) = applicationGraph.successors(stmt).run { take(2).toList() }

        scope.fork(
            boolExpr,
            blockOnTrueState = { addNewStmt(posStmt) },
            blockOnFalseState = { addNewStmt(negStmt) }
        )
    }

    private fun visitReturn(scope: StepScope, stmt: Return) {
        val exprResolver = ExprResolver(scope)

        val valueToReturn = stmt.valueToReturn?.let { exprResolver.resolveExpr(it) }

        scope.doWithState {
            popMethodCall(valueToReturn)
        }
    }

    private fun visitSetLabel(scope: StepScope, stmt: SetLabel) {
        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState { addNewStmt(nextStmt) }
    }

    private fun visitSetValue(scope: StepScope, stmt: SetValue) {
        val exprResolver = ExprResolver(scope)
        val lvalue = exprResolver.resolveLValue(stmt.lvalue) ?: return
        val expr = exprResolver.resolveExpr(stmt.expr) ?: return

        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState {
            memory.write(lvalue, expr)
            addNewStmt(nextStmt)
        }
    }
}