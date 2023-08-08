package org.usvm.machine.interpreters.operations

import io.ksmt.sort.KBoolSort
import org.usvm.UExpr
import org.usvm.fork
import org.usvm.forkMulti
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.DelayedFork
import org.usvm.machine.PythonExecutionState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.PythonPinnedCallable

fun myFork(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    if (ctx.curState == null)
        return
    val model = ctx.curState!!.pyModel
    val oldCurState = ctx.curState
    val forkResult = fork(ctx.curState!!, cond)
    if (forkResult.positiveState?.pyModel == model) {
        ctx.curState = forkResult.positiveState
    } else if (forkResult.negativeState?.pyModel == model) {
        ctx.curState = forkResult.negativeState
    } else {
        error("Should not be reachable")
    }
    if (forkResult.negativeState != oldCurState)
        forkResult.negativeState?.let {
            ctx.forkedStates.add(it)
            it.meta.generatedFrom = "From ordinary fork"
        }
}

fun myAssertOnState(state: PythonExecutionState, cond: UExpr<KBoolSort>): PythonExecutionState? {
    val forkResult = forkMulti(state, listOf(cond)).single()
    if (forkResult != null)
        require(forkResult == state)

    return forkResult
}

fun myAssert(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    if (ctx.curState == null)
        return
    val oldModel = ctx.curState!!.pyModel
    val forkResult = myAssertOnState(ctx.curState!!, cond)
    if (forkResult == null)
        ctx.curState!!.meta.modelDied = true
    if (forkResult?.pyModel != oldModel)
        throw BadModelException
}

fun addDelayedFork(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject, clonedState: PythonExecutionState) {
    if (context.curState == null)
        return
    context.curState!!.delayedForks = context.curState!!.delayedForks.add(
        DelayedFork(
            clonedState,
            on,
            clonedState.pyModel.uModel.typeStreamOf(clonedState.pyModel.eval(on.address)),
            context.curState!!.delayedForks
        )
    )
}

fun handlerForkKt(ctx: ConcolicRunContext, cond: UninterpretedSymbolicPythonObject) {
    if (ctx.curState == null)
        return
    if (cond.getTypeIfDefined(ctx) == null) {
        addDelayedFork(ctx, cond, ctx.curState!!.clone())
    }
    val expr = cond.getToBoolValue(ctx) ?: return
    myFork(ctx, expr)
}

@Suppress("unused_parameter")
fun handlerFunctionCallKt(ctx: ConcolicRunContext, function: PythonPinnedCallable) {
    // (ctx.curState ?: return).callStack.push(function, (ctx.curState ?: return).lastHandlerEvent)
}

@Suppress("unused_parameter")
fun handlerReturnKt(ctx: ConcolicRunContext) {
    // (ctx.curState ?: return).callStack.pop()
}

object BadModelException: Exception()