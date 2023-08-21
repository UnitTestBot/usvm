package org.usvm.machine.interpreters.operations

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.constructListIterator
import org.usvm.language.types.PythonType
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateListKt(context: ConcolicRunContext, elements: Stream<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(context, elements.asSequence().toList(), context.typeSystem.pythonList)

fun handlerListGetSizeKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    list.addSupertype(context, typeSystem.pythonList)
    @Suppress("unchecked_cast")
    val listSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, typeSystem.pythonList)) as UExpr<KIntSort>
    return constructInt(context, listSize)
}

private fun resolveIndex(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UArrayIndexLValue<PythonType>? {
    if (context.curState == null)
        return null
    with (context.ctx) {
        val typeSystem = context.typeSystem
        index.addSupertype(context, typeSystem.pythonInt)
        list.addSupertype(context, typeSystem.pythonList)

        @Suppress("unchecked_cast")
        val listSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, typeSystem.pythonList)) as UExpr<KIntSort>
        val indexValue = index.getIntContent(context)

        val indexCond = mkAnd(indexValue lt listSize, mkArithUnaryMinus(listSize) le indexValue)
        myFork(context, indexCond)

        if (context.curState!!.pyModel.eval(indexCond).isFalse)
            return null

        val positiveIndex = mkAnd(indexValue lt listSize, mkIntNum(0) le indexValue)
        myFork(context, positiveIndex)

        return if (context.curState!!.pyModel.eval(positiveIndex).isTrue) {
            UArrayIndexLValue(addressSort, list.address, indexValue, typeSystem.pythonList)
        } else {
            val negativeIndex = mkAnd(indexValue lt mkIntNum(0), mkArithUnaryMinus(listSize) le indexValue)
            require(context.curState!!.pyModel.eval(negativeIndex).isTrue)
            UArrayIndexLValue(addressSort, list.address, mkArithAdd(indexValue, listSize), typeSystem.pythonList)
        }
    }
}

fun handlerListGetItemKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(context.ctx) {
    if (context.curState == null)
        return null
    val lvalue = resolveIndex(context, list, index) ?: return null

    @Suppress("unchecked_cast")
    val elemAddr = context.curState!!.memory.read(lvalue) as UHeapRef

    myAssert(context, mkHeapRefEq(list.address, elemAddr).not())  // to avoid recursive lists

    return UninterpretedSymbolicPythonObject(elemAddr, context.typeSystem)
}


fun handlerListSetItemKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject, value: UninterpretedSymbolicPythonObject) {
    if (context.curState == null)
        return
    val lvalue = resolveIndex(context, list, index) ?: return
    context.curState!!.memory.write(lvalue, value.address)
}


fun handlerListExtendKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    with (context.ctx) {
        list.addSupertype(context, typeSystem.pythonList)
        tuple.addSupertype(context, typeSystem.pythonTuple)
        @Suppress("unchecked_cast")
        val currentSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, typeSystem.pythonList)) as UExpr<KIntSort>
        @Suppress("unchecked_cast")
        val tupleSize = context.curState!!.memory.read(UArrayLengthLValue(tuple.address, typeSystem.pythonTuple)) as UExpr<KIntSort>
        // TODO: type: list or tuple?
        context.curState!!.memory.memcpy(tuple.address, list.address, typeSystem.pythonList, addressSort, mkIntNum(0), currentSize, tupleSize)
        val newSize = mkArithAdd(currentSize, tupleSize)
        context.curState!!.memory.write(UArrayLengthLValue(list.address, typeSystem.pythonList), newSize)
        return list
    }
}

fun handlerListAppendKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, elem: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    with (context.ctx) {
        list.addSupertype(context, typeSystem.pythonList)
        @Suppress("unchecked_cast")
        val currentSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, typeSystem.pythonList)) as UExpr<KIntSort>
        context.curState!!.memory.write(UArrayIndexLValue(addressSort, list.address, currentSize, typeSystem.pythonList), elem.address)
        context.curState!!.memory.write(UArrayLengthLValue(list.address, typeSystem.pythonList), mkArithAdd(currentSize, mkIntNum(1)))
        return list
    }
}

fun handlerListIterKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    list.addSupertype(context, typeSystem.pythonList)
    return constructListIterator(context, list)
}

fun handlerListIteratorNextKt(context: ConcolicRunContext, iterator: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(context.ctx) {
    if (context.curState == null)
        return null

    val typeSystem = context.typeSystem
    val (listAddress, index) = iterator.getListIteratorContent(context)
    @Suppress("unchecked_cast")
    val listSize = context.curState!!.memory.read(UArrayLengthLValue(listAddress, typeSystem.pythonList)) as UExpr<KIntSort>
    val indexCond = index lt listSize
    myFork(context, indexCond)
    if (context.curState!!.pyModel.eval(indexCond).isFalse)
        return null

    iterator.increaseListIteratorCounter(context)

    @Suppress("unchecked_cast")
    val elemAddr = context.curState!!.memory.read(UArrayIndexLValue(addressSort, listAddress, index, typeSystem.pythonList)) as UHeapRef
    return UninterpretedSymbolicPythonObject(elemAddr, typeSystem)
}