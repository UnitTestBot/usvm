package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import org.usvm.machine.TsContext
import org.usvm.machine.TsEntryPointGuardResultStmt
import org.usvm.machine.state.TsEntryPointGuardOutcome
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.newStmt

/** Completes the precondition before entering the ordinary source predicate. */
internal fun TsContext.visitEntryPointGuardResult(
    scope: TsStepScope,
    stmt: TsEntryPointGuardResultStmt,
) = with(this) {
    val result = scope.calcOnState { methodResult }
    if (result !is TsMethodResult.Success) {
        scope.doWithState {
            entryPointGuardActive = false
            entryPointGuardOutcome = TsEntryPointGuardOutcome.ERROR
            callStack.pop()
        }
        return@with
    }

    val guard = result.value.takeIf { it.sort == boolSort }?.asExpr(boolSort)
    if (guard == null) {
        scope.doWithState {
            entryPointGuardActive = false
            entryPointGuardOutcome = TsEntryPointGuardOutcome.ERROR
            callStack.pop()
        }
        return@with
    }

    scope.fork(
        condition = guard,
        blockOnTrueState = {
            methodResult = TsMethodResult.NoCall
            entryPointGuardActive = false
            entryPointGuardOutcome = TsEntryPointGuardOutcome.NONE
            newStmt(stmt.entryPoint)
        },
        blockOnFalseState = {
            entryPointGuardActive = false
            entryPointGuardOutcome = TsEntryPointGuardOutcome.REJECTED
            callStack.pop()
        },
    )
}
