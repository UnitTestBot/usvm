package org.usvm.concrete.interpreter

import org.usvm.*
import org.usvm.concrete.SampleApplicationGraph
import org.usvm.concrete.state.ExecutionState
import org.usvm.concrete.state.addNewMethodCall
import org.usvm.concrete.state.addNewStmt
import org.usvm.concrete.state.popMethodCall
import org.usvm.language.*

class SampleInterpreter(
    private val ctx: UContext,
    private val applicationGraph: SampleApplicationGraph,
    private val solver: USolverBase<Field<*>, SampleType, Method<*>>,
) {
    private val checker: (UMemoryBase<Field<*>, SampleType, Method<*>>, UPathCondition) -> UModel? = { memory, pc ->
        val solverResult = solver.check(
            memory,
            pc
        )
        (solverResult as? USolverSat<UModelBase<Field<*>, SampleType>>)?.model
    }

    fun step(state: ExecutionState): Collection<ExecutionState> {
        val stmt = state.path.last()
        val scope = StepScope(ctx, state, checker)
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