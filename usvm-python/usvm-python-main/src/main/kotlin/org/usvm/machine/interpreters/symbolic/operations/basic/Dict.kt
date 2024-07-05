package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.UBoolExpr
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.dictContainsInt
import org.usvm.machine.symbolicobjects.memory.dictContainsRef
import org.usvm.machine.symbolicobjects.memory.dictIsEmpty
import org.usvm.machine.symbolicobjects.memory.getToIntContent
import org.usvm.machine.symbolicobjects.memory.readArrayElement
import org.usvm.machine.symbolicobjects.memory.readDictIntElement
import org.usvm.machine.symbolicobjects.memory.readDictRefElement
import org.usvm.machine.symbolicobjects.memory.writeDictIntElement
import org.usvm.machine.symbolicobjects.memory.writeDictRefElement
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerDictGetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    addHashableTypeConstrains(ctx, key)
    val keyType = key.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    return when (keyType) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> {
            null // TODO
        }
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = key.getToIntContent(ctx) ?: return null
            val containsCond = dict.dictContainsInt(ctx, intValue)
            pyFork(ctx, containsCond)
            if (ctx.modelHolder.model.eval(containsCond).isTrue) {
                dict.readDictIntElement(ctx, intValue)
            } else {
                null
            }
        }
        else -> {
            if (keyType == null) {
                forkOnUnknownHashableType(ctx, key)
            }
            val containsCond = dict.dictContainsRef(ctx, key)
            pyFork(ctx, containsCond)
            if (ctx.modelHolder.model.eval(containsCond).isTrue) {
                dict.readDictRefElement(ctx, key)
            } else {
                null
            }
        }
    }
}

private fun setItem(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    val typeSystem = ctx.typeSystem
    when (val keyType = key.getTypeIfDefined(ctx)) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> { // TODO
            Unit
        }
        typeSystem.pythonInt -> {
            val intValue = key.getToIntContent(ctx) ?: return
            dict.writeDictIntElement(ctx, intValue, value)
        }
        else -> {
            if (keyType == null) {
                forkOnUnknownHashableType(ctx, key)
            }
            dict.writeDictRefElement(ctx, key, value)
        }
    }
}

fun handlerDictSetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    addHashableTypeConstrains(ctx, key)
    val typeSystem = ctx.typeSystem
    dict.addSupertypeSoft(ctx, typeSystem.pythonDict)
    setItem(ctx, dict, key, value)
}

fun handlerCreateDictKt(
    ctx: ConcolicRunContext,
    keysStream: Stream<UninterpretedSymbolicPythonObject>,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val keys = keysStream.asSequence().toList()
    val elems = elemsStream.asSequence().toList()
    require(keys.size == elems.size)
    val typeSystem = ctx.typeSystem
    val ref = ctx.extractCurState().memory.allocConcrete(typeSystem.pythonDict)
    val result = UninterpretedSymbolicPythonObject(ref, ctx.typeSystem)
    (keys zip elems).forEach { (key, elem) ->
        addHashableTypeConstrains(ctx, key)
        setItem(ctx, result, key, elem)
    }
    return result
}

fun handlerCreateDictConstKeyKt(
    ctx: ConcolicRunContext,
    keys: UninterpretedSymbolicPythonObject,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val elems = elemsStream.asSequence().toList()
    val typeSystem = ctx.typeSystem
    keys.addSupertypeSoft(ctx, typeSystem.pythonTuple)
    val ref = ctx.extractCurState().memory.allocConcrete(typeSystem.pythonDict)
    val result = UninterpretedSymbolicPythonObject(ref, ctx.typeSystem)
    elems.forEachIndexed { index, elem ->
        val key = keys.readArrayElement(ctx, ctx.ctx.mkIntNum(index))
        addHashableTypeConstrains(ctx, key)
        setItem(ctx, result, key, elem)
    }
    return result
}

fun handlerDictContainsKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    addHashableTypeConstrains(ctx, key)
    val keyType = key.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    val result: UBoolExpr = when (keyType) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> { // TODO
            return
        }
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = key.getToIntContent(ctx) ?: return
            dict.dictContainsInt(ctx, intValue)
        }
        else -> {
            if (keyType == null) {
                forkOnUnknownHashableType(ctx, key)
            }
            dict.dictContainsRef(ctx, key)
        }
    }
    pyFork(ctx, result)
}

fun handlerDictIterKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    pyFork(ctx, dict.dictIsEmpty(ctx))
}

fun handlerDictLengthKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    pyFork(ctx, dict.dictIsEmpty(ctx))
}
