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
    val callable: Callable,
    pathConstraints: UPathConstraints<PythonType>,
    memory: UMemoryBase<Attribute, PythonType, Callable>,
    models: List<UModel>,
    callStack: UCallStack<Callable, PythonInstruction> = UCallStack(),
    path: PersistentList<PythonInstruction> = persistentListOf()
): UState<PythonType, Attribute, Callable, PythonInstruction>(ctx, callStack, pathConstraints, memory, models, path) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): UState<PythonType, Attribute, Callable, PythonInstruction> {
        val newPathConstraints = newConstraints ?: pathConstraints.clone()
        val newMemory = memory.clone(newPathConstraints.typeConstraints)
        return PythonExecutionState(
            ctx,
            callable,
            newPathConstraints,
            newMemory,
            models,
            callStack,
            path
        )
    }

    var wasExecuted: Boolean = false
}