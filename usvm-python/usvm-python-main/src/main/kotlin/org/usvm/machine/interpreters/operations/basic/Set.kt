package org.usvm.machine.interpreters.operations.basic

import org.usvm.UBoolExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.*
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerSetContainsKt(
    ctx: ConcolicRunContext,
    set: UninterpretedSymbolicPythonObject,
    elem: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    set.addSupertype(ctx, ctx.typeSystem.pythonSet)
    addHashableTypeConstrains(ctx, elem)
    val elemType = elem.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    val result: UBoolExpr = when (elemType) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> return  // TODO
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = elem.getToIntContent(ctx) ?: return
            set.setContainsInt(ctx, intValue)
        }
        else -> {
            if (elemType == null) {
                forkOnUnknownHashableType(ctx, elem)
            }
            set.setContainsRef(ctx, elem)
        }
    }
    myFork(ctx, result)
}

private fun addItem(
    ctx: ConcolicRunContext,
    set: UninterpretedSymbolicPythonObject,
    elem: UninterpretedSymbolicPythonObject
) {
    val elemType = elem.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    when (elemType) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> return  // TODO
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = elem.getToIntContent(ctx) ?: return
            set.addIntToSet(ctx, intValue)
        }
        else -> {
            if (elemType == null) {
                forkOnUnknownHashableType(ctx, elem)
            }
            set.addRefToSet(ctx, elem)
        }
    }
}

fun handlerSetAddKt(
    ctx: ConcolicRunContext,
    set: UninterpretedSymbolicPythonObject,
    elem: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    set.addSupertype(ctx, ctx.typeSystem.pythonSet)
    addHashableTypeConstrains(ctx, elem)
    addItem(ctx, set, elem)
}

fun handlerCreateEmptySetKt(ctx: ConcolicRunContext): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val address = ctx.curState!!.memory.allocConcrete(ctx.typeSystem.pythonSet)
    return UninterpretedSymbolicPythonObject(address, ctx.typeSystem)
}


fun handlerCreateSetKt(
    ctx: ConcolicRunContext,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val elems = elemsStream.asSequence().toList()
    val result = handlerCreateEmptySetKt(ctx) ?: return null
    elems.forEach { elem ->
        addHashableTypeConstrains(ctx, elem)
        addItem(ctx, result, elem)
    }
    return result
}