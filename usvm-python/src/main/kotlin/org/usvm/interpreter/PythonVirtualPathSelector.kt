package org.usvm.interpreter

import org.usvm.UPathSelector
import org.usvm.fork
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.language.types.PythonType
import org.usvm.types.UTypeStream
import org.usvm.types.takeFirst

class PythonVirtualPathSelector(
    private val basePathSelector: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithDelayedForks: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithConcretizedTypes: UPathSelector<PythonExecutionState>
) : UPathSelector<PythonExecutionState> {
    private val delayedForks = mutableSetOf<DelayedForkWithTypeRating>()
    private val executionsWithVirtualObjectAndWithoutDelayedForks = mutableSetOf<PythonExecutionState>()

    override fun isEmpty(): Boolean {
        return basePathSelector.isEmpty() && delayedForks.isEmpty() && pathSelectorForStatesWithDelayedForks.isEmpty()
                && executionsWithVirtualObjectAndWithoutDelayedForks.isEmpty() && pathSelectorForStatesWithConcretizedTypes.isEmpty()
    }

    private fun generateStateWithConcretizedType(): PythonExecutionState? {
        if (executionsWithVirtualObjectAndWithoutDelayedForks.isEmpty())
            return null
        val state = executionsWithVirtualObjectAndWithoutDelayedForks.random()
        val symbol = state.symbolsWithoutConcreteTypes!!.first()
        val obj = interpretSymbolicPythonObject(symbol.obj, state.pyModel)
        val typeStream = state.pyModel.uModel.types.typeStream(obj.address)
        if (typeStream.isEmpty) {
            executionsWithVirtualObjectAndWithoutDelayedForks.remove(state)
            return generateStateWithConcretizedType()
        }
        val type = typeStream.takeFirst()
        val forkResult = fork(state, state.pathConstraints.typeConstraints.evalIs(symbol.obj.address, type))
        executionsWithVirtualObjectAndWithoutDelayedForks.remove(state)
        val stateWithRemainingTypes = forkResult.negativeState
        if (stateWithRemainingTypes != null) {
            stateWithRemainingTypes.symbolsWithoutConcreteTypes = state.symbolsWithoutConcreteTypes!!
            executionsWithVirtualObjectAndWithoutDelayedForks.add(stateWithRemainingTypes)
        }

        require(forkResult.positiveState != null)

        val result = forkResult.positiveState!!
        result.fromStateWithVirtualObjectAndWithoutDelayedForks = stateWithRemainingTypes
        result.extractedFrom = null
        result.wasExecuted = false
        return result
    }

    override fun peek(): PythonExecutionState {
        if (!pathSelectorForStatesWithConcretizedTypes.isEmpty()) {
            val result = pathSelectorForStatesWithConcretizedTypes.peek()
            result.extractedFrom = pathSelectorForStatesWithConcretizedTypes
            return result
        }
        val stateWithConcreteType = generateStateWithConcretizedType()
        if (stateWithConcreteType != null) {
            pathSelectorForStatesWithConcretizedTypes.add(listOf(stateWithConcreteType))
            return peek()
        }
        if (!basePathSelector.isEmpty()) {
            val result = basePathSelector.peek()
            result.extractedFrom = basePathSelector
            return result
        }
        if (delayedForks.isNotEmpty()) {
            TODO()

        } else if (!pathSelectorForStatesWithDelayedForks.isEmpty()) {
            val result = pathSelectorForStatesWithDelayedForks.peek()
            result.extractedFrom = pathSelectorForStatesWithDelayedForks
            return result

        } else {
            error("Not reachable")
        }
    }

    override fun update(state: PythonExecutionState) {
        if (state.symbolsWithoutConcreteTypes != null) {
            require(state.wasExecuted)
            executionsWithVirtualObjectAndWithoutDelayedForks.add(state)
        }
        if (state.wasExecuted && !state.modelDied) {
            state.fromStateWithVirtualObjectAndWithoutDelayedForks?.let {
                executionsWithVirtualObjectAndWithoutDelayedForks.remove(it)
            }
        }
        if (state.wasExecuted) {
            state.extractedFrom?.remove(state)
            state.delayedForks.forEach {
                delayedForks.add(DelayedForkWithTypeRating(it, state.makeTypeRating(it)))
            }

        } else {
            state.extractedFrom?.update(state)
        }
    }

    override fun add(states: Collection<PythonExecutionState>) {
        states.forEach { state ->
            if (state.wasExecuted && !state.modelDied) {
                state.fromStateWithVirtualObjectAndWithoutDelayedForks?.let {
                    executionsWithVirtualObjectAndWithoutDelayedForks.remove(it)
                }
            }
            if (state.symbolsWithoutConcreteTypes != null) {
                require(state.wasExecuted)
                executionsWithVirtualObjectAndWithoutDelayedForks.add(state)
            }
            if (state.wasExecuted) {
                return@forEach
            }
            if (state.delayedForks.isEmpty()) {
                basePathSelector.add(listOf(state))
            } else {
                pathSelectorForStatesWithDelayedForks.add(listOf(state))
            }
        }
    }

    override fun remove(state: PythonExecutionState) {
        state.extractedFrom?.remove(state)
    }

}

data class DelayedForkWithTypeRating(
    val delayedFork: DelayedFork,
    val typeRating: UTypeStream<PythonType>
)