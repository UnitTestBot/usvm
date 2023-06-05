package org.usvm.interpreter.operations

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.fork
import org.usvm.interpreter.ConcolicRunContext

fun handlerForkKt(ctx: ConcolicRunContext, cond: UExpr<*>?) {
    cond ?: return
    with (ctx.ctx) {
        if (cond.sort != boolSort)
            return
        val model = ctx.curState.models.first()
        val oldCurState = ctx.curState
        @Suppress("unchecked_cast")
        val forkResult = fork(ctx.curState, cond as UBoolExpr)
        if (forkResult.positiveState?.models?.first() == model) {
            ctx.curState = forkResult.positiveState
        } else if (forkResult.negativeState?.models?.first() == model) {
            ctx.curState = forkResult.negativeState
        } else {
            error("Should not be reachable")
        }
        if (forkResult.negativeState != oldCurState)
            forkResult.negativeState?.let { ctx.forkedStates.add(it) }
    }
}