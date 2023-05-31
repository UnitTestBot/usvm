package org.usvm.interpreter

import org.usvm.UBoolExpr
import org.usvm.UContext

fun handlerForkResultKt(ctx: UContext, openedExpr: UBoolExpr?, stepScope: PythonStepScope, result: Boolean) {
    openedExpr ?: return
    with (ctx) {
        val cond = if (!result) mkNot(openedExpr) else openedExpr
        stepScope.fork(cond)
    }
}