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
    callStack: UCallStack<Callable, Instruction> = UCallStack(),
    pathConstraints: UPathConstraints<PythonType> = UPathConstraints(ctx),
    memory: UMemoryBase<Attribute, PythonType, Callable> =
        UMemoryBase<Attribute, PythonType, Callable>(ctx, pathConstraints.typeConstraints).apply {
            stack.push(callable.numberOfArguments)
        },
    models: List<UModel> = listOf(),
    path: PersistentList<Instruction> = persistentListOf(),
    var wasExecuted: Boolean = false
): UState<PythonType, Attribute, Callable, Instruction>(ctx, callStack, pathConstraints, memory, models, path) {
    override fun clone(newConstraints: UPathConstraints<PythonType>?): UState<PythonType, Attribute, Callable, Instruction> {
        val newPathConstraints = newConstraints ?: pathConstraints.clone()
        return PythonExecutionState(
            ctx,
            callable,
            callStack,
            newPathConstraints,
            memory.clone(newPathConstraints.typeConstraints),
            models,
            path,
            wasExecuted
        )
    }

    val symbols: List<UExpr<*>> = List(callable.numberOfArguments) { memory.read(URegisterRef(ctx.intSort, it)) }
}