package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.memory.getEnumerateIndexAndIncrement
import org.usvm.machine.symbolicobjects.memory.getEnumerateIterator
import org.usvm.machine.symbolicobjects.memory.initializeEnumerate
import org.usvm.machine.types.HasTpIter
import java.util.stream.Stream

fun handlerCreateEnumerateKt(
    ctx: ConcolicRunContext,
    iterable: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    iterable.addSupertype(ctx, HasTpIter)
    val typeSystem = ctx.typeSystem
    val iterator: UninterpretedSymbolicPythonObject = when (iterable.getTypeIfDefined(ctx)) {
        null -> {
            addDelayedFork(ctx, iterable, ctx.extractCurState().clone())
            return null
        }
        typeSystem.pythonList -> {
            handlerListIterKt(ctx, iterable) ?: return null
        }
        typeSystem.pythonTuple -> {
            handlerTupleIterKt(ctx, iterable) ?: return null
        }
        else -> {
            return null
        }
    }
    val address = ctx.extractCurState().memory.allocConcrete(ctx.typeSystem.pythonEnumerate)
    val result = UninterpretedSymbolicPythonObject(address, ctx.typeSystem)
    result.initializeEnumerate(ctx, iterator)
    return result
}

fun handlerEnumerateIterKt(
    ctx: ConcolicRunContext,
    enumerate: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    return enumerate
}

fun handlerEnumerateNextKt(
    ctx: ConcolicRunContext,
    enumerate: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val iterator = enumerate.getEnumerateIterator(ctx)
    val typeSystem = ctx.typeSystem
    val item: UninterpretedSymbolicPythonObject = when (iterator.getTypeIfDefined(ctx)) {
        typeSystem.pythonListIteratorType ->
            handlerListIteratorNextKt(ctx, iterator) ?: return null
        typeSystem.pythonTupleIteratorType ->
            handlerTupleIteratorNextKt(ctx, iterator) ?: return null
        else -> return null
    }
    val indexValue = enumerate.getEnumerateIndexAndIncrement(ctx)
    val index = constructInt(ctx, indexValue)
    return handlerCreateTupleKt(ctx, Stream.of(index, item))
}
