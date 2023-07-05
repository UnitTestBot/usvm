package org.usvm.interpreter.operations

import org.usvm.fork
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.SymbolicPythonObject
import org.usvm.language.PythonPinnedCallable
import org.usvm.language.pythonBool

fun handlerForkKt(ctx: ConcolicRunContext, cond: SymbolicPythonObject?) {
    cond ?: return
    if (cond.concreteType != pythonBool)
        return
    val expr = cond.getBoolContent()
    val model = ctx.curState.pyModel
    val oldCurState = ctx.curState
    val forkResult = fork(ctx.curState, expr)
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

fun handlerFunctionCallKt(ctx: ConcolicRunContext, function: PythonPinnedCallable): Int {  // return value for Java call
    ctx.curState.callStack.push(function, ctx.curState.lastHandlerEvent)
    return 0
}

fun handlerReturnKt(ctx: ConcolicRunContext): Int {  // return value for Java call
    ctx.curState.callStack.pop()
    return 0
}