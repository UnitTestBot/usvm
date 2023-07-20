package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.operations.tracing.SymbolicHandlerEvent
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase
import org.usvm.types.UTypeStream

class PythonExecutionState(
    private val ctx: UContext,
    private val pythonCallable: PythonUnpinnedCallable,
    val inputSymbols: List<SymbolForCPython>,
    pathConstraints: UPathConstraints<PythonType>,
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    uModel: UModelBase<PropertyOfPythonObject, PythonType>,
    callStack: UCallStack<PythonCallable, SymbolicHandlerEvent<Any>> = UCallStack(),
    path: PersistentList<SymbolicHandlerEvent<Any>> = persistentListOf(),
    var delayedForks: PersistentList<DelayedFork> = persistentListOf()
): UState<PythonType, PropertyOfPythonObject, PythonCallable, SymbolicHandlerEvent<Any>>(ctx, callStack, pathConstraints, memory, listOf(uModel), path) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): UState<PythonType, PropertyOfPythonObject, PythonCallable, SymbolicHandlerEvent<Any>> {
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
            delayedForks
        )
    }

    var extractedFrom: UPathSelector<PythonExecutionState>? = null

    val pyModel: PyModel
        get() = PyModel(models.first())

    var wasExecuted: Boolean = false
    var modelDied: Boolean = false
    val lastHandlerEvent: SymbolicHandlerEvent<Any>?
        get() = if (path.isEmpty()) null else path.last()

    // TODO: here we will use Python type hints to prioritize concrete types
    fun makeTypeRating(delayedFork: DelayedFork): UTypeStream<PythonType> {
        return pyModel.uModel.typeStreamOf(pyModel.eval(delayedFork.symbol.obj.address))
    }

    var symbolsWithoutConcreteTypes: Collection<SymbolForCPython>? = null
    var fromStateWithVirtualObjectAndWithoutDelayedForks: PythonExecutionState? = null
}

data class DelayedFork(
    val state: PythonExecutionState,
    val symbol: SymbolForCPython
)