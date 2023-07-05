package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase

class PythonExecutionState(
    ctx: UContext,
    private val pythonCallable: PythonUnpinnedCallable,
    val inputSymbols: List<SymbolForCPython>,
    pathConstraints: UPathConstraints<PythonType>,
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    uModel: UModelBase<PropertyOfPythonObject, PythonType>,
    callStack: UCallStack<PythonCallable, SymbolicHandlerEvent<Any>> = UCallStack(),
    path: PersistentList<SymbolicHandlerEvent<Any>> = persistentListOf()
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
            path
        )
    }

    val pyModel: PyModel
        get() = PyModel(models.first())

    var wasExecuted: Boolean = false
    val lastHandlerEvent: SymbolicHandlerEvent<Any>?
        get() = if (path.isEmpty()) null else path.last()
}