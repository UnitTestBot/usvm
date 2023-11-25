package org.usvm.machine

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.WithSolverStateForker.fork
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.language.types.MockType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.model.toPyModel
import org.usvm.types.TypesResult
import kotlin.random.Random

class PythonVirtualPathSelector(
    private val ctx: UPythonContext,
    private val typeSystem: PythonTypeSystem,
    private val basePathSelectorWithoutVirtual: UPathSelector<PythonExecutionState>,
    private val basePathSelectorWithVirtual: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithDelayedForks: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithConcretizedTypes: UPathSelector<PythonExecutionState>,
) : UPathSelector<PythonExecutionState> {
    private val unservedDelayedForks = mutableSetOf<DelayedForkWithTypeRating>()
    private val servedDelayedForks = mutableSetOf<DelayedForkWithTypeRating>()
    private val executionsWithVirtualObjectAndWithoutDelayedForks = mutableSetOf<PythonExecutionState>()
    private val triedTypesForDelayedForks = mutableSetOf<Pair<DelayedFork, ConcretePythonType>>()
    private val random = Random(0)

    override fun isEmpty(): Boolean {
        return nullablePeek() == null
    }

    private fun generateStateWithConcretizedTypeFromDelayedFork(
        delayedForkStorage: MutableSet<DelayedForkWithTypeRating>
    ): PythonExecutionState? = with(ctx) {
        if (delayedForkStorage.isEmpty())
            return null
        val delayedFork = delayedForkStorage.random(random)
        val state = delayedFork.delayedFork.state
        val symbol = delayedFork.delayedFork.symbol
        val typeRating = delayedFork.typeRating
        if (typeRating.isEmpty()) {
            delayedForkStorage.remove(delayedFork)
            return generateStateWithConcretizedTypeFromDelayedFork(delayedForkStorage)
        }
        val concreteType = typeRating.first()
        require(concreteType is ConcretePythonType)
        typeRating.removeFirst()
        if (triedTypesForDelayedForks.contains(delayedFork.delayedFork to concreteType))
            return generateStateWithConcretizedTypeFromDelayedFork(delayedForkStorage)
        triedTypesForDelayedForks.add(delayedFork.delayedFork to concreteType)

        val forkResult = fork(state, symbol.evalIs(ctx, state.pathConstraints.typeConstraints, concreteType).not())
        if (forkResult.positiveState != state) {
            require(typeRating.isEmpty() && forkResult.positiveState == null)
            unservedDelayedForks.removeIf { it.delayedFork.state == state }
            servedDelayedForks.removeIf { it.delayedFork.state == state }
        }
        if (forkResult.negativeState == null)
            return null
        val stateWithConcreteType = forkResult.negativeState!!
        stateWithConcreteType.models = listOf(stateWithConcreteType.models.first().toPyModel(ctx, typeSystem, stateWithConcreteType.pathConstraints, stateWithConcreteType.preAllocatedObjects))
        if (unservedDelayedForks.remove(delayedFork))
            servedDelayedForks.add(delayedFork)

        return stateWithConcreteType.also {
            it.delayedForks = delayedFork.delayedFork.delayedForkPrefix
            it.meta.generatedFrom = "From delayed fork"
        }
    }

    private fun generateStateWithConcretizedTypeWithoutDelayedForks(): PythonExecutionState? {
        if (executionsWithVirtualObjectAndWithoutDelayedForks.isEmpty())
            return null
        val state = executionsWithVirtualObjectAndWithoutDelayedForks.random(random)
        executionsWithVirtualObjectAndWithoutDelayedForks.remove(state)
        val objects = state.meta.objectsWithoutConcreteTypes!!.map { it.interpretedObj }
        val typeStreamsRaw = objects.map { it.getTypeStream() ?: state.possibleTypesForNull }
        val typeStreams = typeStreamsRaw.map {
            @Suppress("unchecked_cast")
            when (val taken = it.take(2)) {
                is TypesResult.SuccessfulTypesResult<*> -> taken.types as Collection<PythonType>
                else -> return null
            }
        }
        if (typeStreams.any { it.size < 2 }) {
            return generateStateWithConcretizedTypeWithoutDelayedForks()
        }
        require(typeStreams.all { it.first() == MockType })
        val types = typeStreams.map {it.take(2).last()}
        (objects zip types).forEach { (obj, type) ->
            state.meta.lastConverter!!.forcedConcreteTypes[obj.address] = type
        }
        state.meta.wasExecuted = false
        state.meta.extractedFrom = null
        return state
    }

    private var peekCache: PythonExecutionState? = null

    private fun nullablePeek(): PythonExecutionState? {
        if (peekCache != null)
            return peekCache

        if (!pathSelectorForStatesWithConcretizedTypes.isEmpty()) {
            val result = pathSelectorForStatesWithConcretizedTypes.peek()
            result.meta.extractedFrom = pathSelectorForStatesWithConcretizedTypes
            peekCache = result
            return result
        }

        val stateWithConcreteType = generateStateWithConcretizedTypeWithoutDelayedForks()
        if (stateWithConcreteType != null) {
            pathSelectorForStatesWithConcretizedTypes.add(listOf(stateWithConcreteType))
            return nullablePeek()
        }

        val zeroCoin = random.nextDouble()
        val firstCoin = random.nextDouble()
        val secondCoin = random.nextDouble()
        val thirdCoin = random.nextDouble()

        if (!basePathSelectorWithoutVirtual.isEmpty() && (zeroCoin < 0.8 || (unservedDelayedForks.isEmpty() && pathSelectorForStatesWithDelayedForks.isEmpty() && basePathSelectorWithVirtual.isEmpty() && servedDelayedForks.isEmpty()))) {
            val result = basePathSelectorWithoutVirtual.peek()
            result.meta.extractedFrom = basePathSelectorWithoutVirtual
            peekCache = result
            return result

        } else if (unservedDelayedForks.isNotEmpty() && (firstCoin < 0.9 || pathSelectorForStatesWithDelayedForks.isEmpty() && basePathSelectorWithVirtual.isEmpty() && servedDelayedForks.isEmpty())) {
            logger.debug("Trying to make delayed fork")
            val newState = generateStateWithConcretizedTypeFromDelayedFork(unservedDelayedForks)
            newState?.let { add(listOf(it)) }
            return nullablePeek()

        } else if (!pathSelectorForStatesWithDelayedForks.isEmpty()  && (secondCoin < 0.7 || basePathSelectorWithVirtual.isEmpty() && servedDelayedForks.isEmpty())) {
            val result = pathSelectorForStatesWithDelayedForks.peek()
            result.meta.extractedFrom = pathSelectorForStatesWithDelayedForks
            peekCache = result
            return result

        } else if (!basePathSelectorWithVirtual.isEmpty() && (thirdCoin < 0.5 || servedDelayedForks.isEmpty())) {
            val result = basePathSelectorWithVirtual.peek()
            result.meta.extractedFrom = basePathSelectorWithVirtual
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
        val source = when (result.meta.extractedFrom) {
            basePathSelectorWithoutVirtual -> "basePathSelectorWithoutVirtual"
            basePathSelectorWithVirtual -> "basePathSelectorWithVirtual"
            pathSelectorForStatesWithDelayedForks -> "pathSelectorForStatesWithDelayedForks"
            pathSelectorForStatesWithConcretizedTypes -> "pathSelectorForStatesWithConcretizedTypes"
            else -> error("Not reachable")
        }
        logger.debug("Extracted from {} state {}", source, result)
        return result
    }

    private fun processDelayedForksOfExecutedState(state: PythonExecutionState) {
        require(state.isTerminated())
        /*if (!state.meta.endedWithTypeErrorOrAttributeError && state.meta.objectsWithoutConcreteTypes != null) {
            /*require(state.meta.objectsWithoutConcreteTypes != null) {
                "State with delayed fork must have virtual objects"
            }*/
            executionsWithVirtualObjectAndWithoutDelayedForks.add(state)
        }*/
        state.delayedForks.firstOrNull()?.let {
            unservedDelayedForks.add(
                DelayedForkWithTypeRating(
                    it,
                    state.makeTypeRating(it).toMutableList()
                )
            )
        }
    }

    override fun update(state: PythonExecutionState) {
        peekCache = null
        state.meta.extractedFrom?.remove(state)
        add(listOf(state))
    }

    override fun add(states: Collection<PythonExecutionState>) {
        peekCache = null
        states.forEach { state ->
            if (state.isTerminated()) {
                processDelayedForksOfExecutedState(state)
                return@forEach
            }
            if (state.meta.objectsWithoutConcreteTypes != null /*&& !state.meta.endedWithTypeErrorOrAttributeError*/) {
                require(state.meta.wasExecuted)
                executionsWithVirtualObjectAndWithoutDelayedForks.add(state)
            } else if (state.delayedForks.isEmpty()) {
                if (state.meta.numberOfVirtualObjectsInParent > 0) {
                    basePathSelectorWithVirtual.add(listOf(state))
                } else {
                    basePathSelectorWithoutVirtual.add(listOf(state))
                }
            } else {
                pathSelectorForStatesWithDelayedForks.add(listOf(state))
            }
        }
    }

    override fun remove(state: PythonExecutionState) {
        peekCache = null
        state.meta.extractedFrom?.remove(state)
        processDelayedForksOfExecutedState(state)
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}

class DelayedForkWithTypeRating(
    val delayedFork: DelayedFork,
    val typeRating: MutableList<PythonType>
)