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
import org.usvm.constraints.UPathConstraints
import org.usvm.language.PyCallable
import org.usvm.language.PyInstruction
import org.usvm.language.PyUnpinnedCallable
import org.usvm.language.TypeMethod
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
    var concolicQueries: PersistentList<SymbolicHandlerEvent<Any>> = persistentListOf(),
    var delayedForks: PersistentList<DelayedFork> = persistentListOf(),
    private val mocks: MutableMap<MockHeader, UMockSymbol<UAddressSort>> = mutableMapOf(),
    val mockedObjects: MutableSet<UninterpretedSymbolicPythonObject> = mutableSetOf(),
    var uniqueInstructions: PersistentSet<PyInstruction> = persistentSetOf(),
) : UState<PythonType, PyCallable, PyInstruction, PyContext, PyTarget, PyState>(
    ctx,
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
        val newPathConstraints = newConstraints ?: pathConstraints.clone()
        val newMemory = memory.clone(newPathConstraints.typeConstraints)
        return PyState(
            ctx,
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
            concolicQueries,
            delayedForks,
            mocks.toMutableMap(), // copy
            mockedObjects.toMutableSet(), // copy
            uniqueInstructions
        )
    }

    override val entrypoint = pythonCallable

    override val isExceptional: Boolean = false // TODO

    val meta = PythonExecutionStateMeta()

    val pyModel: PyModel
        get() = checkNotNull(models.first() as? PyModel) { "Model PyState must be PyModel" }

    fun buildPathAsList(): List<SymbolicHandlerEvent<Any>> = concolicQueries

    fun mock(what: MockHeader): MockResult {
        val cached = mocks[what]
        if (cached != null) {
            return MockResult(UninterpretedSymbolicPythonObject(cached, typeSystem), false, cached)
        }
        val result = memory.mocker.call(what.method, what.args.map { it.address }.asSequence(), ctx.addressSort)
        mocks[what] = result
        what.methodOwner?.let { mockedObjects.add(it) }
        return MockResult(UninterpretedSymbolicPythonObject(result, typeSystem), true, result)
    }

    fun getMocksForSymbol(
        symbol: UninterpretedSymbolicPythonObject,
    ): List<Pair<MockHeader, UninterpretedSymbolicPythonObject>> =
        mocks.mapNotNull { (mockHeader, mockResult) ->
            if (mockHeader.methodOwner == symbol) {
                mockHeader to UninterpretedSymbolicPythonObject(mockResult, typeSystem)
            } else {
                null
            }
        }

    fun isTerminated(): Boolean {
        return meta.modelDied || meta.wasInterrupted || meta.wasExecuted && meta.objectsWithoutConcreteTypes == null
    }

    fun isInterestingForPathSelector(): Boolean {
        return !isTerminated() || delayedForks.isNotEmpty()
    }
}

class DelayedFork(
    val state: PyState,
    val symbol: UninterpretedSymbolicPythonObject,
    val possibleTypes: UTypeStream<PythonType>,
    val delayedForkPrefix: PersistentList<DelayedFork>,
)

data class MockHeader(
    val method: TypeMethod,
    val args: List<UninterpretedSymbolicPythonObject>,
    var methodOwner: UninterpretedSymbolicPythonObject?,
)

data class MockResult(
    val mockedObject: UninterpretedSymbolicPythonObject,
    val isNew: Boolean,
    val mockSymbol: UMockSymbol<UAddressSort>,
)

class PythonExecutionStateMeta {
    var extractedFrom: UPathSelector<PyState>? = null
    var wasExecuted: Boolean = false
    var wasInterrupted: Boolean = false
    var modelDied: Boolean = false
    var objectsWithoutConcreteTypes: Collection<VirtualPythonObject>? = null
    var generatedFrom: String = "" // for debugging only
}
