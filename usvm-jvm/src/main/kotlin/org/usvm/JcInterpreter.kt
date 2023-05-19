package org.usvm

import io.ksmt.utils.asExpr
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcThrowInst
import org.usvm.state.JcState
import org.usvm.state.lastStmt
import org.usvm.state.newStmt
import org.usvm.state.returnValue

typealias JcStepScope = StepScope<JcState, JcType>

@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
class JcInterpreter(
    private val cp: JcClasspath,
    private val ctx: UContext,
    private val applicationGraph: JcApplicationGraph,
) : UInterpreter<JcState>() {
    override fun step(state: JcState): StepResult<JcState> {
        val stmt = state.lastStmt
        val scope = StepScope(ctx, state)
        when (stmt) {
            is JcAssignInst -> visitAssignInst(scope, stmt)
            is JcIfInst -> visitIfStmt(scope, stmt)
            is JcReturnInst -> visitReturnStmt(scope, stmt)
            is JcGotoInst -> visitGotoStmt(scope, stmt)
            is JcCatchInst -> visitCatchStmt(scope, stmt)
            is JcSwitchInst -> visitSwitchStmt(scope, stmt)
            is JcThrowInst -> visitThrowStmt(scope, stmt)
            is JcCallInst -> visitCallStmt(scope, stmt)
        }
        return scope.stepResult()
    }

    private fun visitAssignInst(scope: JcStepScope, stmt: JcAssignInst) {
        val exprResolver = JcExprResolver(cp, scope)
        val lvalue = exprResolver.resolveLValue(stmt.lhv) ?: return
        val expr = exprResolver.resolveExpr(stmt.rhv) ?: return

        val nextStmt = applicationGraph.successors(stmt).single()
        scope.doWithState {
            memory.write(lvalue, expr)
            newStmt(nextStmt)
        }
    }

    private fun visitIfStmt(scope: JcStepScope, stmt: JcIfInst) {
        val exprResolver = JcExprResolver(cp, scope)

        val boolExpr = with(ctx) {
            exprResolver
                .resolveExpr(stmt.condition)
                ?.asExpr(bv32Sort)
                ?.let { mkEq(it, mkBv(0)) } ?: return
        }

        val (posStmt, negStmt) = applicationGraph.successors(stmt).run { take(2).toList() }

        scope.fork(
            boolExpr,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
    }

    private fun visitReturnStmt(scope: JcStepScope, stmt: JcReturnInst) {
        val exprResolver = JcExprResolver(cp, scope)

        val valueToReturn = stmt.returnValue?.let { exprResolver.resolveExpr(it) }

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
        TODO("Not yet implemented")
    }

    private fun visitCallStmt(scope: JcStepScope, stmt: JcCallInst) {
        TODO("Not yet implemented")
    }
}