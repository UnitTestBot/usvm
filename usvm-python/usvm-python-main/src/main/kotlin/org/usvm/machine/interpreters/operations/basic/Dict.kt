package org.usvm.machine.interpreters.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.language.types.HasTpHash
import org.usvm.machine.symbolicobjects.*
import java.util.stream.Stream
import kotlin.streams.asSequence

private fun forkOnUnknownType(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
) {
    val clonedState = ctx.curState!!.clone()
    val stateForDelayedFork =
        myAssertOnState(clonedState, ctx.ctx.mkNot(ctx.ctx.mkHeapRefEq(key.address, ctx.ctx.nullRef)))
    stateForDelayedFork?.let { addDelayedFork(ctx, key, it) }
}

fun handlerDictGetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    key.addSupertypeSoft(ctx, HasTpHash)
    val keyType = key.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    return when (keyType) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> null  // TODO
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = key.getToIntContent(ctx) ?: return null
            val containsCond = dict.dictContainsInt(ctx, intValue)
            myFork(ctx, containsCond)
            if (ctx.modelHolder.model.eval(containsCond).isTrue) {
                dict.readDictIntElement(ctx, intValue)
            } else {
                null
            }
        }
        null -> {
            forkOnUnknownType(ctx, key)
            null
        }
        else -> {
            val containsCond = dict.dictContainsRef(ctx, key)
            myFork(ctx, containsCond)
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
    value: UninterpretedSymbolicPythonObject
) {
    val typeSystem = ctx.typeSystem
    when (key.getTypeIfDefined(ctx)) {
        null -> {
            forkOnUnknownType(ctx, key)
        }
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> Unit  // TODO
        typeSystem.pythonInt -> {
            val intValue = key.getToIntContent(ctx) ?: return
            dict.writeDictIntElement(ctx, intValue, value)
        }
        else -> {
            dict.writeDictRefElement(ctx, key, value)
        }
    }
}

fun handlerDictSetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    val typeSystem = ctx.typeSystem
    dict.addSupertypeSoft(ctx, typeSystem.pythonDict)
    key.addSupertypeSoft(ctx, HasTpHash)
    setItem(ctx, dict, key, value)
}

fun handlerCreateDictKt(
    ctx: ConcolicRunContext,
    keysStream: Stream<UninterpretedSymbolicPythonObject>,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val keys = keysStream.asSequence().toList()
    val elems = elemsStream.asSequence().toList()
    require(keys.size == elems.size)
    val typeSystem = ctx.typeSystem
    val ref = ctx.curState!!.memory.allocConcrete(typeSystem.pythonDict)
    val result = UninterpretedSymbolicPythonObject(ref, ctx.typeSystem)
    (keys zip elems).forEach { (key, elem) ->
        setItem(ctx, result, key, elem)
    }
    return result
}

fun handlerCreateDictConstKeyKt(
    ctx: ConcolicRunContext,
    keys: UninterpretedSymbolicPythonObject,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val elems = elemsStream.asSequence().toList()
    val typeSystem = ctx.typeSystem
    keys.addSupertypeSoft(ctx, typeSystem.pythonTuple)
    val ref = ctx.curState!!.memory.allocConcrete(typeSystem.pythonDict)
    val result = UninterpretedSymbolicPythonObject(ref, ctx.typeSystem)
    elems.forEachIndexed { index, elem ->
        val key = keys.readArrayElement(ctx, ctx.ctx.mkIntNum(index))
        setItem(ctx, result, key, elem)
    }
    return result
}