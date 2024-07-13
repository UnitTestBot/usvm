package org.usvm.machine.ps.strategies

import org.usvm.UPathSelector
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.types.ConcretePythonType

interface PyPathSelectorActionStrategy<DFState : DelayedForkState, DFGraph : DelayedForkGraph<DFState>> {
    fun chooseAction(graph: DFGraph): PyPathSelectorAction<DFState>?
}

interface DelayedForkStrategy<DFState : DelayedForkState> {
    fun chooseTypeRating(state: DFState): TypeRating
}

interface DelayedForkGraphCreation<DFState : DelayedForkState, DFGraph : DelayedForkGraph<DFState>> {
    fun createEmptyDelayedForkState(): DFState
    fun createOneVertexGraph(root: DelayedForkGraphRootVertex<DFState>): DFGraph
}

open class DelayedForkState {
    val usedTypes: MutableSet<ConcretePythonType> = mutableSetOf()
    val successfulTypes: MutableSet<ConcretePythonType> = mutableSetOf()
    var isDead: Boolean = false
    private val typeRatings = mutableListOf<TypeRating>()
    open fun addTypeRating(typeRating: TypeRating) {
        typeRatings.add(typeRating)
    }
    val size: Int
        get() = typeRatings.size
    fun getAt(idx: Int): TypeRating {
        require(idx < size)
        return typeRatings[idx]
    }
}

abstract class DelayedForkGraph<DFState : DelayedForkState>(
    val root: DelayedForkGraphRootVertex<DFState>,
) {
    private val vertices: MutableMap<DelayedFork, DelayedForkGraphInnerVertex<DFState>> = mutableMapOf()
    open fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<DFState>) {
        require(vertices[df] == null) {
            "Cannot add delayed fork twice"
        }
        vertices[df] = vertex
    }
    abstract fun addExecutedStateWithConcreteTypes(state: PyState)
    abstract fun addStateToVertex(vertex: DelayedForkGraphVertex<DFState>, state: PyState)
    open fun updateVertex(vertex: DelayedForkGraphInnerVertex<DFState>) = run {}
    fun getVertexByDelayedFork(df: DelayedFork): DelayedForkGraphInnerVertex<DFState>? =
        vertices[df]
}

sealed class DelayedForkGraphVertex<DFState : DelayedForkState>

class DelayedForkGraphRootVertex<DFState : DelayedForkState> : DelayedForkGraphVertex<DFState>()

class DelayedForkGraphInnerVertex<DFState : DelayedForkState>(
    val delayedForkState: DFState,
    val delayedFork: DelayedFork,
    val parent: DelayedForkGraphVertex<DFState>,
) : DelayedForkGraphVertex<DFState>()

sealed class PyPathSelectorAction<DFState : DelayedForkState>
class Peek<DFState : DelayedForkState>(
    val pathSelector: UPathSelector<PyState>,
) : PyPathSelectorAction<DFState>()
class MakeDelayedFork<DFState : DelayedForkState>(
    val vertex: DelayedForkGraphInnerVertex<DFState>,
) : PyPathSelectorAction<DFState>()

class TypeRating(
    val types: MutableList<ConcretePythonType>,
    val numberOfHints: Int,
    var numberOfUsed: Int = 0,
)
