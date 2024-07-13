package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KBoolSort
import org.usvm.UExpr
import org.usvm.WithSolverStateForker.fork
import org.usvm.WithSolverStateForker.forkMulti
import org.usvm.machine.BadModelException
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.extractCurState
import org.usvm.machine.model.GenerateNewFromPathConstraints
import org.usvm.machine.model.toPyModel
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.getToBoolValue
import org.usvm.machine.utils.getTypeStreamForDelayedFork

fun pyFork(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    if (ctx.curState == null) {
        return
    }
    val model = ctx.extractCurState().pyModel
    val oldCurState = ctx.curState
    val forkResult = fork(ctx.extractCurState(), cond)
    when (model) {
        forkResult.positiveState?.models?.first() -> ctx.curState = forkResult.positiveState
        forkResult.negativeState?.models?.first() -> ctx.curState = forkResult.negativeState
        else -> error("Should not be reachable")
    }
    ctx.builder.state = ctx.extractCurState()
    val applyToPyModel = { state: PyState ->
        state.models = listOf(
            state.models.first().toPyModel(ctx.ctx, GenerateNewFromPathConstraints(state.pathConstraints))
        )
    }
    forkResult.positiveState?.let(applyToPyModel)
    forkResult.negativeState?.let(applyToPyModel)
    forkResult.positiveState?.also { it.generatedFrom = "From ordinary fork" }
    forkResult.negativeState?.also { it.generatedFrom = "From ordinary fork" }
    if (forkResult.negativeState != oldCurState) {
        forkResult.negativeState?.let {
            ctx.forkedStates.add(it)
        }
    }
}

fun pyAssertOnState(state: PyState, cond: UExpr<KBoolSort>): PyState? {
    val forkResult = forkMulti(state, listOf(cond)).single()
    if (forkResult != null) {
        require(forkResult == state)
        forkResult.models = listOf(
            forkResult.models.first().toPyModel(state.ctx, GenerateNewFromPathConstraints(state.pathConstraints))
        )
    }

    return forkResult
}

fun pyAssert(ctx: ConcolicRunContext, cond: UExpr<KBoolSort>) {
    if (ctx.curState == null) {
        return
    }
    val oldModel = ctx.extractCurState().pyModel
    val forkResult = pyAssertOnState(ctx.extractCurState(), cond)
    if (forkResult == null) {
        ctx.extractCurState().modelDied = true
    }
    if (forkResult?.pyModel != oldModel) {
        throw BadModelException()
    }
}

fun addDelayedFork(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject, clonedState: PyState) {
    if (ctx.curState == null) {
        return
    }
    ctx.extractCurState().delayedForks = ctx.extractCurState().delayedForks.add(
        DelayedFork(
            clonedState,
            on,
            getTypeStreamForDelayedFork(on, ctx),
            ctx.extractCurState().delayedForks
        )
    )
}

fun handlerForkKt(ctx: ConcolicRunContext, cond: UninterpretedSymbolicPythonObject) {
    if (ctx.curState == null) {
        return
    }
    if (cond.getTypeIfDefined(ctx) == null) {
        addDelayedFork(ctx, cond, ctx.extractCurState().clone())
    }
    val expr = cond.getToBoolValue(ctx) ?: return
    pyFork(ctx, expr)
}
