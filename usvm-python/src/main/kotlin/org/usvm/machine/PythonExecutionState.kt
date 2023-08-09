package org.usvm.machine

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.interpreters.operations.tracing.SymbolicHandlerEvent
import org.usvm.machine.symbolicobjects.ConverterToPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.TypeOfVirtualObject
import org.usvm.machine.utils.PyModel
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase
import org.usvm.types.UTypeStream

private const val MAX_CONCRETE_TYPES_TO_CONSIDER = 1000

class PythonExecutionState(
    private val ctx: UPythonContext,
    private val pythonCallable: PythonUnpinnedCallable,
    val inputSymbols: List<SymbolForCPython>,
    pathConstraints: UPathConstraints<PythonType>,
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    uModel: UModelBase<PropertyOfPythonObject, PythonType>,
    callStack: UCallStack<PythonCallable, SymbolicHandlerEvent<Any>> = UCallStack(),
    path: PersistentList<SymbolicHandlerEvent<Any>> = persistentListOf(),
    var delayedForks: PersistentList<DelayedFork> = persistentListOf(),
    private val mocks: MutableMap<MockHeader, UMockSymbol<UAddressSort>> = mutableMapOf(),
    val mockedObjects: MutableSet<SymbolForCPython> = mutableSetOf()
): UState<PythonType, PropertyOfPythonObject, PythonCallable, SymbolicHandlerEvent<Any>>(ctx, callStack, pathConstraints, memory, listOf(uModel), path) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): PythonExecutionState {
        val newPathConstraints = newConstraints ?: pathConstraints.clone()
        val newMemory = memory.clone(newPathConstraints.typeConstraints)
        return PythonExecutionState(
            ctx,
            pythonCallable,
            inputSymbols,
            newPathConstraints,
            newMemory,
            pyModel.uModel,
            callStack,
            path,
            delayedForks,
            mocks.toMutableMap(),  // copy
            mockedObjects.toMutableSet()  // copy
        )
    }
    override val isExceptional: Boolean = false  // TODO
    val meta = PythonExecutionStateMeta()
    val pyModel: PyModel
        get() = PyModel(models.first())
    val lastHandlerEvent: SymbolicHandlerEvent<Any>?
        get() = if (path.isEmpty()) null else path.last()

    // TODO: here we will use Python type hints to prioritize concrete types
    fun makeTypeRating(delayedFork: DelayedFork): List<PythonType> {
        val res = delayedFork.possibleTypes.take(MAX_CONCRETE_TYPES_TO_CONSIDER).toList()
        require(res.first() == TypeOfVirtualObject)
        return res.drop(1)
    }

    fun mock(what: MockHeader): MockResult {
        val cached = mocks[what]
        if (cached != null)
            return MockResult(UninterpretedSymbolicPythonObject(cached), false, cached)
        val (result, newMocker) = memory.mocker.call(what.method, what.args.map { it.obj.address }.asSequence(), ctx.addressSort)
        memory.mocker = newMocker
        mocks[what] = result
        what.methodOwner?.let { mockedObjects.add(it) }
        return MockResult(UninterpretedSymbolicPythonObject(result), true, result)
    }
}

class DelayedFork(
    val state: PythonExecutionState,
    val symbol: UninterpretedSymbolicPythonObject,
    val possibleTypes: UTypeStream<PythonType>,
    val delayedForkPrefix: PersistentList<DelayedFork>
)

data class MockHeader(
    val method: TypeMethod,
    val args: List<SymbolForCPython>,
    var methodOwner: SymbolForCPython?
)

data class MockResult(
    val mockedObject: UninterpretedSymbolicPythonObject,
    val isNew: Boolean,
    val mockSymbol: UMockSymbol<UAddressSort>
)

class PythonExecutionStateMeta {
    var extractedFrom: UPathSelector<PythonExecutionState>? = null
    var wasExecuted: Boolean = false
    var modelDied: Boolean = false
    var objectsWithoutConcreteTypes: Set<VirtualPythonObject>? = null
    var lastConverter: ConverterToPythonObject? = null
    var generatedFrom: String = ""  // for debugging only
}