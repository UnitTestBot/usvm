package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.api.allocateArray
import org.usvm.api.collection.ListCollectionApi.symbolicListInsert
import org.usvm.api.memcpy
import org.usvm.api.writeArrayLength
import org.usvm.isFalse
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructListIterator
import org.usvm.machine.symbolicobjects.memory.extendArrayConstraints
import org.usvm.machine.symbolicobjects.memory.getIntContent
import org.usvm.machine.symbolicobjects.memory.getListIteratorContent
import org.usvm.machine.symbolicobjects.memory.increaseListIteratorCounter
import org.usvm.machine.symbolicobjects.memory.readArrayElement
import org.usvm.machine.symbolicobjects.memory.readArrayLength
import org.usvm.machine.symbolicobjects.memory.writeArrayElement
import org.usvm.machine.types.ArrayType
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerCreateListKt(
    ctx: ConcolicRunContext,
    elements: Stream<UninterpretedSymbolicPythonObject>,
): UninterpretedSymbolicPythonObject? =
    createIterable(ctx, elements.asSequence().toList(), ctx.typeSystem.pythonList)

fun handlerListGetSizeKt(
    context: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    getArraySize(context, list, context.typeSystem.pythonList)

fun handlerListGetItemKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val indexInt = resolveSequenceIndex(ctx, list, index, ctx.typeSystem.pythonList) ?: return null
    return list.readArrayElement(ctx, indexInt)
}

fun handlerListSetItemKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    if (ctx.curState == null) {
        return
    }
    val indexInt = resolveSequenceIndex(ctx, list, index, ctx.typeSystem.pythonList) ?: return
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
    with(ctx.ctx) {
        val leftSize = left.readArrayLength(ctx)
        val rightSize = right.readArrayLength(ctx)
        ctx.extractCurState().memory.writeArrayLength(dst.address, mkArithAdd(leftSize, rightSize), ArrayType, intSort)
        ctx.extractCurState().memory.memcpy(
            left.address,
            dst.address,
            ArrayType,
            addressSort,
            mkIntNum(0),
            mkIntNum(0),
            leftSize
        )
        ctx.extractCurState().memory.memcpy(
            right.address,
            dst.address,
            ArrayType,
            addressSort,
            mkIntNum(0),
            leftSize,
            rightSize
        )
    }
}

fun handlerListExtendKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    iterable: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    list.addSupertypeSoft(ctx, typeSystem.pythonList)
    iterable.addSupertypeSoft(ctx, ArrayType)
    listConcat(ctx, list, iterable, list)
    return list
}

fun handlerListConcatKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    if (right.getTypeIfDefined(ctx) != typeSystem.pythonList || left.getTypeIfDefined(ctx) != typeSystem.pythonList) {
        return null
    }
    with(ctx.ctx) {
        val resultAddress = ctx.extractCurState().memory.allocateArray(ArrayType, intSort, mkIntNum(0))
        ctx.extractCurState().memory.types.allocate(resultAddress.address, typeSystem.pythonList)
        val result = UninterpretedSymbolicPythonObject(resultAddress, typeSystem)
        listConcat(ctx, left, right, result)
        return result
    }
}

fun handlerListInplaceConcatKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    if (right.getTypeIfDefined(ctx) != typeSystem.pythonList || left.getTypeIfDefined(ctx) != typeSystem.pythonList) {
        return null
    }
    listConcat(ctx, left, right, left)
    return left
}

fun handlerListAppendKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    elem: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    if (list.getTypeIfDefined(ctx) != typeSystem.pythonList) {
        return null
    }
    with(ctx.ctx) {
        val currentSize = list.readArrayLength(ctx)
        list.writeArrayElement(ctx, currentSize, elem)
        ctx.extractCurState()
            .memory
            .writeArrayLength(list.address, mkArithAdd(currentSize, mkIntNum(1)), ArrayType, intSort)
        return list
    }
}

fun handlerListIterKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    list.addSupertype(ctx, typeSystem.pythonList)
    return constructListIterator(ctx, list)
}

fun handlerListIteratorNextKt(
    ctx: ConcolicRunContext,
    iterator: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? = with(
    ctx.ctx
) {
    if (ctx.curState == null) {
        return null
    }

    val typeSystem = ctx.typeSystem
    val (listAddress, index) = iterator.getListIteratorContent(ctx)
    val listSize = UninterpretedSymbolicPythonObject(listAddress, ctx.typeSystem).readArrayLength(ctx)
    val indexCond = index lt listSize
    pyFork(ctx, indexCond)
    if (ctx.extractCurState().pyModel.eval(indexCond).isFalse) {
        return null
    }

    iterator.increaseListIteratorCounter(ctx)
    val list = UninterpretedSymbolicPythonObject(listAddress, typeSystem)
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
        pyFork(ctx, sizeCond)
        if (ctx.modelHolder.model.eval(sizeCond).isFalse) {
            return null
        }
        val newSize = mkArithSub(listSize, mkIntNum(1))
        val result = list.readArrayElement(ctx, ind ?: newSize)
        ctx.extractCurState().memory.writeArrayLength(list.address, newSize, ArrayType, intSort)
        return result
    }
}

fun handlerListPopKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    if (list.getTypeIfDefined(ctx) != ctx.typeSystem.pythonList) {
        return null
    }

    return listPop(ctx, list)
}

fun handlerListPopIndKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    ind: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    if (list.getTypeIfDefined(ctx) != ctx.typeSystem.pythonList) {
        return null
    }
    ind.addSupertype(ctx, ctx.typeSystem.pythonInt)
    return listPop(ctx, list, ind.getIntContent(ctx))
}

fun handlerListInsertKt(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
    ind: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    if (list.getTypeIfDefined(ctx) != ctx.typeSystem.pythonList) {
        return
    }
    ind.addSupertype(ctx, ctx.typeSystem.pythonInt)

    with(ctx.ctx) {
        val listSize = list.readArrayLength(ctx)
        val indValueRaw = ind.getIntContent(ctx)
        val indValue = mkIte(
            indValueRaw lt listSize,
            indValueRaw,
            listSize
        )
        ctx.extractCurState().symbolicListInsert(list.address, ArrayType, addressSort, indValue, value.address)
        list.writeArrayElement(ctx, indValue, value) // to assert element constraints
    }
}

fun handlerListClearKt(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject) {
    ctx.curState ?: return
    if (list.getTypeIfDefined(ctx) != ctx.typeSystem.pythonList) {
        return
    }
    ctx.extractCurState().memory.writeArrayLength(list.address, ctx.ctx.mkIntNum(0), ArrayType, ctx.ctx.intSort)
}
