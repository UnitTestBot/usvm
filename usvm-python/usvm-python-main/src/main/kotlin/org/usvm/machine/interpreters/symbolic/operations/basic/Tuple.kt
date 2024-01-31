package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.symbolicobjects.memory.getTupleIteratorContent
import org.usvm.machine.symbolicobjects.memory.increaseTupleIteratorCounter
import org.usvm.machine.symbolicobjects.memory.readArrayElement
import org.usvm.machine.symbolicobjects.memory.readArrayLength
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateTupleKt(ctx: ConcolicRunContext, elements: Stream<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(ctx, elements.asSequence().toList(), ctx.typeSystem.pythonTuple)

fun handlerTupleIterKt(ctx: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    makeConcreteInnerType(ctx, tuple, typeSystem.pythonTuple)
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
    val tupleSize = UninterpretedSymbolicPythonObject(tuple, ctx.typeSystem).readArrayLength(ctx)
    val indexCond = index lt tupleSize
    myFork(ctx, indexCond)
    if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
        return null
    iterator.increaseTupleIteratorCounter(ctx)
    val tupleObject = UninterpretedSymbolicPythonObject(tuple, typeSystem)
    typeCheck(ctx, tupleObject, typeSystem.pythonTuple)
    return tupleObject.readArrayElement(ctx, index)
}

fun handlerUnpackKt(ctx: ConcolicRunContext, iterable: UninterpretedSymbolicPythonObject, count: Int) = with(ctx.ctx) {
    if (ctx.curState == null)
        return
    val typeSystem = ctx.typeSystem
    val type = iterable.getTypeIfDefined(ctx)
    if (type != null && type !is ArrayLikeConcretePythonType)
        return
    if (type == null) {
        makeConcreteInnerType(ctx, iterable, typeSystem.pythonTuple)
    }
    require(iterable.getTypeIfDefined(ctx) != null)
    val tupleSize = iterable.readArrayLength(ctx)
    val sizeCond = tupleSize eq mkIntNum(count)
    if (ctx.modelHolder.model.eval(sizeCond).isTrue) {
        myAssert(ctx, sizeCond)
    } else {
        myFork(ctx, sizeCond)
    }
}

fun handlerTupleGetSizeKt(context: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    getArraySize(context, tuple, context.typeSystem.pythonTuple)

fun handlerTupleGetItemKt(
    ctx: ConcolicRunContext,
    tuple: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    makeConcreteInnerType(ctx, tuple, ctx.typeSystem.pythonTuple)
    val indexInt = resolveSequenceIndex(ctx, tuple, index, ctx.typeSystem.pythonTuple.id) ?: return null
    return tuple.readArrayElement(ctx, indexInt)
}