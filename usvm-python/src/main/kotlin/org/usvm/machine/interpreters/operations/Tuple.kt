package org.usvm.machine.interpreters.operations

import org.usvm.*
import org.usvm.api.readArrayLength
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayType
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructTupleIterator
import org.usvm.machine.symbolicobjects.getTupleIteratorContent
import org.usvm.machine.symbolicobjects.increaseTupleIteratorCounter
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateTupleKt(ctx: ConcolicRunContext, elements: Stream<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(ctx, elements.asSequence().toList(), ctx.typeSystem.pythonTuple)

fun handlerTupleIterKt(ctx: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    tuple.addSupertype(ctx, typeSystem.pythonTuple)
    return constructTupleIterator(ctx, tuple)
}

fun handlerTupleIteratorNextKt(
    ctx: ConcolicRunContext,
    iterator: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    val (tuple, index) = iterator.getTupleIteratorContent(ctx)
    val tupleSize = ctx.curState!!.memory.readArrayLength(tuple, ArrayType)
    val indexCond = index lt tupleSize
    if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
        return null
    iterator.increaseTupleIteratorCounter(ctx)
    val tupleObject = UninterpretedSymbolicPythonObject(tuple, typeSystem)
    return tupleObject.readElement(ctx, index)
}

fun handlerUnpackKt(ctx: ConcolicRunContext, iterable: UninterpretedSymbolicPythonObject, count: Int) = with(ctx.ctx) {
    if (ctx.curState == null)
        return
    val typeSystem = ctx.typeSystem
    if (iterable.getTypeIfDefined(ctx) != typeSystem.pythonTuple) {
        myFork(ctx, iterable.evalIs(ctx, typeSystem.pythonTuple))
        return
    }
    val tupleSize = ctx.curState!!.memory.readArrayLength(iterable.address, ArrayType)
    myFork(ctx, tupleSize eq mkIntNum(count))
}