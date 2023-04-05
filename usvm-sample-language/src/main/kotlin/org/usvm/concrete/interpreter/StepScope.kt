package org.usvm.concrete.interpreter

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UMemoryBase
import org.usvm.UModel
import org.usvm.UPathCondition
import org.usvm.concrete.state.ExecutionState
import org.usvm.fork
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.SampleType

class StepScope(
    val uctx: UContext,
    initialState: ExecutionState,
    private val checker: (UMemoryBase<Field<*>, SampleType, Method<*>>, UPathCondition) -> UModel?,
) {
    private val accumulatedStates = mutableListOf<ExecutionState>()
    private var curState: ExecutionState? = initialState

    fun forkedStates(): List<ExecutionState> = accumulatedStates

    fun allStates() = forkedStates() + listOfNotNull(curState)

    fun doWithState(block: ExecutionState.() -> Unit): Unit? {
        val state = curState ?: return null
        state.block()
        return Unit
    }

    fun <T> calcOnState(block: ExecutionState.() -> T): T? {
        val state = curState ?: return null
        return state.block()
    }

    fun fork(
        condition: UBoolExpr,
        blockOnTrueState: ExecutionState.() -> Unit = {},
        blockOnFalseState: ExecutionState.() -> Unit = {},
    ): Unit? {
        val state = curState ?: return null

        val (posState, negState) = state.fork(condition, checker)

        posState?.blockOnTrueState()
        curState = posState

        if (negState != null) {
            negState.blockOnFalseState()
            accumulatedStates += negState
        }

        // conversion of ExecutionState? to Unit?
        return posState?.let { }
    }
}
