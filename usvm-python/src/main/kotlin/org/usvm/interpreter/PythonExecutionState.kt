package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

class PythonExecutionState(
    ctx: UContext,
    callStack: UCallStack<Callable, Instruction> = UCallStack(),
    pathConstraints: UPathConstraints<PythonType> = UPathConstraints(ctx),
    memory: UMemoryBase<Attribute, PythonType, Callable> = UMemoryBase(ctx, pathConstraints.typeConstraints),
    models: List<UModel> = listOf(),
    path: PersistentList<Instruction> = persistentListOf()
): UState<PythonType, Attribute, Callable, Instruction>(ctx, callStack, pathConstraints, memory, models, path) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): UState<PythonType, Attribute, Callable, Instruction> {
        TODO("Not yet implemented")
    }
}