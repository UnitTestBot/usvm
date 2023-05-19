package org.usvm

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
import org.usvm.interpreter.StepResult
import org.usvm.interpreter.StepScope

typealias JcStepScope = StepScope<JcState, JcType>

@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
class JcInterpreter(
    private val cp: JcClasspath,
    private val ctx: UContext,
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
        val lValue = exprResolver.resolveLValue(stmt.lhv) ?: return
    }

    private fun visitIfStmt(scope: JcStepScope, stmt: JcIfInst) {
        TODO("Not yet implemented")
    }

    private fun visitReturnStmt(scope: JcStepScope, stmt: JcReturnInst) {
        TODO("Not yet implemented")
    }

    private fun visitGotoStmt(scope: JcStepScope, stmt: JcGotoInst) {
        TODO("Not yet implemented")
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