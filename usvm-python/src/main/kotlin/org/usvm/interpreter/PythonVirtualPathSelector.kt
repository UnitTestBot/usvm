package org.usvm.interpreter

import mu.KLogging
import org.usvm.UContext
import org.usvm.UPathSelector
import org.usvm.fork
import org.usvm.language.types.PythonType
import org.usvm.language.types.TypeOfVirtualObject
import org.usvm.types.first
import kotlin.random.Random

class PythonVirtualPathSelector(
    private val ctx: UContext,
    private val basePathSelector: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithDelayedForks: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithConcretizedTypes: UPathSelector<PythonExecutionState>
) : UPathSelector<PythonExecutionState> {
    private val unservedDelayedForks = mutableSetOf<DelayedForkWithTypeRating>()
    private val servedDelayedForks = mutableSetOf<DelayedForkWithTypeRating>()
    private val executionsWithVirtualObjectAndWithoutDelayedForks = mutableSetOf<PythonExecutionState>()
    private val random = Random(0)

    override fun isEmpty(): Boolean {
        return nullablePeek() == null
    }

    private fun generateStateWithConcretizedTypeFromDelayedFork(
        delayedForkStorage: MutableSet<DelayedForkWithTypeRating>
    ): PythonExecutionState? = with(ctx) {
        if (delayedForkStorage.isEmpty())
            return null
        val delayedFork = delayedForkStorage.random()  // TODO: add weights (the less unresolved types, the more probable choice)
        val state = delayedFork.delayedFork.state
        val symbol = delayedFork.delayedFork.symbol
        val typeRating = delayedFork.typeRating
        if (typeRating.isEmpty()) {
            delayedForkStorage.remove(delayedFork)
            return generateStateWithConcretizedTypeFromDelayedFork(delayedForkStorage)
        }
        val concreteType = typeRating.first()
        require(concreteType != TypeOfVirtualObject)
        val forkResult = fork(state, symbol.evalIs(ctx, state.pathConstraints.typeConstraints, concreteType).not())
        if (forkResult.positiveState != state) {
            unservedDelayedForks.removeIf { it.delayedFork.state == state }
            servedDelayedForks.removeIf { it.delayedFork.state == state }
        } else {
            typeRating.removeFirst()
        }
        if (forkResult.negativeState != null && unservedDelayedForks.remove(delayedFork))
            servedDelayedForks.add(delayedFork)

        return forkResult.negativeState?.let {
            it.delayedForks = delayedFork.delayedFork.delayedForkPrefix
            it
        }
    }

    private fun generateStateWithConcretizedTypeWithoutDelayedForks(): PythonExecutionState? {
        if (executionsWithVirtualObjectAndWithoutDelayedForks.isEmpty())
            return null
        val state = executionsWithVirtualObjectAndWithoutDelayedForks.random()
        executionsWithVirtualObjectAndWithoutDelayedForks.remove(state)
        val objects = state.objectsWithoutConcreteTypes!!.map { it.interpretedObj }
        val typeStreams = objects.map { it.getTypeStream() }
        if (typeStreams.any { it.take(2).size < 2 }) {
            return generateStateWithConcretizedTypeWithoutDelayedForks()
        }
        require(typeStreams.all { it.first() == TypeOfVirtualObject })
        val types = typeStreams.map {it.take(2).last()}
        (objects zip types).forEach { (obj, type) ->
            state.lastConverter!!.forcedConcreteTypes[obj.address] = type
        }
        state.wasExecuted = false
        state.extractedFrom = null
        return state
    }

    private val threshold: Double = 0.5
    private var peekCache: PythonExecutionState? = null

    private fun nullablePeek(): PythonExecutionState? {
        if (peekCache != null)
            return peekCache
        if (!pathSelectorForStatesWithConcretizedTypes.isEmpty()) {
            val result = pathSelectorForStatesWithConcretizedTypes.peek()
            result.extractedFrom = pathSelectorForStatesWithConcretizedTypes
            peekCache = result
            return result
        }
        val stateWithConcreteType = generateStateWithConcretizedTypeWithoutDelayedForks()
        if (stateWithConcreteType != null) {
            pathSelectorForStatesWithConcretizedTypes.add(listOf(stateWithConcreteType))
            return nullablePeek()
        }
        if (!basePathSelector.isEmpty()) {
            val result = basePathSelector.peek()
            result.extractedFrom = basePathSelector
            peekCache = result
            return result
        }

        val firstCoin = random.nextDouble()
        val secondCoin = random.nextDouble()
        if (unservedDelayedForks.isNotEmpty() && (firstCoin < threshold || pathSelectorForStatesWithDelayedForks.isEmpty())) {
            val newState = generateStateWithConcretizedTypeFromDelayedFork(unservedDelayedForks)
            newState?.let { add(listOf(it)) }
            return nullablePeek()

        } else if (!pathSelectorForStatesWithDelayedForks.isEmpty()  && (secondCoin < threshold || servedDelayedForks.isEmpty())) {
            val result = pathSelectorForStatesWithDelayedForks.peek()
            result.extractedFrom = pathSelectorForStatesWithDelayedForks
            peekCache = result
            return result

        } else if (servedDelayedForks.isNotEmpty()) {
            val newState = generateStateWithConcretizedTypeFromDelayedFork(servedDelayedForks)
            newState?.let { add(listOf(it)) }
            return nullablePeek()

        } else {
            peekCache = null
            return null
        }
    }

    override fun peek(): PythonExecutionState {
        val result = nullablePeek()!!
        val source = when (result.extractedFrom) {
            basePathSelector -> "basePathSelector"
            pathSelectorForStatesWithDelayedForks -> "pathSelectorForStatesWithDelayedForks"
            pathSelectorForStatesWithConcretizedTypes -> "pathSelectorForStatesWithConcretizedTypes"
            else -> error("Not reachable")
        }
        logger.debug("Extracted from {} state {}", source, result)
        return result
    }

    override fun update(state: PythonExecutionState) {
        peekCache = null
        if (state.objectsWithoutConcreteTypes != null) {
            require(state.wasExecuted)
            executionsWithVirtualObjectAndWithoutDelayedForks.add(state)
        }
        if (state.wasExecuted) {
            state.extractedFrom?.remove(state)
            state.delayedForks.firstOrNull()?.let {
                unservedDelayedForks.add(
                    DelayedForkWithTypeRating(
                        it,
                        state.makeTypeRating(it).toMutableList()
                    )
                )
            }

        } else {
            state.extractedFrom?.update(state)
        }
    }

    override fun add(states: Collection<PythonExecutionState>) {
        peekCache = null
        states.forEach { state ->
            if (state.objectsWithoutConcreteTypes != null) {
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
        peekCache = null
        state.extractedFrom?.remove(state)
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}

class DelayedForkWithTypeRating(
    val delayedFork: DelayedFork,
    val typeRating: MutableList<PythonType>
)