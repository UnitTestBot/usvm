package org.usvm.machine

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.PathNode
import org.usvm.UAddressSort
import org.usvm.UCallStack
import org.usvm.UMockSymbol
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.language.PyCallable
import org.usvm.language.PyInstruction
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.interpreters.concrete.utils.VirtualPythonObject
import org.usvm.machine.interpreters.symbolic.operations.tracing.SymbolicHandlerEvent
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetsSet
import org.usvm.types.UTypeStream

object PyTarget : UTarget<PyInstruction, PyTarget>()
private val targets = UTargetsSet.empty<PyTarget, PyInstruction>()

class PyState(
    ctx: PyContext,
    ownership: MutabilityOwnership,
    private val pythonCallable: PyUnpinnedCallable,
    val inputSymbols: List<UninterpretedSymbolicPythonObject>,
    override val pathConstraints: PyPathConstraints,
    memory: UMemory<PythonType, PyCallable>,
    uModel: UModelBase<PythonType>,
    val typeSystem: PythonTypeSystem,
    val preAllocatedObjects: PreallocatedObjects,
    var possibleTypesForNull: UTypeStream<PythonType> = typeSystem.topTypeStream(),
    callStack: UCallStack<PyCallable, PyInstruction> = UCallStack(),
    pathLocation: PathNode<PyInstruction> = PathNode.root(),
    forkPoints: PathNode<PathNode<PyInstruction>> = PathNode.root(),
    var pathNodeBreakpoints: PersistentList<PathNode<PyInstruction>> = persistentListOf(),
    var concolicQueries: PersistentList<SymbolicHandlerEvent<Any>> = persistentListOf(),
    var delayedForks: PersistentList<DelayedFork> = persistentListOf(),
    val mocks: MutableMap<MockHeader, UMockSymbol<UAddressSort>> = mutableMapOf(),
    val mockedObjects: MutableSet<UninterpretedSymbolicPythonObject> = mutableSetOf(),
    var uniqueInstructions: PersistentSet<PyInstruction> = persistentSetOf(),
) : UState<PythonType, PyCallable, PyInstruction, PyContext, PyTarget, PyState>(
    ctx,
    ownership,
    callStack,
    pathConstraints,
    memory,
    listOf(uModel),
    pathLocation,
    forkPoints,
    targets,
) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): PyState {
        require(newConstraints is PyPathConstraints?)
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val newPathConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        val newMemory = memory.clone(newPathConstraints.typeConstraints, newThisOwnership, cloneOwnership)
        return PyState(
            ctx,
            cloneOwnership,
            pythonCallable,
            inputSymbols,
            newPathConstraints,
            newMemory,
            models.first(),
            typeSystem,
            preAllocatedObjects.clone(),
            possibleTypesForNull,
            callStack,
            pathNode,
            forkPoints,
            pathNodeBreakpoints.add(pathNode),
            concolicQueries,
            delayedForks,
            mocks.toMutableMap(), // copy
            mockedObjects.toMutableSet(), // copy
            uniqueInstructions
        )
    }

    override val entrypoint = pythonCallable

    override val isExceptional: Boolean = false // TODO

    val pyModel: PyModel
        get() = checkNotNull(models.first() as? PyModel) { "Model PyState must be PyModel" }

    fun buildPathAsList(): List<SymbolicHandlerEvent<Any>> = concolicQueries

    fun isTerminated(): Boolean {
        return modelDied || wasInterrupted || wasExecuted && objectsWithoutConcreteTypes == null
    }

    fun isInterestingForPathSelector(): Boolean {
        return !isTerminated() || delayedForks.isNotEmpty()
    }

    var extractedFrom: UPathSelector<PyState>? = null
    var wasExecuted: Boolean = false
    var wasInterrupted: Boolean = false
    var modelDied: Boolean = false
    var objectsWithoutConcreteTypes: Collection<VirtualPythonObject>? = null
    var generatedFrom: String = "" // for debugging only
}

class DelayedFork(
    val state: PyState,
    val symbol: UninterpretedSymbolicPythonObject,
    val possibleTypes: UTypeStream<PythonType>,
    val delayedForkPrefix: PersistentList<DelayedFork>,
)
