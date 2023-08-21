package org.usvm.machine.interpreters.operations

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructTupleIterator
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
    @Suppress("unchecked_cast")
    val tupleSize = ctx.curState!!.memory.read(UArrayLengthLValue(tuple, typeSystem.pythonTuple)) as UExpr<KIntSort>
    val indexCond = index lt tupleSize
    if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
        return null
    iterator.increaseTupleIteratorCounter(ctx)
    @Suppress("unchecked_cast")
    val address = ctx.curState!!.memory.read(UArrayIndexLValue(addressSort, tuple, index, typeSystem.pythonTuple)) as UHeapRef
    return UninterpretedSymbolicPythonObject(address, typeSystem)
}