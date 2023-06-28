package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

class PythonExecutionState(
    ctx: UContext,
    private val callable: Callable,
    val inputSymbols: List<SymbolForCPython>,
    pathConstraints: UPathConstraints<PythonType>,
    memory: UMemoryBase<Attribute, PythonType, Callable>,
    models: List<UModel>,
    callStack: UCallStack<Callable, SymbolicHandlerEvent<Any>> = UCallStack(),
    path: PersistentList<SymbolicHandlerEvent<Any>> = persistentListOf()
): UState<PythonType, Attribute, Callable, SymbolicHandlerEvent<Any>>(ctx, callStack, pathConstraints, memory, models, path) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): UState<PythonType, Attribute, Callable, SymbolicHandlerEvent<Any>> {
        val newPathConstraints = newConstraints ?: pathConstraints.clone()
        val newMemory = memory.clone(newPathConstraints.typeConstraints)
        return PythonExecutionState(
            ctx,
            callable,
            inputSymbols,
            newPathConstraints,
            newMemory,
            models,
            callStack,
            path
        )
    }

    var wasExecuted: Boolean = false
}