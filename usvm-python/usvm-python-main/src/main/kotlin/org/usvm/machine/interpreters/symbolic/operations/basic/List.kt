package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.*
import org.usvm.api.collection.ListCollectionApi.symbolicListInsert
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.language.types.ArrayType
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.symbolicobjects.memory.*
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateListKt(ctx: ConcolicRunContext, elements: Stream<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(ctx, elements.asSequence().toList(), ctx.typeSystem.pythonList)

fun handlerListGetSizeKt(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    getArraySize(context, list, context.typeSystem.pythonList)

fun handlerListGetItemKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    val indexInt = resolveSequenceIndex(ctx, list, index, ctx.typeSystem.pythonList.id) ?: return null
    return list.readArrayElement(ctx, indexInt)
}

fun handlerListSetItemKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, index: UninterpretedSymbolicPythonObject, value: UninterpretedSymbolicPythonObject) {
    if (ctx.curState == null)
        return
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    val indexInt = resolveSequenceIndex(ctx, list, index, ctx.typeSystem.pythonList.id) ?: return
    list.writeArrayElement(ctx, indexInt, value)
}


private fun listConcat(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
    dst: UninterpretedSymbolicPythonObject,
) {
    dst.extendArrayConstraints(ctx, left)
    dst.extendArrayConstraints(ctx, right)
    with (ctx.ctx) {
        val leftSize = left.readArrayLength(ctx)
        val rightSize = right.readArrayLength(ctx)
        ctx.curState!!.memory.writeArrayLength(dst.address, mkArithAdd(leftSize, rightSize), ArrayType, intSort)
        ctx.curState!!.memory.memcpy(left.address, dst.address, ArrayType, addressSort, mkIntNum(0), mkIntNum(0), leftSize)
        ctx.curState!!.memory.memcpy(right.address, dst.address, ArrayType, addressSort, mkIntNum(0), leftSize, rightSize)
    }
}

fun handlerListExtendKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, iterable: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    iterable.addSupertypeSoft(ctx, ArrayType)
    listConcat(ctx, list, iterable, list)
    return list
}

fun handlerListConcatKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    makeConcreteInnerType(ctx, left, ctx.typeSystem.pythonList)
    makeConcreteInnerType(ctx, right, ctx.typeSystem.pythonList)
    with (ctx.ctx) {
        val resultAddress = ctx.curState!!.memory.allocateArray(ArrayType, intSort, mkIntNum(0))
        ctx.curState!!.memory.types.allocate(resultAddress.address, typeSystem.pythonList)
        val result = UninterpretedSymbolicPythonObject(resultAddress, typeSystem)
        listConcat(ctx, left, right, result)
        return result
    }
}

fun handlerListInplaceConcatKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    makeConcreteInnerType(ctx, left, ctx.typeSystem.pythonList)
    makeConcreteInnerType(ctx, right, ctx.typeSystem.pythonList)
    listConcat(ctx, left, right, left)
    return left
}

fun handlerListAppendKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, elem: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    with (ctx.ctx) {
        val currentSize = list.readArrayLength(ctx)
        list.writeArrayElement(ctx, currentSize, elem)
        ctx.curState!!.memory.writeArrayLength(list.address, mkArithAdd(currentSize, mkIntNum(1)), ArrayType, intSort)
        return list
    }
}

fun handlerListIterKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    require(list.getTypeIfDefined(ctx) != null) {
        "Type must be already resolved"
    }
    return constructListIterator(ctx, list)
}

fun handlerListIteratorNextKt(ctx: ConcolicRunContext, iterator: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null)
        return null

    val typeSystem = ctx.typeSystem
    val (listAddress, index) = iterator.getListIteratorContent(ctx)
    val listSize = UninterpretedSymbolicPythonObject(listAddress, ctx.typeSystem).readArrayLength(ctx)
    val indexCond = index lt listSize
    myFork(ctx, indexCond)
    if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
        return null

    iterator.increaseListIteratorCounter(ctx)
    val list = UninterpretedSymbolicPythonObject(listAddress, typeSystem)
    typeCheck(ctx, list, typeSystem.pythonList)
    return list.readArrayElement(ctx, index)
}

private fun listPop(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    ind: UExpr<KIntSort>? = null,
): UninterpretedSymbolicPythonObject? {
    with(ctx.ctx) {
        val listSize = list.readArrayLength(ctx)
        val sizeCond = listSize gt (ind ?: mkIntNum(0))
        myFork(ctx, sizeCond)
        if (ctx.modelHolder.model.eval(sizeCond).isFalse)
            return null
        val newSize = mkArithSub(listSize, mkIntNum(1))
        val result = list.readArrayElement(ctx, ind ?: newSize)
        ctx.curState!!.memory.writeArrayLength(list.address, newSize, ArrayType, intSort)
        return result
    }
}

fun handlerListPopKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    return listPop(ctx, list)
}

fun handlerListPopIndKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject, ind: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    ind.addSupertype(ctx, ctx.typeSystem.pythonInt)
    return listPop(ctx, list, ind.getIntContent(ctx))
}

fun handlerListInsertKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    ind: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    makeConcreteInnerType(ctx, list, ctx.typeSystem.pythonList)
    ind.addSupertype(ctx, ctx.typeSystem.pythonInt)

    with(ctx.ctx) {
        val listSize = list.readArrayLength(ctx)
        val indValueRaw = ind.getIntContent(ctx)
        val indValue = mkIte(
            indValueRaw lt listSize,
            indValueRaw,
            listSize
        )
        ctx.curState!!.symbolicListInsert(list.address, ArrayType, addressSort, indValue, value.address)
        list.writeArrayElement(ctx, indValue, value)  // to assert element constraints
    }
}

fun handlerListClearKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject) {
    ctx.curState ?: return
    if (list.getTypeIfDefined(ctx) != ctx.typeSystem.pythonList)
        return
    ctx.curState!!.memory.writeArrayLength(list.address, ctx.ctx.mkIntNum(0), ArrayType, ctx.ctx.intSort)
}