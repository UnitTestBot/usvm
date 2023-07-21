package org.usvm.interpreter.operations

import io.ksmt.sort.KBoolSort
import org.usvm.UExpr
import org.usvm.fork
import org.usvm.forkMulti
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.SymbolicPythonObject
import org.usvm.isTrue
import org.usvm.language.PythonPinnedCallable

fun myFork(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    val model = ctx.curState.pyModel
    val oldCurState = ctx.curState
    val forkResult = fork(ctx.curState, cond)
    if (forkResult.positiveState?.pyModel == model) {
        ctx.curState = forkResult.positiveState
    } else if (forkResult.negativeState?.pyModel == model) {
        ctx.curState = forkResult.negativeState
    } else {
        error("Should not be reachable")
    }
    if (forkResult.negativeState != oldCurState)
        forkResult.negativeState?.let { ctx.forkedStates.add(it) }
}

fun myAssert(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    val forkResult = forkMulti(ctx.curState, listOf(cond)).single()
    if (forkResult == null) {
        ctx.curState.modelDied = true
        throw BadModelException
    }
}

fun handlerForkKt(ctx: ConcolicRunContext, cond: SymbolicPythonObject?) {
    cond ?: return
    val expr = cond.getBoolContent(ctx)
    myFork(ctx, expr)
}

fun handlerFunctionCallKt(ctx: ConcolicRunContext, function: PythonPinnedCallable) {
    ctx.curState.callStack.push(function, ctx.curState.lastHandlerEvent)
}

fun handlerReturnKt(ctx: ConcolicRunContext) {
    ctx.curState.callStack.pop()
}

object BadModelException: Exception()