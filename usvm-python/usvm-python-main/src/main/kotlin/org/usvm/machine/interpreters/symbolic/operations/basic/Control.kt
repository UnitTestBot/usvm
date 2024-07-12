package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KBoolSort
import org.usvm.UExpr
import org.usvm.WithSolverStateForker.fork
import org.usvm.WithSolverStateForker.forkMulti
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.model.toPyModel
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.getToBoolValue
import org.usvm.machine.utils.getTypeStreamForDelayedFork

fun myFork(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    if (ctx.curState == null)
        return
    val model = ctx.curState!!.pyModel
    val oldCurState = ctx.curState
    val forkResult = fork(ctx.curState!!, cond)
    when (model) {
        forkResult.positiveState?.models?.first() -> ctx.curState = forkResult.positiveState
        forkResult.negativeState?.models?.first() -> ctx.curState = forkResult.negativeState
        else -> error("Should not be reachable")
    }
    ctx.builder.state = ctx.curState!!
    val applyToPyModel = { state: PyState ->
        state.models = listOf(state.models.first().toPyModel(ctx.ctx, state.pathConstraints))
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

fun myAssertOnState(state: PyState, cond: UExpr<KBoolSort>): PyState? {
    val forkResult = forkMulti(state, listOf(cond)).single()
    if (forkResult != null) {
        require(forkResult == state)
        forkResult.models = listOf(forkResult.models.first().toPyModel(state.ctx, state.pathConstraints))
    }

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

fun addDelayedFork(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject, clonedState: PyState) {
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

object BadModelException: Exception() {
    private fun readResolve(): Any = BadModelException
}