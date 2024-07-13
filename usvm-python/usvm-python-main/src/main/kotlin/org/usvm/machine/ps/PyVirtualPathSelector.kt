package org.usvm.machine.ps

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.WithSolverStateForker.fork
import org.usvm.api.typeStreamOf
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.model.GenerateNewFromPathConstraints
import org.usvm.machine.model.toPyModel
import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkGraphCreation
import org.usvm.machine.ps.strategies.DelayedForkGraphInnerVertex
import org.usvm.machine.ps.strategies.DelayedForkGraphRootVertex
import org.usvm.machine.ps.strategies.DelayedForkGraphVertex
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.DelayedForkStrategy
import org.usvm.machine.ps.strategies.MakeDelayedFork
import org.usvm.machine.ps.strategies.Peek
import org.usvm.machine.ps.strategies.PyPathSelectorActionStrategy
import org.usvm.machine.ps.types.makeTypeRating
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.types.MockType
import org.usvm.machine.types.PythonType
import org.usvm.types.TypesResult

class PyVirtualPathSelector<DFState : DelayedForkState, DFGraph : DelayedForkGraph<DFState>>(
    private val ctx: PyContext,
    private val actionStrategy: PyPathSelectorActionStrategy<DFState, DFGraph>,
    private val delayedForkStrategy: DelayedForkStrategy<DFState>,
    private val graphCreation: DelayedForkGraphCreation<DFState, DFGraph>,
    private val newStateObserver: NewStateObserver,
) : UPathSelector<PyState> {
    private val graph = graphCreation.createOneVertexGraph(DelayedForkGraphRootVertex())
    override fun isEmpty(): Boolean = nullablePeek() == null

    override fun peek(): PyState =
        nullablePeek() ?: error("peek() must be called after isEmpty() check")

    override fun update(state: PyState) {
        logger.debug("Updating state {}", state)
        removeNotExecutedState(state)
        add(state)
    }

    override fun add(states: Collection<PyState>) {
        states.forEach { add(it) }
    }

    override fun remove(state: PyState) {
        logger.debug("Removing state {}", state)
        removeNotExecutedState(state)
        addDelayedForkVertices(state)
        processExecutedState(state)
    }

    private fun removeNotExecutedState(state: PyState) {
        peekCache = null
        state.extractedFrom?.remove(state)
    }

    private fun add(state: PyState) {
        addDelayedForkVertices(state)
        if (state.isTerminated()) {
            processExecutedState(state)
            return
        }
        if (state.delayedForks.isEmpty() && state.objectsWithoutConcreteTypes != null) {
            require(state.wasExecuted)
            val newState = generateStateWithConcreteTypeWithoutDelayedFork(state) ?: return
            graph.addExecutedStateWithConcreteTypes(newState)
        } else if (state.delayedForks.isEmpty()) {
            graph.addStateToVertex(graph.root, state)
        } else {
            val lastDelayedFork = state.delayedForks.last()
            val vertex = graph.getVertexByDelayedFork(lastDelayedFork)
                ?: error("DelayedForkVertex must already be in the graph")
            graph.addStateToVertex(vertex, state)
        }
    }

    private fun addDelayedForkVertices(state: PyState) {
        var parent: DelayedForkGraphVertex<DFState> = graph.root
        state.delayedForks.forEach { delayedFork ->
            val vertex = graph.getVertexByDelayedFork(delayedFork) ?: let {
                val dfState = graphCreation.createEmptyDelayedForkState()
                DelayedForkGraphInnerVertex(dfState, delayedFork, parent).also { vertex ->
                    graph.addVertex(delayedFork, vertex)
                }
            }
            parent = vertex
        }
    }

    private var peekCache: PyState? = null

    private fun nullablePeek(): PyState? {
        if (peekCache != null) {
            return peekCache
        }
        while (true) {
            when (val action = actionStrategy.chooseAction(graph)) {
                null -> {
                    peekCache = null
                    break
                }
                is Peek -> {
                    val ps = action.pathSelector
                    require(!ps.isEmpty()) {
                        "Cannot peek object from empty path selector"
                    }
                    peekCache = ps.peek().also { it.extractedFrom = ps }
                    break
                }
                is MakeDelayedFork -> {
                    require(!action.vertex.delayedForkState.isDead) {
                        "Cannot make delayed fork from dead state"
                    }
                    require(action.vertex.delayedForkState.size != 0) {
                        "Cannot make delayed fork from state without type ratings"
                    }
                    val state = generateStateWithConcreteType(action.vertex.delayedFork, action.vertex.delayedForkState)
                    if (state != null) {
                        graph.addStateToVertex(action.vertex.parent, state)
                    } else {
                        logger.debug("Could not make state with concrete type for {}", action.vertex.delayedFork)
                    }
                    graph.updateVertex(action.vertex)
                }
            }
        }
        return peekCache
    }

    private fun generateStateWithConcreteType(delayedFork: DelayedFork, delayedForkState: DFState): PyState? = with(
        ctx
    ) {
        val typeRating = delayedForkStrategy.chooseTypeRating(delayedForkState)
        while (typeRating.types.isNotEmpty() && typeRating.types.first() in delayedForkState.usedTypes) {
            typeRating.types.removeAt(0)
            typeRating.numberOfUsed++
        }
        if (typeRating.types.isEmpty()) {
            delayedForkState.isDead = true
            return null
        }
        val type = typeRating.types.removeAt(0)
        typeRating.numberOfUsed++
        delayedForkState.usedTypes.add(type)
        val state = delayedFork.state
        val symbol = delayedFork.symbol
        logger.debug { "Delayed fork on ${symbol.address}" }
        logger.debug { "Chosen type: $type" }
        val forkResult = fork(state, symbol.evalIs(ctx, state.pathConstraints.typeConstraints, type).not())
        if (forkResult.positiveState != state) {
            require(typeRating.types.isEmpty())
            delayedForkState.isDead = true
            return null
        }
        val result = forkResult.negativeState ?: return null
        result.models = listOf(
            result.models.first().toPyModel(ctx, GenerateNewFromPathConstraints(result.pathConstraints))
        )
        newStateObserver.onNewState(result)
        require(result.delayedForks == delayedFork.delayedForkPrefix)
        result.generatedFrom = "from delayed fork"
        delayedForkState.successfulTypes.add(type)
        return result
    }

    private fun generateStateWithConcreteTypeWithoutDelayedFork(state: PyState): PyState? {
        val objectsWithoutConcreteTypes = state.objectsWithoutConcreteTypes
        require(state.wasExecuted && objectsWithoutConcreteTypes != null)
        val objects = objectsWithoutConcreteTypes.map {
            val addressRaw = it.interpretedObjRef
            ctx.mkConcreteHeapRef(addressRaw)
        }
        val typeStreamsRaw = objects.map {
            if (it.address == 0) {
                state.possibleTypesForNull
            } else {
                state.pyModel.typeStreamOf(it)
            }
        }
        val typeStreams = typeStreamsRaw.map {
            @Suppress("unchecked_cast")
            when (val taken = it.take(2)) {
                is TypesResult.SuccessfulTypesResult<*> -> taken.types as Collection<PythonType>
                else -> return null
            }
        }
        if (typeStreams.any { it.size < 2 }) {
            return null
        }
        require(typeStreams.all { it.first() == MockType })
        val types = typeStreams.map { it.take(2).last() }
        (objects zip types).forEach { (objAddress, type) ->
            state.pyModel.forcedConcreteTypes[objAddress] = type
        }
        state.wasExecuted = false
        state.extractedFrom = null
        return state
    }

    private fun processExecutedState(state: PyState) {
        logger.debug("Processing executed state {}", state)
        require(state.isTerminated())
        state.delayedForks.forEach {
            val vertex = graph.getVertexByDelayedFork(it)
                ?: error("DelayedForkVertex must already be in the graph")
            val typeRating = makeTypeRating(state, it) ?: return@forEach
            vertex.delayedForkState.addTypeRating(typeRating)
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
