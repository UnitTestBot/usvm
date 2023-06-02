package org.usvm.interpreter

import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr

fun handlerForkResultKt(ctx: UContext, openedExpr: UBoolExpr?, stepScope: PythonStepScope, result: Boolean) {
    openedExpr ?: return
    with (ctx) {
        val cond = if (!result) mkNot(openedExpr) else openedExpr
        println("Fork on $cond")
        System.out.flush()
        stepScope.fork(
            cond,
            blockOnFalseState = { println("Neg state created"); System.out.flush() }
        )
    }
}

@Suppress("unchecked_cast")
fun handlerGTLongKt(ctx: UContext, left: UExpr<*>, right: UExpr<*>): UBoolExpr? {
    with (ctx) {
        if (left.sort != intSort || right.sort != intSort)
            return null
        return (left as UExpr<KIntSort>) gt (right as UExpr<KIntSort>)
    }
}