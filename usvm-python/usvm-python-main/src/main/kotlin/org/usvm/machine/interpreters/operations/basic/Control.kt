package org.usvm.machine.interpreters.operations.basic

import io.ksmt.sort.KBoolSort
import org.usvm.UExpr
import org.usvm.fork
import org.usvm.forkMulti
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.DelayedFork
import org.usvm.machine.PythonExecutionState
import org.usvm.machine.model.PyModel
import org.usvm.machine.model.toPyModel
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.getToBoolValue
import org.usvm.machine.utils.getTypeStreamForDelayedFork

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
    val applyToPyModel = { state: PythonExecutionState ->
        state.models = listOf(state.pyModel.uModel.toPyModel(ctx.ctx, ctx.typeSystem))
    }
    forkResult.positiveState?.let(applyToPyModel)
    forkResult.negativeState?.let(applyToPyModel)
    forkResult.positiveState?.also { it.meta.generatedFrom = "From ordinary fork" }
    forkResult.negativeState?.also { it.meta.generatedFrom = "From ordinary fork" }
    if (forkResult.negativeState != oldCurState)
        forkResult.negativeState?.let {
            ctx.forkedStates.add(it)
        }
}

fun myAssertOnState(state: PythonExecutionState, cond: UExpr<KBoolSort>): PythonExecutionState? {
    val forkResult = forkMulti(state, listOf(cond)).single()
    if (forkResult != null) {
        require(forkResult == state)
        forkResult.models = listOf(forkResult.pyModel.uModel.toPyModel(state.ctx, state.typeSystem))
    }

    return forkResult
}

fun myAssert(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    if (ctx.curState == null)
        return
    val oldModel = ctx.curState!!.pyModel
    require(oldModel.uModel is PyModel)
    val forkResult = myAssertOnState(ctx.curState!!, cond)
    if (forkResult == null)
        ctx.curState!!.meta.modelDied = true
    if (forkResult?.pyModel != oldModel)
        throw BadModelException
}

fun addDelayedFork(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject, clonedState: PythonExecutionState) {
    if (ctx.curState == null)
        return
    ctx.curState!!.delayedForks = ctx.curState!!.delayedForks.add(
        DelayedFork(
            clonedState,
            on,
            getTypeStreamForDelayedFork(on, ctx),
            ctx.curState!!.delayedForks
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

object BadModelException: Exception()