package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructTupleIterator
import org.usvm.machine.symbolicobjects.memory.getTupleIteratorContent
import org.usvm.machine.symbolicobjects.memory.increaseTupleIteratorCounter
import org.usvm.machine.symbolicobjects.memory.readArrayElement
import org.usvm.machine.symbolicobjects.memory.readArrayLength
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateTupleKt(
    ctx: ConcolicRunContext,
    elements: Stream<UninterpretedSymbolicPythonObject>,
): UninterpretedSymbolicPythonObject? =
    createIterable(ctx, elements.asSequence().toList(), ctx.typeSystem.pythonTuple)

fun handlerTupleIterKt(
    ctx: ConcolicRunContext,
    tuple: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    tuple.addSupertype(ctx, typeSystem.pythonTuple)
    return constructTupleIterator(ctx, tuple)
}

fun handlerTupleIteratorNextKt(
    ctx: ConcolicRunContext,
    iterator: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    val (tuple, index) = iterator.getTupleIteratorContent(ctx)
    val tupleSize = UninterpretedSymbolicPythonObject(tuple, ctx.typeSystem).readArrayLength(ctx)
    val indexCond = index lt tupleSize
    pyFork(ctx, indexCond)
    if (ctx.extractCurState().pyModel.eval(indexCond).isFalse) {
        return null
    }
    iterator.increaseTupleIteratorCounter(ctx)
    val tupleObject = UninterpretedSymbolicPythonObject(tuple, typeSystem)
    return tupleObject.readArrayElement(ctx, index)
}

fun handlerUnpackKt(ctx: ConcolicRunContext, iterable: UninterpretedSymbolicPythonObject, count: Int) = with(ctx.ctx) {
    if (ctx.curState == null) {
        return
    }
    val typeSystem = ctx.typeSystem
    if (iterable.getTypeIfDefined(ctx) != typeSystem.pythonTuple) {
        pyFork(ctx, iterable.evalIs(ctx, typeSystem.pythonTuple))
        return
    }
    val tupleSize = iterable.readArrayLength(ctx)
    val sizeCond = tupleSize eq mkIntNum(count)
    if (ctx.modelHolder.model.eval(sizeCond).isTrue) {
        pyAssert(ctx, sizeCond)
    } else {
        pyFork(ctx, sizeCond)
    }
}

fun handlerTupleGetSizeKt(
    context: ConcolicRunContext,
    tuple: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    getArraySize(context, tuple, context.typeSystem.pythonTuple)

fun handlerTupleGetItemKt(
    ctx: ConcolicRunContext,
    tuple: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val indexInt = resolveSequenceIndex(ctx, tuple, index, ctx.typeSystem.pythonTuple) ?: return null
    return tuple.readArrayElement(ctx, indexInt)
}
