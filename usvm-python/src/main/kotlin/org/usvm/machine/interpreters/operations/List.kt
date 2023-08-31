package org.usvm.machine.interpreters.operations

import org.usvm.*
import org.usvm.api.*
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayType
import org.usvm.language.types.PythonType
import org.usvm.machine.symbolicobjects.*
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateListKt(ctx: ConcolicRunContext, elements: Stream<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(ctx, elements.asSequence().toList(), ctx.typeSystem.pythonList)

fun handlerListGetSizeKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val typeSystem = context.typeSystem
    val arrayType = ArrayType(typeSystem)
    if (list.getTypeIfDefined(context) != typeSystem.pythonList)
        return null
    val listSize = context.curState!!.memory.readArrayLength(list.address, arrayType)
    return constructInt(context, listSize)
}

private fun resolveIndex(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UArrayIndexLValue<PythonType, UAddressSort>? {
    if (ctx.curState == null)
        return null
    with (ctx.ctx) {
        val typeSystem = ctx.typeSystem
        val arrayType = ArrayType(typeSystem)
        index.addSupertypeSoft(ctx, typeSystem.pythonInt)
        list.addSupertypeSoft(ctx, typeSystem.pythonList)

        val listSize = ctx.curState!!.memory.readArrayLength(list.address, arrayType)
        val indexValue = index.getIntContent(ctx)

        val indexCond = mkAnd(indexValue lt listSize, mkArithUnaryMinus(listSize) le indexValue)
        myFork(ctx, indexCond)

        if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
            return null

        val positiveIndex = mkAnd(indexValue lt listSize, mkIntNum(0) le indexValue)
        myFork(ctx, positiveIndex)

        return if (ctx.curState!!.pyModel.eval(positiveIndex).isTrue) {
            UArrayIndexLValue(addressSort, list.address, indexValue, arrayType)
        } else {
            val negativeIndex = mkAnd(indexValue lt mkIntNum(0), mkArithUnaryMinus(listSize) le indexValue)
            require(ctx.curState!!.pyModel.eval(negativeIndex).isTrue)
            UArrayIndexLValue(addressSort, list.address, mkArithAdd(indexValue, listSize), arrayType)
        }
    }
}

fun handlerListGetItemKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null)
        return null
    val lvalue = resolveIndex(ctx, list, index) ?: return null

    val elemAddr = ctx.curState!!.memory.read(lvalue)
    myAssert(ctx, mkHeapRefEq(list.address, elemAddr).not())  // to avoid recursive lists
    return UninterpretedSymbolicPythonObject(elemAddr, ctx.typeSystem)
}


fun handlerListSetItemKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject, value: UninterpretedSymbolicPythonObject) {
    if (ctx.curState == null)
        return
    val lvalue = resolveIndex(ctx, list, index) ?: return
    ctx.curState!!.memory.write(lvalue, value.address, ctx.ctx.trueExpr)
}


private fun listConcat(
    ctx: ConcolicRunContext,
    left: UHeapRef,
    right: UHeapRef,
    dst: UHeapRef
) {
    val typeSystem = ctx.typeSystem
    val arrayType = ArrayType(typeSystem)
    with (ctx.ctx) {
        val leftSize = ctx.curState!!.memory.readArrayLength(left, arrayType)
        val rightSize = ctx.curState!!.memory.readArrayLength(right, arrayType)
        ctx.curState!!.memory.writeArrayLength(dst, mkArithAdd(leftSize, rightSize), arrayType)
        ctx.curState!!.memory.memcpy(left, dst, arrayType, addressSort, mkIntNum(0), mkIntNum(0), leftSize)
        ctx.curState!!.memory.memcpy(right, dst, arrayType, addressSort, mkIntNum(0), leftSize, rightSize)
    }
}

fun handlerListExtendKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    list.addSupertypeSoft(ctx, typeSystem.pythonList)
    tuple.addSupertypeSoft(ctx, typeSystem.pythonTuple)
    listConcat(ctx, list.address, tuple.address, list.address)
    return list
}

fun handlerListConcatKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    val arrayType = ArrayType(typeSystem)
    if (right.getTypeIfDefined(ctx) != typeSystem.pythonList || left.getTypeIfDefined(ctx) != typeSystem.pythonList)
        return null
    with (ctx.ctx) {
        val result = ctx.curState!!.memory.allocateArray(arrayType, mkIntNum(0))
        listConcat(ctx, left.address, right.address, result)
        return UninterpretedSymbolicPythonObject(result, typeSystem)
    }
}

fun handlerListInplaceConcatKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    if (right.getTypeIfDefined(ctx) != typeSystem.pythonList || left.getTypeIfDefined(ctx) != typeSystem.pythonList)
        return null
    listConcat(ctx, left.address, right.address, left.address)
    return left
}

fun handlerListAppendKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, elem: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    val arrayType = ArrayType(typeSystem)
    if (list.getTypeIfDefined(ctx) != typeSystem.pythonList)
        return null
    with (ctx.ctx) {
        val currentSize = ctx.curState!!.memory.readArrayLength(list.address, arrayType)
        ctx.curState!!.memory.writeArrayIndex(list.address, currentSize, arrayType, addressSort, elem.address, trueExpr)
        ctx.curState!!.memory.writeArrayLength(list.address, mkArithAdd(currentSize, mkIntNum(1)), arrayType)
        return list
    }
}

fun handlerListIterKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    list.addSupertype(ctx, typeSystem.pythonList)
    return constructListIterator(ctx, list)
}

fun handlerListIteratorNextKt(ctx: ConcolicRunContext, iterator: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null)
        return null

    val typeSystem = ctx.typeSystem
    val arrayType = ArrayType(typeSystem)
    val (listAddress, index) = iterator.getListIteratorContent(ctx)
    val listSize = ctx.curState!!.memory.readArrayLength(listAddress, arrayType)
    val indexCond = index lt listSize
    myFork(ctx, indexCond)
    if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
        return null

    iterator.increaseListIteratorCounter(ctx)
    val elemAddr = ctx.curState!!.memory.readArrayIndex(listAddress, index, arrayType, addressSort)
    return UninterpretedSymbolicPythonObject(elemAddr, typeSystem)
}