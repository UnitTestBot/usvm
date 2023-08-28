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
    if (list.getTypeIfDefined(context) != typeSystem.pythonList)
        return null
    @Suppress("unchecked_cast")
    val listSize = context.curState!!.memory.read(UArrayLengthLValue(list.address, typeSystem.pythonList)) as UExpr<KIntSort>
    return constructInt(context, listSize)
}

private fun resolveIndex(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UArrayIndexLValue<PythonType>? {
    if (context.curState == null)
        return null
    with (context.ctx) {
        val typeSystem = context.typeSystem
        index.addSupertypeSoft(context, typeSystem.pythonInt)
        list.addSupertypeSoft(context, typeSystem.pythonList)

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


/*
context.curState!!.memory.read(UArrayIndexLValue(addressSort, result, mkIntNum(1), typeSystem.pythonList))
 */

private fun listConcat(
    context: ConcolicRunContext,
    left: UHeapRef,
    leftType: PythonType,
    right: UHeapRef,
    rightType: PythonType,
    dst: UHeapRef
) {
    val typeSystem = context.typeSystem
    with (context.ctx) {
        @Suppress("unchecked_cast")
        val leftSize = context.curState!!.memory.read(UArrayLengthLValue(left, leftType)) as UExpr<KIntSort>
        @Suppress("unchecked_cast")
        val rightSize = context.curState!!.memory.read(UArrayLengthLValue(right, rightType)) as UExpr<KIntSort>
        context.curState!!.memory.write(UArrayLengthLValue(dst, typeSystem.pythonList), mkArithAdd(leftSize, rightSize))
        context.curState!!.memory.memcpy(left, dst, typeSystem.pythonList, addressSort, mkIntNum(0), mkIntNum(0), leftSize)
        context.curState!!.memory.memcpy(right, dst, typeSystem.pythonList, addressSort, mkIntNum(0), leftSize, rightSize)
    }
}

fun handlerListExtendKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    context.curState ?: return null
    val typeSystem = context.typeSystem
    list.addSupertypeSoft(context, typeSystem.pythonList)
    tuple.addSupertypeSoft(context, typeSystem.pythonTuple)
    listConcat(context, list.address, typeSystem.pythonList, tuple.address, typeSystem.pythonTuple, list.address)
    return list
}

fun handlerListConcatKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    with (context.ctx) {
        left.addSupertypeSoft(context, typeSystem.pythonList)
        right.addSupertypeSoft(context, typeSystem.pythonList)
        val result = context.curState!!.memory.malloc(typeSystem.pythonList, mkIntNum(0))
        listConcat(context, left.address, typeSystem.pythonList, right.address, typeSystem.pythonList, result)
        return UninterpretedSymbolicPythonObject(result, typeSystem)
    }
}

fun handlerListInplaceConcatKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    if (right.getTypeIfDefined(context) != typeSystem.pythonList || left.getTypeIfDefined(context) != typeSystem.pythonList)
        return null
    listConcat(context, left.address, typeSystem.pythonList, right.address, typeSystem.pythonList, left.address)
    return left
}

fun handlerListAppendKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, elem: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    if (list.getTypeIfDefined(context) != typeSystem.pythonList)
        return null
    with (context.ctx) {
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